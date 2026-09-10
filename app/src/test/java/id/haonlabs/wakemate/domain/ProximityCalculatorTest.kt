package id.haonlabs.wakemate.domain

import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.model.hasReached
import id.haonlabs.wakemate.domain.proximity.GeoPoint
import id.haonlabs.wakemate.domain.proximity.ProximityCalculator
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityCalculatorTest {

    private fun calculator(route: List<Station>, destinationIndex: Int, mode: TransitMode) =
        ProximityCalculator(route, route[destinationIndex].id, mode)

    private fun at(station: Station) = GeoPoint(station.lat, station.lng)

    /** Jalankan perjalanan dari [from] ke [to] dengan satu fix per stasiun, 90 detik sekali. */
    private fun ride(
        calculator: ProximityCalculator,
        route: List<Station>,
        from: Int,
        to: Int,
    ): List<ProximityState> {
        val indices = if (to >= from) from..to else from downTo to
        return indices.mapIndexed { step, i ->
            calculator.onLocation(at(route[i]), step * 90_000L)
        }
    }

    @Test
    fun `di stasiun awal, sisa stasiun sama dengan jumlah pemberhentian ke tujuan`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val state = calc.onLocation(at(MRT_NORTH_SOUTH[0]), 0)

        assertEquals(12, state.stationsRemaining)
        assertEquals("Lebak Bulus Bank Syariah Indonesia", state.nearestStation.name)
        assertFalse(state.hasArrived)
        assertFalse(state.isOffRoute)
    }

    @Test
    fun `sisa stasiun berkurang satu per satu sepanjang perjalanan`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val remaining = ride(calc, MRT_NORTH_SOUTH, 0, 12).map { it.stationsRemaining }

        assertEquals((12 downTo 0).toList(), remaining)
    }

    @Test
    fun `di tujuan, sesi dianggap sampai`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val arrival = ride(calc, MRT_NORTH_SOUTH, 0, 12).last()

        assertTrue(arrival.hasArrived)
        assertEquals(0, arrival.stationsRemaining)
    }

    @Test
    fun `alert 2 stasiun menyala tepat setelah meninggalkan stasiun ketiga dari akhir`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val states = ride(calc, MRT_NORTH_SOUTH, 0, 12)
        val threshold = AlertThreshold.StationCount(2)

        val firedAt = states.indexOfFirst { it.hasReached(threshold) }
        // Stasiun index 10 = Setiabudi Astra, menyisakan Dukuh Atas + Bundaran HI.
        assertEquals(10, firedAt)
        assertEquals("Setiabudi Astra", states[firedAt].nearestStation.name)
    }

    @Test
    fun `arah terdeteksi sebagai menjauh kalau user naik ke arah sebaliknya`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        // Naik di Blok M tapi kereta jalan ke arah Lebak Bulus.
        val states = ride(calc, MRT_NORTH_SOUTH, 5, 0)

        assertEquals(TravelDirection.AWAY_FROM_DESTINATION, states.last().direction)
        assertTrue(states.last().stationsRemaining > states.first().stationsRemaining)
    }

    @Test
    fun `line yang urutannya terbalik tetap terhitung benar`() {
        // Perjalanan Bundaran HI -> Lebak Bulus memakai data line yang sama, hanya arah berbeda.
        val calc = calculator(MRT_NORTH_SOUTH, 0, TransitMode.MRT)
        val states = ride(calc, MRT_NORTH_SOUTH, 12, 0)

        assertEquals((12 downTo 0).toList(), states.map { it.stationsRemaining })
        assertEquals(TravelDirection.TOWARD_DESTINATION, states[1].direction)
        assertTrue(states.last().hasArrived)
    }

    @Test
    fun `naik line yang salah terdeteksi sebagai off-route`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        calc.onLocation(at(MRT_NORTH_SOUTH[0]), 0)
        // Stasiun Bekasi: masih Jabodetabek, tapi jauh dari koridor MRT.
        val state = calc.onLocation(GeoPoint(-6.235607, 106.999512), 90_000)

        assertTrue(state.isOffRoute)
        assertTrue(state.offRouteMeters > 10_000)
    }

    @Test
    fun `berada di antara dua stasiun tidak dianggap off-route`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val a = MRT_NORTH_SOUTH[9]
        val b = MRT_NORTH_SOUTH[10]
        val state = calc.onLocation(GeoPoint((a.lat + b.lat) / 2, (a.lng + b.lng) / 2), 0)

        assertFalse(state.isOffRoute)
        // Masih sebelum Setiabudi Astra, jadi Setiabudi + Dukuh Atas + Bundaran HI.
        assertEquals(3, state.stationsRemaining)
    }

    @Test
    fun `halte Transjakarta yang berdempetan tidak tertukar`() {
        val calc = calculator(TJ_KORIDOR_1, 13, TransitMode.TRANSJAKARTA)
        val states = ride(calc, TJ_KORIDOR_1, 0, 13)

        assertEquals(TJ_KORIDOR_1.map { it.name }.take(14), states.map { it.nearestStation.name })
        assertEquals((13 downTo 0).toList(), states.map { it.stationsRemaining })
    }

    @Test
    fun `saat GPS hilang, posisi diteruskan dari kecepatan terakhir`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        // Dua fix untuk membangun estimasi kecepatan, lalu masuk terowongan.
        calc.onLocation(at(MRT_NORTH_SOUTH[8]), 0)
        val lastFix = calc.onLocation(at(MRT_NORTH_SOUTH[9]), 90_000)

        val predicted = calc.onNoFix(90_000 + 120_000)

        assertTrue(predicted != null && predicted.isDeadReckoned)
        assertTrue(predicted!!.distanceToDestinationMeters < lastFix.distanceToDestinationMeters)
        assertTrue(predicted.stationsRemaining < lastFix.stationsRemaining)
    }

    @Test
    fun `estimasi tanpa GPS berhenti setelah terlalu lama tak dipercaya`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        calc.onLocation(at(MRT_NORTH_SOUTH[8]), 0)
        calc.onLocation(at(MRT_NORTH_SOUTH[9]), 90_000)

        assertNull("baru sebentar, fix terakhir masih relevan", calc.onNoFix(90_000 + 5_000))
        assertNull("sudah 20 menit tanpa fix", calc.onNoFix(90_000 + 20 * 60_000))
    }

    @Test
    fun `tanpa fix sama sekali tidak ada yang bisa diestimasi`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)

        assertNull(calc.onNoFix(600_000))
    }

    @Test
    fun `lompatan GPS tidak dipakai sebagai kecepatan`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        calc.onLocation(at(MRT_NORTH_SOUTH[0]), 0)
        // Teleport 20 km dalam 1 detik: mustahil untuk moda apa pun.
        calc.onLocation(at(MRT_NORTH_SOUTH[12]), 1_000)

        assertNull("kecepatan palsu tidak boleh jadi dasar estimasi", calc.onNoFix(60_000))
    }

    @Test
    fun `ambang berbasis jarak memakai jarak sepanjang rute`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        val states = ride(calc, MRT_NORTH_SOUTH, 0, 12)
        val threshold = AlertThreshold.Distance(2_000.0)

        val firedAt = states.indexOfFirst { it.hasReached(threshold) }
        assertTrue(firedAt in 9..11)
        assertTrue(states[firedAt].distanceToDestinationMeters <= 2_000.0)
        assertFalse(states[firedAt - 1].hasReached(threshold))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tujuan yang tidak ada di line ditolak`() {
        ProximityCalculator(MRT_NORTH_SOUTH, "tj-koridor-1:0", TransitMode.MRT)
    }

    @Test
    fun `arah berbalik langsung di update pertama setelah putar balik`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        // Beberapa stasiun ke arah tujuan dulu, supaya ada momentum yang harus dibalik.
        val forward = ride(calc, MRT_NORTH_SOUTH, 0, 5)
        assertEquals(TravelDirection.TOWARD_DESTINATION, forward.last().direction)

        val reversed = calc.onLocation(at(MRT_NORTH_SOUTH[4]), 6 * 90_000L)

        assertEquals(TravelDirection.AWAY_FROM_DESTINATION, reversed.direction)
    }

    @Test
    fun `jitter GPS saat berhenti tidak mengubah arah`() {
        val calc = calculator(MRT_NORTH_SOUTH, 12, TransitMode.MRT)
        ride(calc, MRT_NORTH_SOUTH, 0, 5)
        val station = MRT_NORTH_SOUTH[5]

        // Kereta berhenti di stasiun; posisi bergeser beberapa puluh meter karena noise saja.
        val jittered = calc.onLocation(GeoPoint(station.lat - 0.0002, station.lng), 6 * 90_000L)

        assertEquals(TravelDirection.TOWARD_DESTINATION, jittered.direction)
    }
}
