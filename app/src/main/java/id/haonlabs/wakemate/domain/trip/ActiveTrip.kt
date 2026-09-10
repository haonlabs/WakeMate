package id.haonlabs.wakemate.domain.trip

import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TripSession
import id.haonlabs.wakemate.domain.proximity.ProximityState

/**
 * Sesi yang sedang berjalan berikut posisi terakhirnya. `proximity` masih null sampai fix GPS
 * pertama masuk, jadi UI bisa membedakan "belum dapat sinyal" dari "belum mulai".
 */
data class ActiveTrip(
    val session: TripSession,
    val destination: Station,
    val lineName: String,
    val proximity: ProximityState? = null,
    val alertedAt: Long? = null,
) {
    val hasAlerted: Boolean get() = alertedAt != null
}

/**
 * Kendali foreground service, dideklarasikan di domain supaya use case tidak menyentuh Android
 * framework (lihat prinsip layering di Technical Design §1).
 */
interface TripTracker {
    fun start()
    fun stop()
}
