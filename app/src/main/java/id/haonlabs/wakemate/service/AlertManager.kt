package id.haonlabs.wakemate.service

import android.app.PendingIntent

/**
 * Isi alarm, dipisah dari asal sesinya: alarm GPS (KRL/MRT/TJ) dan alarm dari feed live bus TJ
 * memakai eskalasi yang sama, hanya teks dan tombol "selesai"-nya yang berbeda.
 */
data class WakeAlert(
    val notificationId: Int,
    val title: String,
    val text: String,
    val doneLabel: String,
    val doneAction: PendingIntent,
)

/**
 * Dibiarkan sebagai interface supaya service tidak perlu tahu bentuk alarmnya — strateginya sudah
 * berubah sekali (notifikasi + getar menjadi alarm bertahap) dan bisa berubah lagi, mis. saat
 * companion Wear OS masuk.
 */
interface AlertManager {
    fun alert(alert: WakeAlert)

    /** Hentikan apa pun yang sedang berbunyi atau bergetar. Aman dipanggil berkali-kali. */
    fun stop()
}
