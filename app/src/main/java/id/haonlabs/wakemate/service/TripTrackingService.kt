package id.haonlabs.wakemate.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import id.haonlabs.wakemate.data.location.LocationLog
import id.haonlabs.wakemate.data.location.LocationTracker
import id.haonlabs.wakemate.data.location.TrackingProfile
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.hasReached
import id.haonlabs.wakemate.domain.proximity.GeoPoint
import id.haonlabs.wakemate.domain.proximity.ProximityCalculator
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.repository.TripRepository
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Menjaga sesi tetap hidup selama perjalanan (PRD §7.1): WorkManager tidak dipakai karena
 * periodenya tidak presisi, sementara alert yang telat sama saja dengan alert yang gagal.
 *
 * Siapa yang menutup sesi dipisah dengan sengaja: `ACTION_STOP` hanya mematikan service (dipakai
 * use case yang sudah mengurus status sendiri), sedangkan `ACTION_CANCEL`/`ACTION_COMPLETE` datang
 * dari tombol notifikasi dan memang harus menulis status.
 */
@AndroidEntryPoint
class TripTrackingService : Service() {

    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var locationLog: LocationLog
    @Inject lateinit var stationRepository: StationRepository
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var alertManager: AlertManager
    @Inject lateinit var notifications: TripNotifications

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wajib dipanggil dalam hitungan detik sejak startForegroundService, jadi jangan menunggu
        // Room dulu: judul aslinya menyusul begitu sesi selesai dimuat.
        if (!enterForeground(tripRepository.activeTrip.value)) return pauseUntilAppOpens()

        when (intent?.action) {
            ACTION_STOP -> return stopTracking()
            ACTION_CANCEL -> return endSession(completed = false)
            ACTION_COMPLETE -> return endSession(completed = true)
        }

        if (trackingJob?.isActive != true) {
            trackingJob = scope.launch {
                // intent null berarti sistem me-restart service setelah proses dibunuh. State
                // in-memory hilang, tapi sesinya masih ACTIVE di Room — pulihkan, jangan dibatalkan.
                val trip = tripRepository.activeTrip.value ?: tripRepository.restoreActiveSession()
                if (trip == null) {
                    stopTracking()
                    return@launch
                }
                notifications.updateProgress(trip)
                track(trip)
            }
        }
        return START_STICKY
    }

    /**
     * false kalau sistem menolak: tanpa izin lokasi latar belakang, service lokasi hanya boleh
     * masuk foreground selagi aplikasi terbuka — penolakan terjadi saat sistem menyalakan ulang
     * service setelah proses dibunuh.
     */
    private fun enterForeground(trip: ActiveTrip?): Boolean {
        val notification = trip?.let { notifications.progress(it) } ?: notifications.starting()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    TripNotifications.ID_PROGRESS,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(TripNotifications.ID_PROGRESS, notification)
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Service lokasi ditolak masuk foreground", e)
            false
        } catch (e: IllegalStateException) { // ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Service lokasi ditolak masuk foreground", e)
            false
        }
    }

    /**
     * Jangan tutup sesi di Room: begitu user membuka aplikasi, RestoreActiveTripUseCase
     * menyalakan service dari foreground dan pemantauan berlanjut. Diam-diam berhenti adalah
     * kegagalan terburuk, jadi user diberi tahu.
     */
    private fun pauseUntilAppOpens(): Int {
        notifications.postSessionEnded(SessionEnd.RESUME_IN_APP)
        stopSelf()
        return START_NOT_STICKY
    }

    private suspend fun track(trip: ActiveTrip) {
        val route = stationRepository.stationsInLine(trip.session.lineId)
        if (route.size < 2) {
            Log.w(TAG, "Line ${trip.session.lineId} tidak punya rute; sesi dihentikan")
            endSession(completed = false)
            return
        }
        val calculator = ProximityCalculator(
            route = route,
            destinationStationId = trip.session.destinationStationId,
            mode = trip.session.mode,
        )
        val threshold = trip.session.threshold

        // Tracker hidup lebih lama dari satu sesi, jadi profilnya harus di-reset di sini. Tanpa ini
        // sesi berikutnya mewarisi mode hemat dari sesi sebelumnya dan fix pertamanya tidak
        // pernah datang.
        locationTracker.setRequest(TrackingProfile.near())
        locationLog.start(trip)

        val fixes = flow<Event> { locationTracker.locations.collect { emit(Event.Fix(it)) } }
        // Denyut terpisah supaya estimasi tanpa GPS (terowongan MRT) tetap jalan walau fix berhenti.
        val ticks = flow {
            while (true) {
                delay(DEAD_RECKON_TICK_MS)
                emit(Event.Tick)
            }
        }

        var lastFixAt = System.currentTimeMillis()

        merge(fixes, ticks)
            .catch { error ->
                // Izin lokasi dicabut di tengah sesi: berhenti, jangan pura-pura masih tracking.
                Log.e(TAG, "Tracking berhenti", error)
                endSession(completed = false, reason = SessionEnd.NO_SIGNAL)
            }
            .collect { event ->
                val now = System.currentTimeMillis()
                // Diperiksa di jalur denyut juga, bukan hanya saat ada fix — justru sesi yang
                // tidak pernah menerima fix lagi yang paling perlu dihentikan.
                sessionExhausted(trip.session.startedAt, lastFixAt, now)?.let { reason ->
                    endSession(completed = false, reason = reason)
                    return@collect
                }
                val state = when (event) {
                    is Event.Fix -> {
                        lastFixAt = now
                        calculator.onLocation(event.point, now)
                    }
                    Event.Tick -> calculator.onNoFix(now)
                } ?: return@collect
                onProximity(state, threshold, now)
            }
    }

    private fun onProximity(state: ProximityState, threshold: AlertThreshold, now: Long) {
        tripRepository.publishProgress(state)
        val trip = tripRepository.activeTrip.value ?: return
        locationTracker.setRequest(
            if (isClosingIn(state, threshold)) TrackingProfile.near()
            else TrackingProfile.far(trip.session.mode)
        )
        notifications.updateProgress(trip)

        if (!trip.hasAlerted) {
            if (state.hasReached(threshold)) {
                alertManager.alert(notifications.tripAlert(trip))
                locationLog.event("alert")
                tripRepository.markAlerted(now)
            }
            return
        }

        // FR-8: setelah alert, sesi ditutup begitu user benar-benar sampai, atau menyerah menunggu
        // konfirmasi kalau notifikasinya ternyata tidak pernah disentuh.
        val alertedFor = now - (trip.alertedAt ?: now)
        if (state.hasArrived || alertedFor > ALERT_TIMEOUT_MS) {
            endSession(completed = true)
        }
    }

    /** Naikkan akurasi lebih awal dari ambang alert, supaya alert-nya sendiri tidak telat. */
    private fun isClosingIn(state: ProximityState, threshold: AlertThreshold) = when (threshold) {
        is AlertThreshold.StationCount -> state.stationsRemaining <= threshold.stations + 1
        is AlertThreshold.Distance -> state.distanceToDestinationMeters <= threshold.meters * 2
    }

    /** Tutup sesi di Room lalu matikan service — urutannya penting, lihat [stopTracking]. */
    private fun endSession(completed: Boolean, reason: SessionEnd? = null): Int {
        trackingJob?.cancel()
        trackingJob = null
        scope.launch {
            if (completed) tripRepository.complete() else tripRepository.cancel()
            // Berhenti tanpa memberi tahu adalah kegagalan terburuk untuk app pengingat: user
            // mengira masih dijaga padahal tidak.
            reason?.let {
                locationLog.event("berhenti:${it.name}")
                notifications.postSessionEnded(it)
            }
            stopTracking()
        }
        return START_NOT_STICKY
    }

    /**
     * stopSelf memicu onDestroy yang membatalkan [scope], jadi apa pun yang harus tersimpan wajib
     * selesai sebelum ini dipanggil — bukan di-launch berbarengan.
     */
    private fun stopTracking(): Int {
        alertManager.stop()
        locationLog.stop()
        trackingJob?.cancel()
        trackingJob = null
        notifications.clearAlert()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private sealed interface Event {
        data class Fix(val point: GeoPoint) : Event
        data object Tick : Event
    }

    companion object {
        const val ACTION_START = "id.haonlabs.wakemate.START_TRIP"
        const val ACTION_STOP = "id.haonlabs.wakemate.STOP_TRIP"
        const val ACTION_CANCEL = "id.haonlabs.wakemate.CANCEL_TRIP"
        const val ACTION_COMPLETE = "id.haonlabs.wakemate.COMPLETE_TRIP"

        private const val TAG = "TripTracking"
        private const val DEAD_RECKON_TICK_MS = 15_000L
        private const val ALERT_TIMEOUT_MS = 20 * 60_000L

        fun intent(context: Context, action: String) =
            Intent(context, TripTrackingService::class.java).setAction(action)
    }
}
