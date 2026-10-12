package id.haonlabs.wakemate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.MainActivity
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "Sesi perjalanan",
                NotificationManager.IMPORTANCE_LOW, // Persisten dan tidak boleh berisik.
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "Pengingat turun",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alarm saat mendekati stasiun tujuan"
                // PRD §12: pola panjang, bukan getar notifikasi biasa yang terlalu singkat untuk
                // membangunkan. Diletakkan di channel supaya tidak perlu izin VIBRATE.
                vibrationPattern = longArrayOf(0, 900, 400, 900, 400, 1400, 400, 1400)
                enableVibration(true)
            }
        )
    }

    /** Notifikasi wajib untuk foreground service, sekaligus status sesi (FR-10). */
    fun progress(trip: ActiveTrip): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_wakemate)
            .setContentTitle("Menuju ${trip.destination.name}")
            .setContentText(progressText(trip))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp())
            .addAction(0, "Batalkan", serviceAction(TripTrackingService::class.java, TripTrackingService.ACTION_CANCEL))
            .build()

    /** Dipakai sesaat sebelum sesi selesai dimuat dari Room; judulnya menyusul. */
    fun starting(): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_wakemate)
            .setContentTitle("WakeMate")
            .setContentText("Menyiapkan sesi perjalanan...")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp())
            .build()

    fun updateProgress(trip: ActiveTrip) {
        if (hasPermission()) manager.notify(ID_PROGRESS, progress(trip))
    }

    /** Isi alarm sesi GPS; eskalasinya diurus [AlertManager]. */
    fun tripAlert(trip: ActiveTrip): WakeAlert {
        val remaining = trip.proximity?.stationsRemaining
        return WakeAlert(
            notificationId = ID_ALERT,
            title = "Hampir sampai ${trip.destination.name} — bersiap turun",
            text = when {
                remaining == null || remaining <= 0 -> "Kamu sudah sampai tujuan."
                remaining == 1 -> "Tujuan adalah pemberhentian berikutnya."
                else -> "$remaining pemberhentian lagi."
            },
            doneLabel = "Selesai",
            doneAction = serviceAction(TripTrackingService::class.java, TripTrackingService.ACTION_COMPLETE),
        )
    }

    fun postAlert(alert: WakeAlert) {
        if (!hasPermission()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_wakemate)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .addAction(0, alert.doneLabel, alert.doneAction)
            .build()
        manager.notify(alert.notificationId, notification)
    }

    /** Notifikasi foreground untuk alarm live; dipisah dari sesi GPS karena keduanya bisa jalan bersamaan. */
    fun liveProgress(title: String, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_wakemate)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openApp())
            .addAction(0, "Batalkan", serviceAction(LiveAlarmService::class.java, LiveAlarmService.ACTION_STOP))
            .build()

    fun updateLive(title: String, text: String) {
        if (hasPermission()) manager.notify(ID_LIVE_PROGRESS, liveProgress(title, text))
    }

    fun liveAlert(title: String, text: String) = WakeAlert(
        notificationId = ID_LIVE_ALERT,
        title = title,
        text = text,
        doneLabel = "Matikan",
        doneAction = serviceAction(LiveAlarmService::class.java, LiveAlarmService.ACTION_STOP),
    )

    fun clearAlert(id: Int = ID_ALERT) = manager.cancel(id)

    /** Sengaja di channel alert: user perlu tahu jaring pengamannya sudah tidak aktif. */
    fun postSessionEnded(reason: SessionEnd) {
        if (!hasPermission()) return
        manager.notify(
            ID_SESSION_ENDED,
            NotificationCompat.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_stat_wakemate)
                .setContentTitle("Pengingat WakeMate berhenti")
                .setContentText(reason.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason.message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun progressText(trip: ActiveTrip): String {
        val proximity = trip.proximity ?: return "Menunggu sinyal GPS..."
        val prefix = when {
            proximity.isDeadReckoned -> "Sinyal hilang, estimasi: "
            proximity.isOffRoute -> "Posisi jauh dari ${trip.lineName} — cek arah: "
            proximity.direction == TravelDirection.AWAY_FROM_DESTINATION -> "Menjauh dari tujuan: "
            else -> ""
        }
        val remaining = proximity.stationsRemaining
        val body = if (remaining <= 0) "sudah sampai" else "$remaining stasiun lagi"
        return "$prefix$body · ${(proximity.distanceToDestinationMeters / 1000).format()} km"
    }

    private fun Double.format() = String.format(java.util.Locale.US, "%.1f", this)

    private fun openApp() = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(service: Class<*>, action: String) = PendingIntent.getService(
        context, action.hashCode(),
        Intent(context, service).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun hasPermission() = manager.areNotificationsEnabled()

    companion object {
        const val CHANNEL_PROGRESS = "trip_progress"
        const val CHANNEL_ALERT = "trip_alert"
        const val ID_PROGRESS = 1
        const val ID_ALERT = 2
        const val ID_SESSION_ENDED = 3
        const val ID_LIVE_PROGRESS = 4
        const val ID_LIVE_ALERT = 5
    }
}
