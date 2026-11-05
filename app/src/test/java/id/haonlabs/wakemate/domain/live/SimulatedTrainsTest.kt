package id.haonlabs.wakemate.domain.live

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedTrainsTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private val halte = mapOf(
        "A" to Halte("A", "A", -6.0, 106.0),
        "B" to Halte("B", "B", -6.0, 106.1),
        "C" to Halte("C", "C", -6.2, 106.1),
    )

    /** A 05:00, B 05:10, C 05:30; Sen-Jum. */
    private val trip = TjTrip(
        id = "krl:1", routeId = "krl:B", headsign = "C", name = "",
        halteIds = listOf("A", "B", "C"), shape = "",
        arrivals = listOf(18000, 18600, 19800), days = 0b0011111,
    )

    private fun senin(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, 2, h, m, s, 0, zone)

    @Test
    fun `di tengah ruas posisinya diinterpolasi menurut waktu`() {
        val train = simulatedTrains(listOf(trip), halte, senin(5, 5)).single()
        assertEquals(-6.0, train.lat, 1e-9)
        assertEquals(106.05, train.lng, 1e-9) // separuh jalan A -> B
        assertNull(train.atHalteId)
    }

    @Test
    fun `ruas kedua memakai selisih waktunya sendiri`() {
        val train = simulatedTrains(listOf(trip), halte, senin(5, 20)).single() // B 05:10 -> C 05:30, tengah
        assertEquals(-6.1, train.lat, 1e-9)
        assertEquals(106.1, train.lng, 1e-9)
    }

    @Test
    fun `tepat jam tiba di stasiun ditandai berhenti di sana`() {
        assertEquals("B", simulatedTrains(listOf(trip), halte, senin(5, 10)).single().atHalteId)
    }

    @Test
    fun `sebelum berangkat berdiri di stasiun awal, lalu hilang`() {
        assertEquals("A", simulatedTrains(listOf(trip), halte, senin(4, 58)).single().atHalteId)
        assertTrue(simulatedTrains(listOf(trip), halte, senin(4, 57)).isEmpty())
    }

    @Test
    fun `setelah tiba masih sebentar di stasiun akhir, lalu hilang`() {
        assertEquals("C", simulatedTrains(listOf(trip), halte, senin(5, 30, 30)).single().atHalteId)
        assertTrue(simulatedTrains(listOf(trip), halte, senin(5, 32)).isEmpty())
    }

    @Test
    fun `hari tanpa layanan tidak menampilkan kereta - Sabtu`() {
        assertTrue(simulatedTrains(listOf(trip), halte, ZonedDateTime.of(2026, 11, 7, 5, 5, 0, 0, zone)).isEmpty())
    }

    @Test
    fun `KA lewat tengah malam milik hari layanan kemarin`() {
        // Berangkat Minggu 23:50, tiba B 00:10 (hari berikutnya) lalu C 00:30.
        val larut = trip.copy(arrivals = listOf(85800, 87000, 88200), days = 0b1000000)
        val train = simulatedTrains(listOf(larut), halte, senin(0, 20)).single() // B -> C, tengah
        assertEquals(-6.1, train.lat, 1e-9)
    }

    @Test
    fun `trip berfrekuensi dan data tak sejajar diabaikan`() {
        val freq = trip.copy(freqs = listOf(Frequency(18000, 30000, 600)))
        val rusak = trip.copy(halteIds = listOf("A", "B"))
        assertTrue(simulatedTrains(listOf(freq, rusak), halte, senin(5, 5)).isEmpty())
    }
}
