package id.haonlabs.wakemate.domain.live

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleEtaTest {

    private val zone = ZoneId.of("Asia/Jakarta")

    /**
     * Trip dengan pola umum GTFS Transjakarta: halte pertama berangkat 06:00, tiap 30 menit
     * (headway 1800) sampai 09:00, dan hanya beroperasi hari Senin. Tiba: HA 06:00,
     * HB 06:10, HC 06:25.
     */
    private val trip = TjTrip(
        id = "9-R01", routeId = "9", headsign = "Pinang Ranti", name = "",
        halteIds = listOf("HA", "HB", "HC"), shape = "",
        arrivals = listOf(21600, 22200, 23100), // 06:00, 06:10, 06:25
        days = 0b0000001, // Senin saja
        freqs = listOf(Frequency(21600, 32400, 1800)),
    )
    private val kembar = trip.copy(id = "9-R02")

    private fun senin(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, 2, h, m, s, 0, zone)

    @Test
    fun `eta dihitung dari instansi berangkat plus selisih detik halte`() {
        // 07:05: instansi yang sudah lewat (06:00, 06:30) dilewati; tiba di HB berikutnya
        // 06:50+600=... berangkat 06:30 tiba 06:40 udah lewat, jadi 07:00(+10)=07:10, dst.
        assertEquals(listOf(5, 35, 65), scheduleEtas(listOf(trip), "HB", senin(7, 5)))
    }

    @Test
    fun `tepat saat jadwal tiba tetap ditampilkan minimal satu menit`() {
        // Tiba di HB tepat 07:10 dianggap "±1 mnt" — bukan 0, karena jadwal tidak bisa dipastikan.
        assertEquals(listOf(1, 30, 60), scheduleEtas(listOf(trip), "HB", senin(7, 10)))
    }

    @Test
    fun `trip kembar menyatu tanpa menit duplikat`() {
        assertEquals(listOf(5, 35, 65), scheduleEtas(listOf(trip, kembar), "HB", senin(7, 5)))
    }

    @Test
    fun `hari yang tidak jalan tidak menghasilkan apa-apa`() {
        val selasa = ZonedDateTime.of(2026, 11, 3, 7, 5, 0, 0, zone)
        assertEquals(emptyList<Int>(), scheduleEtas(listOf(trip, kembar), "HB", selasa))
    }

    @Test
    fun `jadwal rusak atau halte tak ada diabaikan`() {
        assertEquals(emptyList<Int>(), scheduleEtas(listOf(trip), "KOTA", senin(7, 5)))
        val tidakSejajar = trip.copy(halteIds = listOf("HA", "HB", "HC", "HD"))
        assertEquals(emptyList<Int>(), scheduleEtas(listOf(tidakSejajar), "HB", senin(7, 5)))
    }
}
