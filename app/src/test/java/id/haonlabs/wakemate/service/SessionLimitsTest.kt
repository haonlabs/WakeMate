package id.haonlabs.wakemate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionLimitsTest {

    private val start = 1_700_000_000_000L

    private fun after(minutes: Long) = start + minutes * 60_000L

    @Test
    fun `perjalanan komuter biasa tidak diganggu`() {
        // Bogor-Jakarta Kota, ~90 menit, fix terakhir baru saja masuk.
        assertNull(sessionExhausted(start, lastFixAtMs = after(90), nowMs = after(90)))
    }

    @Test
    fun `terowongan MRT tidak dianggap kehilangan sinyal`() {
        // Ruas bawah tanah terpanjang jauh di bawah batas menyerah.
        assertNull(sessionExhausted(start, lastFixAtMs = after(20), nowMs = after(32)))
    }

    @Test
    fun `setengah jam tanpa fix dianggap sinyal hilang`() {
        assertEquals(
            SessionEnd.NO_SIGNAL,
            sessionExhausted(start, lastFixAtMs = after(10), nowMs = after(41)),
        )
    }

    @Test
    fun `sesi yang ditinggalkan berjam-jam dihentikan`() {
        // GPS masih jalan — user lupa membatalkan, dan tanpa batas ini baterainya habis seharian.
        assertEquals(
            SessionEnd.TOO_LONG,
            sessionExhausted(start, lastFixAtMs = after(245), nowMs = after(245)),
        )
    }

    @Test
    fun `durasi sesi diperiksa lebih dulu daripada hilangnya sinyal`() {
        // Keduanya terlampaui sekaligus; alasan yang dilaporkan harus yang menjelaskan akarnya.
        assertEquals(
            SessionEnd.TOO_LONG,
            sessionExhausted(start, lastFixAtMs = after(100), nowMs = after(300)),
        )
    }

    @Test
    fun `tepat di batas durasi belum dihentikan`() {
        val now = start + MAX_SESSION_MS
        assertNull(sessionExhausted(start, lastFixAtMs = now, nowMs = now))
    }

    @Test
    fun `tepat di batas tanpa fix belum dihentikan`() {
        val now = start + NO_FIX_GIVE_UP_MS
        assertNull(sessionExhausted(start, lastFixAtMs = start, nowMs = now))
    }
}
