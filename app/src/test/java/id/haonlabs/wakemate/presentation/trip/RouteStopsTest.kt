package id.haonlabs.wakemate.presentation.trip

import id.haonlabs.wakemate.domain.MRT_NORTH_SOUTH
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.model.TripSession
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStopsTest {

    private val route = MRT_NORTH_SOUTH

    private fun tripAt(
        nearestIndex: Int,
        destinationIndex: Int,
        threshold: AlertThreshold = AlertThreshold.StationCount(2),
    ): ActiveTrip {
        val destination = route[destinationIndex]
        return ActiveTrip(
            session = TripSession(
                id = 1,
                destinationStationId = destination.id,
                lineId = destination.lineId,
                mode = TransitMode.MRT,
                threshold = threshold,
                startedAt = 0,
            ),
            destination = destination,
            lineName = "MRT Lin Utara-Selatan",
            proximity = stateAt(route[nearestIndex]),
        )
    }

    private fun stateAt(station: Station) = ProximityState(
        nearestStation = station,
        stationsRemaining = 0,
        distanceToDestinationMeters = 0.0,
        direction = TravelDirection.TOWARD_DESTINATION,
        offRouteMeters = 0.0,
        isOffRoute = false,
        isDeadReckoned = false,
        hasArrived = false,
    )

    private fun List<RouteStop>.statusOf(name: String) =
        firstOrNull { it.station.name == name }?.status

    @Test
    fun `menandai lewat, posisi sekarang, titik bangun, dan tujuan`() {
        // Di Bendungan Hilir (9) menuju Bundaran HI (12), alarm 2 stasiun sebelum tujuan.
        val stops = routeStops(tripAt(nearestIndex = 9, destinationIndex = 12), route)

        assertEquals(StopStatus.PASSED, stops.statusOf("Senayan Mastercard"))
        assertEquals(StopStatus.PASSED, stops.statusOf("Istora Mandiri"))
        assertEquals(StopStatus.CURRENT, stops.statusOf("Bendungan Hilir"))
        assertEquals(StopStatus.WAKE_POINT, stops.statusOf("Setiabudi Astra"))
        assertEquals(StopStatus.UPCOMING, stops.statusOf("Dukuh Atas BNI"))
        assertEquals(StopStatus.DESTINATION, stops.statusOf("Bundaran HI Bank Jakarta"))
    }

    @Test
    fun `berhenti di tujuan, tidak menampilkan sisa line`() {
        val stops = routeStops(tripAt(nearestIndex = 9, destinationIndex = 12), route)

        assertEquals("Bundaran HI Bank Jakarta", stops.last().station.name)
    }

    @Test
    fun `hanya beberapa stasiun terakhir yang sudah lewat ikut ditampilkan`() {
        // Dari Blok M (5) tujuan Bundaran HI: 5 stasiun sebelumnya tidak perlu semuanya muncul.
        val stops = routeStops(tripAt(nearestIndex = 5, destinationIndex = 12), route)

        assertEquals(2, stops.count { it.status == StopStatus.PASSED })
        assertEquals("Haji Nawi", stops.first().station.name)
    }

    @Test
    fun `arah sebaliknya membalik mana yang sudah lewat`() {
        // Perjalanan turun: Setiabudi (10) menuju Blok M (5) di line yang sama.
        val stops = routeStops(tripAt(nearestIndex = 10, destinationIndex = 5), route)

        assertEquals(StopStatus.PASSED, stops.statusOf("Dukuh Atas BNI"))
        assertEquals(StopStatus.CURRENT, stops.statusOf("Setiabudi Astra"))
        assertEquals(StopStatus.WAKE_POINT, stops.statusOf("Senayan Mastercard"))
        assertEquals(StopStatus.DESTINATION, stops.statusOf("Blok M BCA"))
        assertNull("stasiun di luar rentang tidak ditampilkan", stops.statusOf("Haji Nawi"))
    }

    @Test
    fun `ambang berbasis jarak tidak punya titik bangun di stasiun tertentu`() {
        val stops = routeStops(
            tripAt(nearestIndex = 9, destinationIndex = 12, threshold = AlertThreshold.Distance(1_500.0)),
            route,
        )

        assertTrue(stops.none { it.status == StopStatus.WAKE_POINT })
        assertEquals(StopStatus.DESTINATION, stops.statusOf("Bundaran HI Bank Jakarta"))
    }

    @Test
    fun `titik bangun tidak menimpa tujuan kalau ambangnya nol stasiun`() {
        val stops = routeStops(
            tripAt(nearestIndex = 9, destinationIndex = 12, threshold = AlertThreshold.StationCount(0)),
            route,
        )

        assertEquals(StopStatus.DESTINATION, stops.statusOf("Bundaran HI Bank Jakarta"))
        assertTrue(stops.none { it.status == StopStatus.WAKE_POINT })
    }

    @Test
    fun `sebelum fix GPS pertama belum ada yang bisa digambar`() {
        val trip = tripAt(nearestIndex = 9, destinationIndex = 12).copy(proximity = null)

        assertTrue(routeStops(trip, route).isEmpty())
    }

    @Test
    fun `rute kosong tidak membuat crash`() {
        assertTrue(routeStops(tripAt(nearestIndex = 9, destinationIndex = 12), emptyList()).isEmpty())
    }
}
