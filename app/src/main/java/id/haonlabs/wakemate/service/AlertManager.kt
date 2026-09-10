package id.haonlabs.wakemate.service

import id.haonlabs.wakemate.domain.trip.ActiveTrip

/**
 * Dibiarkan sebagai interface supaya `TripTrackingService` tidak perlu tahu bentuk alarmnya —
 * strateginya sudah berubah sekali (notifikasi + getar menjadi alarm bertahap) dan bisa berubah
 * lagi, mis. saat companion Wear OS masuk.
 */
interface AlertManager {
    fun alert(trip: ActiveTrip)

    /** Hentikan apa pun yang sedang berbunyi atau bergetar. Aman dipanggil berkali-kali. */
    fun stop()
}
