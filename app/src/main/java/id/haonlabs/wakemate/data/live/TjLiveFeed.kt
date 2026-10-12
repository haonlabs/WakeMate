package id.haonlabs.wakemate.data.live

import android.util.Log
import id.haonlabs.wakemate.domain.live.LiveBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.MqttTopic
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.inject.Inject
import javax.inject.Singleton

enum class FeedStatus { IDLE, CONNECTING, LIVE, OFFLINE }

/**
 * Posisi bus live dari broker MQTT milik Transjakarta (dipakai juga oleh aplikasi resminya).
 *
 * Broker ini tidak terdokumentasi dan bukan API publik: bisa berubah atau ditutup kapan saja,
 * jadi semua pemakainya harus tetap berguna tanpa data live. Untuk menjaga beban ke server mereka,
 * yang di-subscribe hanya rute yang sedang dipakai (`/mobile_armada/+/{rute}/#`), bukan seluruh
 * armada — satu rute cuma belasan sampai puluhan bus, sedangkan seluruh armada >100 pesan/detik.
 *
 * Satu koneksi dipakai bersama oleh peta dan service alarm; diputus sendiri saat tidak ada yang
 * mendengarkan lagi.
 */
@Singleton
class TjLiveFeed @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val listeners = mutableListOf<Listener>()
    private var client: MqttAsyncClient? = null
    private var connectJob: Job? = null
    private var idleJob: Job? = null

    private val _status = MutableStateFlow(FeedStatus.IDLE)
    val status: StateFlow<FeedStatus> = _status.asStateFlow()

    private class Listener(val filter: String, val onBus: (LiveBus) -> Unit)

    /**
     * Potret semua bus di satu rute, diperbarui tiap ada pesan. Bus yang tidak melapor selama
     * [STALE_MS] dibuang supaya bus yang sudah masuk pool tidak tampil sebagai hantu.
     */
    fun route(routeId: String): Flow<Map<String, LiveBus>> = flow {
        val buses = HashMap<String, LiveBus>()
        emit(emptyMap())
        val ticks = flow {
            while (true) {
                delay(PRUNE_EVERY_MS)
                emit(null)
            }
        }
        merge(messages(topicFilter(routeId)).map<LiveBus, LiveBus?> { it }, ticks).collect { bus ->
            if (bus != null) buses[bus.busNo] = bus
            val cutoff = System.currentTimeMillis() - STALE_MS
            buses.values.removeAll { it.updatedAt < cutoff }
            emit(buses.toMap())
        }
    }

    private fun messages(filter: String): Flow<LiveBus> = callbackFlow {
        val listener = Listener(filter) { trySend(it) }
        register(listener)
        awaitClose { unregister(listener) }
    }.buffer(256)

    private fun register(listener: Listener) = synchronized(lock) {
        idleJob?.cancel()
        val isNewFilter = listeners.none { it.filter == listener.filter }
        listeners += listener
        val current = client
        if (isNewFilter && current?.isConnected == true) subscribe(current, listener.filter)
        ensureConnected()
    }

    private fun unregister(listener: Listener) = synchronized(lock) {
        listeners -= listener
        val current = client
        if (listeners.none { it.filter == listener.filter } && current?.isConnected == true) {
            runCatching { current.unsubscribe(listener.filter) }
        }
        if (listeners.isEmpty()) {
            // Jeda sebelum memutus: pindah layar atau rotasi tidak perlu membuka koneksi baru.
            idleJob = scope.launch {
                delay(IDLE_DISCONNECT_MS)
                disconnect()
            }
        }
    }

    /** Dipanggil di dalam [lock]. */
    private fun ensureConnected() {
        if (client != null || connectJob?.isActive == true) return
        connectJob = scope.launch {
            var backoff = 1_000L
            // Reconnect otomatis Paho hanya berlaku setelah koneksi pertama berhasil, jadi
            // percobaan pertama diulang sendiri.
            while (isActive && synchronized(lock) { listeners.isNotEmpty() }) {
                _status.value = FeedStatus.CONNECTING
                if (connectOnce()) return@launch
                _status.value = FeedStatus.OFFLINE
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000L)
            }
        }
    }

    private fun connectOnce(): Boolean {
        val candidate = MqttAsyncClient(BROKER, MqttAsyncClient.generateClientId(), MemoryPersistence())
        candidate.setCallback(Callback(candidate))
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
            connectionTimeout = 15
            keepAliveInterval = 30
        }
        synchronized(lock) { client = candidate }
        return runCatching { candidate.connect(options).waitForCompletion(20_000) }
            .onFailure {
                Log.w(TAG, "Gagal terhubung ke feed live", it)
                synchronized(lock) { if (client === candidate) client = null }
                runCatching { candidate.close(true) }
            }
            .isSuccess
    }

    private fun disconnect() {
        val current = synchronized(lock) {
            if (listeners.isNotEmpty()) return
            connectJob?.cancel()
            client.also { client = null }
        } ?: return
        _status.value = FeedStatus.IDLE
        runCatching { current.disconnect().waitForCompletion(5_000) }
        runCatching { current.close(true) }
    }

    private fun subscribe(target: MqttAsyncClient, filter: String) {
        runCatching { target.subscribe(filter, 0) }
            .onFailure { Log.w(TAG, "Gagal subscribe $filter", it) }
    }

    private inner class Callback(private val owner: MqttAsyncClient) : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            // Sesi bersih: setelah reconnect broker sudah lupa semua subscription.
            val filters = synchronized(lock) {
                if (client !== owner) return
                listeners.map { it.filter }.distinct()
            }
            _status.value = FeedStatus.LIVE
            filters.forEach { subscribe(owner, it) }
        }

        override fun connectionLost(cause: Throwable?) {
            if (synchronized(lock) { client === owner }) _status.value = FeedStatus.CONNECTING
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            val targets = synchronized(lock) {
                listeners.filter { MqttTopic.isMatched(it.filter, topic) }
            }
            if (targets.isEmpty()) return
            val bus = parseArmada(String(message.payload), System.currentTimeMillis()) ?: return
            targets.forEach { it.onBus(bus) }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    companion object {
        private const val TAG = "TjLiveFeed"
        private const val BROKER = "wss://mqtt.tj.co.id:8084/mqtt"
        private const val STALE_MS = 3 * 60_000L
        private const val PRUNE_EVERY_MS = 15_000L
        private const val IDLE_DISCONNECT_MS = 10_000L

        fun topicFilter(routeId: String) = "/mobile_armada/+/$routeId/#"
    }
}
