package id.haonlabs.wakemate.service

import id.haonlabs.wakemate.domain.trip.ActiveTrip
import javax.inject.Inject

/**
 * Dibiarkan sebagai interface walau implementasinya baru satu: v1.1 menambah alarm keras yang
 * menembus mode silent, dan pemanggilnya (`TripTrackingService`) tidak boleh ikut berubah.
 */
interface AlertManager {
    fun alert(trip: ActiveTrip)
}

class NotificationAlertManager @Inject constructor(
    private val notifications: TripNotifications,
) : AlertManager {
    override fun alert(trip: ActiveTrip) = notifications.postAlert(trip)
}
