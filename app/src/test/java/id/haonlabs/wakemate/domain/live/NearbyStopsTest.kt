package id.haonlabs.wakemate.domain.live

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyStopsTest {

    private val zone = ZoneId.of("Asia/Jakarta")
    private fun senin(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 2, h, m, 0, 0, zone)

    // Pusat (-6.2000, 106.8200). 0,001 derajat lintang ~ 111 m.
    private val a = Halte("A", "Halte A", -6.2009, 106.8200)       // ~100 m
    private val b = Halte("B", "Halte B", -6.2045, 106.8200)       // ~500 m
    private val c = Halte("C", "Halte C", -6.2300, 106.8200)       // ~3,3 km
    private val krlStop = Halte("krl:MRI", "Manggarai", -6.2000, 106.8210) // ~110 m
    private val orphan = Halte("X", "Tanpa rute", -6.2001, 106.8200)

    private val bus1 = TjRoute("1", "1", "Blok M - Kota", "BRT", "D32027", "FFFFFF")
    private val krl = TjRoute("krl:B", "B", "Commuter Line Bogor", "KRL", "E30A16", "FFFFFF")

    /** Bus 1 jalan tiap 30 mnt dari A ke B ke C (Senin); KRL jadwal tetap 07:10 di Manggarai. */
    private val tripBus = TjTrip(
        "t1", "1", "Kota", "", listOf("A", "B", "C"), "", arrivals = listOf(25200, 25800, 27000),
        days = 0b1111111, freqs = listOf(Frequency(25200, 36000, 1800)),
    )
    private val tripKrl = TjTrip("k1", "krl:B", "Bogor", "KA 1", listOf("krl:MRI", "krl:TEB"), "", arrivals = listOf(25800, 26400), days = 0b0011111)
    private val tripKrlTerminus = TjTrip("k2", "krl:B", "Manggarai", "KA 2", listOf("krl:TEB", "krl:MRI"), "", arrivals = listOf(25000, 25600), days = 0b0011111)

    private val net = TjNetwork(
        routes = listOf(krl, bus1),
        halte = listOf(a, b, c, krlStop, orphan).associateBy { it.id } + ("krl:TEB" to Halte("krl:TEB", "Tebet", -6.226, 106.858)),
        trips = listOf(tripBus, tripKrl, tripKrlTerminus),
        fixedScheduleValidUntil = LocalDate.of(2026, 12, 31),
    )

    @Test
    fun `hanya halte dalam radius, terdekat dulu, tanpa halte yatim`() {
        val ids = nearbyStops(net, -6.2000, 106.8200, 1000.0).map { it.halte.id }
        assertEquals(listOf("A", "krl:MRI", "B"), ids) // C 3 km di luar radius, X tanpa rute
    }

    @Test
    fun `radius sempit menyaring lebih banyak`() {
        assertEquals(listOf("A", "krl:MRI"), nearbyStops(net, -6.2000, 106.8200, 200.0).map { it.halte.id })
    }

    @Test
    fun `filter moda`() {
        assertEquals(listOf("krl:MRI"), nearbyStops(net, -6.2000, 106.8200, 1000.0, NearbyMode.KRL).map { it.halte.id })
        assertEquals(listOf("A", "B"), nearbyStops(net, -6.2000, 106.8200, 1000.0, NearbyMode.BUS).map { it.halte.id })
        assertTrue(nearbyStops(net, -6.2000, 106.8200, 1000.0, NearbyMode.MRT).isEmpty())
    }

    @Test
    fun `moda dibaca dari awalan id`() {
        assertEquals(NearbyMode.KRL, Halte("krl:X", "", 0.0, 0.0).nearbyMode())
        assertEquals(NearbyMode.MRT, Halte("mrt:1", "", 0.0, 0.0).nearbyMode())
        assertEquals(NearbyMode.LRT, Halte("lrtj:1", "", 0.0, 0.0).nearbyMode())
        assertEquals(NearbyMode.LRT, Halte("lrtjbd:1", "", 0.0, 0.0).nearbyMode())
        assertEquals(NearbyMode.BUS, Halte("12345", "", 0.0, 0.0).nearbyMode())
    }

    @Test
    fun `waktu jalan dibulatkan ke atas dan minimal satu menit`() {
        val stops = nearbyStops(net, -6.2000, 106.8200, 1000.0)
        assertEquals(2, stops.first { it.halte.id == "A" }.walkMinutes)      // ~100 m / 80 = 1,25 -> 2
        assertEquals(1, NearbyStop(a, NearbyMode.BUS, 10.0, listOf(bus1)).walkMinutes)
    }

    @Test
    fun `rute yang melewati halte unik dan berurutan rel dulu`() {
        assertEquals(listOf(krl), net.routesAt("krl:MRI"))
        assertEquals(listOf(bus1), net.routesAt("A"))
        assertEquals(emptyList<TjRoute>(), net.routesAt("tidak-ada"))
    }

    @Test
    fun `keberangkatan tercepat dulu, trip yang berakhir di halte tidak dihitung`() {
        // Senin 07:00. Di Manggarai: KA 1 (ke Bogor) 07:10 = 10 mnt; KA 2 berakhir di Manggarai -> diabaikan.
        val krlDeps = departuresAt(net, "krl:MRI", senin(7, 0))
        assertEquals(1, krlDeps.size)
        assertEquals(10, krlDeps.single().minutes)
        assertEquals("Bogor", krlDeps.single().headsign)
        // Halte A: bus 1 berangkat 07:00 tepat -> minimal 1 menit.
        assertEquals(1, departuresAt(net, "A", senin(7, 0)).single().minutes)
    }

    @Test
    fun `halte terakhir rute tidak punya keberangkatan`() {
        assertTrue(departuresAt(net, "C", senin(7, 0)).isEmpty())
    }

    @Test
    fun `jadwal KRL kadaluarsa disembunyikan tapi barisnya tetap ada`() {
        val sesudah = ZonedDateTime.of(2027, 1, 4, 7, 0, 0, 0, zone) // Senin setelah masa berlaku
        val dep = departuresAt(net, "krl:MRI", sesudah).single()
        assertNull(dep.minutes)
        assertEquals("Bogor", dep.headsign)
    }
}
