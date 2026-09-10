package id.haonlabs.wakemate.domain.repository

import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import kotlinx.coroutines.flow.StateFlow

interface TripRepository {
    /** Sumber tunggal status sesi; ditulis service, dibaca UI (satu proses, tanpa IPC). */
    val activeTrip: StateFlow<ActiveTrip?>

    suspend fun start(destination: Station, threshold: AlertThreshold): ActiveTrip

    /** Recovery setelah app dibunuh sistem: kembalikan sesi ACTIVE yang tersimpan, kalau ada. */
    suspend fun restoreActiveSession(): ActiveTrip?

    fun publishProgress(proximity: ProximityState)

    fun markAlerted(atMs: Long)

    suspend fun complete()

    suspend fun cancel()
}
