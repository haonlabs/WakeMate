package id.haonlabs.wakemate.service

/** Alasan sesi ditutup sistem, bukan oleh user. Teksnya dipakai apa adanya di notifikasi. */
enum class SessionEnd(val message: String) {
    NO_SIGNAL("Sinyal GPS hilang terlalu lama. Pengingat dimatikan — nyalakan ulang kalau masih di perjalanan."),
    TOO_LONG("Sesi berjalan lebih lama dari perjalanan mana pun yang wajar, jadi dihentikan agar tidak menguras baterai."),
    /**
     * Android 14+ menolak service lokasi yang dinyalakan ulang dari latar belakang tanpa izin
     * lokasi latar belakang. Sesinya tetap tersimpan dan dilanjutkan begitu aplikasi dibuka.
     */
    RESUME_IN_APP("Android menghentikan pemantauan di latar belakang. Buka WakeMate untuk melanjutkan pengingat perjalananmu."),
    LIVE_LOST("Data live bus tidak masuk lebih dari 5 menit (internet putus atau feed Transjakarta bermasalah). Pengingat dimatikan."),
}

/** Batas atas perjalanan Jabodetabek mana pun; PRD §3 menyebut 30-90 menit. */
const val MAX_SESSION_MS = 4 * 60 * 60_000L

/**
 * Dua kali lipat batas estimasi tanpa GPS. Selewat ini bukan lagi terowongan, melainkan izin
 * dicabut, GPS dimatikan, atau HP tertinggal di dalam ruangan.
 */
const val NO_FIX_GIVE_UP_MS = 30 * 60_000L

/**
 * Alasan sesi harus ditutup paksa, atau null kalau masih wajar.
 *
 * Dipisah dari service supaya bisa diuji tanpa Android: sesi yang tidak pernah berhenti adalah
 * cara paling mudah bagi app ini menghabiskan baterai user seharian.
 */
fun sessionExhausted(startedAtMs: Long, lastFixAtMs: Long, nowMs: Long): SessionEnd? = when {
    nowMs - startedAtMs > MAX_SESSION_MS -> SessionEnd.TOO_LONG
    nowMs - lastFixAtMs > NO_FIX_GIVE_UP_MS -> SessionEnd.NO_SIGNAL
    else -> null
}
