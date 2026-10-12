package id.haonlabs.wakemate.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.data.live.LiveWatchRepository
import id.haonlabs.wakemate.data.live.TjLiveFeed
import id.haonlabs.wakemate.domain.live.Approach
import id.haonlabs.wakemate.domain.live.LIVE_LOST_MS
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.WatchVerdict
import id.haonlabs.wakemate.domain.live.evaluate
import id.haonlabs.wakemate.domain.live.summary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Alarm yang digerakkan feed live bus TJ, bukan GPS HP: "bangunkan sebelum bus yang saya naiki
 * sampai halte X" dan "ingatkan saat bus rute Y hampir tiba di halte tempat saya menunggu".
 *
 * Tipe dataSync karena yang dikerjakan memang menerima data dari jaringan; lokasi HP tidak
 * dibaca sama sekali.
 */
@AndroidEntryPoint
class LiveAlarmService : Service() {

    @Inject lateinit var feed: TjLiveFeed
    @Inject lateinit var watches: LiveWatchRepository
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var notifications: TripNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var watchJob: Job? = null
    private var watching: LiveWatch? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                watches.clear()
                shutDown(keepAlert = false)
            }
            return START_NOT_STICKY
        }

        enterForeground(watches.state.value?.watch)
        // Start ulang untuk watch yang sama (mis. UI memulihkan sesi) tidak boleh membungkam
        // alarm yang sedang berbunyi; hanya watch baru yang menggantikan yang lama.
        if (watchJob?.isActive == true && watching == watches.state.value?.watch) return START_STICKY
        watchJob?.cancel()
        alertManager.stop()
        watchJob = scope.launch {
            // intent null: sistem me-restart service setelah proses dibunuh; pulihkan dari DataStore.
            val state = watches.state.value ?: watches.restore()
            if (state == null) {
                shutDown(keepAlert = false)
                return@launch
            }
            enterForeground(state.watch)
            watching = state.watch
            watch(state.watch)
        }
        return START_STICKY
    }

    private fun enterForeground(watch: LiveWatch?) {
        val notification = notifications.liveProgress(
            title = watch?.let(::titleFor) ?: "WakeMate Live",
            text = "Menghubungkan ke data live bus...",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TripNotifications.ID_LIVE_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(TripNotifications.ID_LIVE_PROGRESS, notification)
        }
    }

    private suspend fun watch(watch: LiveWatch) {
        var lastSeenAt = System.currentTimeMillis()
        // Denyut terpisah: justru saat feed diam batas waktu harus tetap diperiksa.
        val ticks = flow {
            while (true) {
                emit(Unit)
                delay(TICK_MS)
            }
        }
        combine(feed.route(watch.routeId), ticks) { buses, _ -> buses }.collect { buses ->
            val now = System.currentTimeMillis()
            val state = watches.state.value ?: return@collect
            val seen = when (watch) {
                is LiveWatch.Onboard -> watch.busNo in buses
                is LiveWatch.Arrival -> buses.isNotEmpty()
            }
            if (seen) lastSeenAt = now

            state.alertedAt?.let { alertedAt ->
                // Alarm tidak pernah dimatikan: jangan biarkan service hidup selamanya.
                if (now - alertedAt > ALERT_TIMEOUT_MS) finish()
                return@collect
            }
            when {
                now - watch.startedAt > MAX_SESSION_MS -> return@collect end(SessionEnd.TOO_LONG)
                now - lastSeenAt > LIVE_LOST_MS -> return@collect end(SessionEnd.LIVE_LOST)
            }

            when (val verdict = evaluate(watch, buses)) {
                is WatchVerdict.Waiting -> {
                    watches.publish(verdict.approach)
                    notifications.updateLive(titleFor(watch), progressText(watch, verdict.approach))
                }
                is WatchVerdict.Alert -> {
                    watches.publish(verdict.approach)
                    watches.markAlerted(now)
                    fire(watch, verdict.approach)
                }
            }
        }
    }

    private fun fire(watch: LiveWatch, approach: Approach?) = when (watch) {
        is LiveWatch.Onboard -> alertManager.alert(
            notifications.liveAlert(
                title = if (approach != null) "Hampir sampai ${watch.halteName} — bersiap turun"
                else "Bus sudah melewati ${watch.halteName}",
                text = approach?.summary()?.let { "Halte tujuan: $it." }
                    ?: "Bus ${watch.busNo} sudah melewati halte tujuan atau berganti arah. Cek posisimu.",
            )
        )
        // Menunggu di halte, HP biasanya di tangan: cukup notifikasi penting, tanpa alarm
        // bertahap. Pengingatnya sekali saja, jadi sesinya langsung ditutup.
        is LiveWatch.Arrival -> {
            notifications.postAlert(
                notifications.liveAlert(
                    title = "Bus ${watch.routeId} hampir tiba di ${watch.halteName}",
                    text = approach?.let { "${it.bus.busNo} · ${it.summary()}" }.orEmpty(),
                )
            )
            finish(keepAlert = true)
        }
    }

    private fun finish(keepAlert: Boolean = false) {
        watchJob?.cancel()
        scope.launch {
            watches.clear()
            shutDown(keepAlert)
        }
    }

    private fun end(reason: SessionEnd) {
        watchJob?.cancel()
        scope.launch {
            watches.clear()
            notifications.postSessionEnded(reason)
            shutDown(keepAlert = false)
        }
    }

    /** stopSelf membatalkan [scope] lewat onDestroy, jadi semua penyimpanan harus selesai lebih dulu. */
    private fun shutDown(keepAlert: Boolean) {
        watchJob?.cancel()
        watchJob = null
        if (!keepAlert) {
            alertManager.stop()
            notifications.clearAlert(TripNotifications.ID_LIVE_ALERT)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun titleFor(watch: LiveWatch) = when (watch) {
        is LiveWatch.Onboard -> "Bangunkan sebelum ${watch.halteName}"
        is LiveWatch.Arrival -> "Menunggu bus ${watch.routeId} di ${watch.halteName}"
    }

    private fun progressText(watch: LiveWatch, approach: Approach?): String = when (watch) {
        is LiveWatch.Onboard -> approach?.summary() ?: "Mencari bus ${watch.busNo} di data live..."
        is LiveWatch.Arrival -> approach?.let { "${it.bus.busNo}: ${it.summary()}" }
            ?: "Belum ada bus yang menuju halte ini"
    }

    companion object {
        const val ACTION_START = "id.haonlabs.wakemate.START_LIVE_WATCH"
        const val ACTION_STOP = "id.haonlabs.wakemate.STOP_LIVE_WATCH"

        private const val TICK_MS = 30_000L
        private const val ALERT_TIMEOUT_MS = 20 * 60_000L

        fun intent(context: Context, action: String) =
            Intent(context, LiveAlarmService::class.java).setAction(action)
    }
}

/** Kendali [LiveAlarmService] dari UI. */
class LiveAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val watches: LiveWatchRepository,
) {
    suspend fun start(watch: LiveWatch) {
        watches.start(watch)
        ContextCompat.startForegroundService(
            context,
            LiveAlarmService.intent(context, LiveAlarmService.ACTION_START),
        )
    }

    /** Lanjutkan watch yang tersimpan; aman dipanggil walau service sudah berjalan. */
    fun resume() {
        ContextCompat.startForegroundService(
            context,
            LiveAlarmService.intent(context, LiveAlarmService.ACTION_START),
        )
    }

    /**
     * "Ingatkan lagi di halte berikutnya": watch yang sama dipasang ulang dengan ambang satu halte
     * lebih dekat. Service melihatnya sebagai watch baru, jadi bunyi berhenti dan pemantauan lanjut.
     */
    suspend fun snoozeOneStop(): Boolean {
        val state = watches.state.value ?: return false
        val watch = state.watch as? LiveWatch.Onboard ?: return false
        val stopsAway = state.approach?.stopsAway ?: return false
        if (stopsAway <= 1) return false
        start(watch.copy(lead = LiveLead.Stops(stopsAway - 1)))
        return true
    }

    fun stop() {
        context.startService(LiveAlarmService.intent(context, LiveAlarmService.ACTION_STOP))
    }
}
