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

    // ---- Jadwal tetap (KRL): arrivals mutlak, tanpa freqs.

    /** Stasiun HA 05:00, HB 05:10 (Sen-Jum) dan KA malam yang tiba di HB 24:30 (layanan Minggu). */
    private val krlPagi = TjTrip(
        id = "krl:1", routeId = "krl:B", headsign = "Bogor", name = "KA 1",
        halteIds = listOf("HA", "HB"), shape = "",
        arrivals = listOf(18000, 18600), days = 0b0011111,
    )
    private val krlLarut = TjTrip(
        id = "krl:2", routeId = "krl:B", headsign = "Bogor", name = "KA 2",
        halteIds = listOf("HA", "HB"), shape = "",
        arrivals = listOf(86400, 88200), days = 0b1000000, // berangkat Minggu 24:00, tiba HB Senin 00:30
    )

    @Test
    fun `jadwal tetap memberi selisih menit ke jam tiba mutlak`() {
        assertEquals(listOf(20), scheduleEtas(listOf(krlPagi), "HB", senin(4, 50)))
    }

    @Test
    fun `jadwal tetap yang sudah lewat tidak ditampilkan`() {
        assertEquals(emptyList<Int>(), scheduleEtas(listOf(krlPagi), "HB", senin(5, 11)))
    }

    @Test
    fun `jadwal tetap tidak ada di hari tanpa layanan - Sabtu`() {
        val sabtu = ZonedDateTime.of(2026, 11, 7, 4, 50, 0, 0, zone)
        assertEquals(emptyList<Int>(), scheduleEtas(listOf(krlPagi), "HB", sabtu))
    }

    @Test
    fun `KA lewat tengah malam dihitung dari hari layanan kemarin`() {
        // Senin 00:20: KA layanan Minggu tiba di HB 00:30 -> 10 menit lagi.
        assertEquals(listOf(10), scheduleEtas(listOf(krlLarut), "HB", senin(0, 20)))
    }

    @Test
    fun `jadwal tetap dan frekuensi digabung lalu diurutkan`() {
        // Senin 04:50: KRL 05:10 (20 mnt), lalu bus berfrekuensi tiba di HB 06:10 (80) dan 06:40 (110).
        assertEquals(listOf(20, 80, 110), scheduleEtas(listOf(trip, krlPagi), "HB", senin(4, 50)))
    }
}
