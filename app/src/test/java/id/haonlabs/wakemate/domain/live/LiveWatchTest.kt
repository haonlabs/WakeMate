package id.haonlabs.wakemate.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWatchTest {

    private fun bus(
        no: String,
        vararg remaining: Pair<String, Int?>,
        trip: String = "1-L01",
    ) = LiveBus(
        busNo = no, routeId = "1", tripId = trip, headsign = "Kota", busType = "Single Bus",
        lat = -6.2, lng = 106.8, bearing = 0.0, updatedAt = 0,
        remaining = remaining.map { (id, eta) -> HalteEta(id, eta) },
    )

    private fun onboard(lead: LiveLead) = LiveWatch.Onboard(
        busNo = "TJ-1", tripId = "1-L01", routeId = "1",
        halteId = "C", halteName = "Halte C", lead = lead, startedAt = 0,
    )

    private fun arrival(lead: LiveLead) = LiveWatch.Arrival(
        routeId = "1", halteId = "C", halteName = "Halte C", lead = lead, startedAt = 0,
    )

    @Test
    fun `onboard waits while destination is further than the lead`() {
        val buses = mapOf("TJ-1" to bus("TJ-1", "A" to 1, "B" to 3, "C" to 6))
        val verdict = evaluate(onboard(LiveLead.Stops(2)), buses)
        assertEquals(WatchVerdict.Waiting(approachOf(buses.getValue("TJ-1"), "C")), verdict)
        assertEquals(3, (verdict as WatchVerdict.Waiting).approach?.stopsAway)
    }

    @Test
    fun `onboard alerts when stops remaining reach the lead`() {
        val buses = mapOf("TJ-1" to bus("TJ-1", "B" to 2, "C" to 5))
        assertTrue(evaluate(onboard(LiveLead.Stops(2)), buses) is WatchVerdict.Alert)
    }

    @Test
    fun `onboard alerts on minutes`() {
        val buses = mapOf("TJ-1" to bus("TJ-1", "A" to 1, "B" to 3, "C" to 5))
        assertTrue(evaluate(onboard(LiveLead.Minutes(5)), buses) is WatchVerdict.Alert)
        assertTrue(evaluate(onboard(LiveLead.Minutes(4)), buses) is WatchVerdict.Waiting)
    }

    @Test
    fun `onboard alerts late rather than never once destination is behind the bus`() {
        val passed = mapOf("TJ-1" to bus("TJ-1", "D" to 2, "E" to 4))
        assertEquals(WatchVerdict.Alert(null), evaluate(onboard(LiveLead.Stops(1)), passed))

        val turnedAround = mapOf("TJ-1" to bus("TJ-1", "B" to 2, "C" to 4, trip = "1-R01"))
        assertEquals(WatchVerdict.Alert(null), evaluate(onboard(LiveLead.Stops(1)), turnedAround))
    }

    @Test
    fun `onboard keeps waiting while the bus is missing from the feed`() {
        assertEquals(WatchVerdict.Waiting(null), evaluate(onboard(LiveLead.Stops(2)), emptyMap()))
    }

    @Test
    fun `missing eta only counts as reached when the halte is next`() {
        val atDoor = approachOf(bus("TJ-1", "C" to null), "C")!!
        val farAway = approachOf(bus("TJ-1", "A" to null, "B" to null, "C" to null), "C")!!
        assertTrue(atDoor.hasReached(LiveLead.Minutes(3)))
        assertTrue(!farAway.hasReached(LiveLead.Minutes(3)))
    }

    @Test
    fun `arrival picks the soonest bus that still has the halte ahead`() {
        val buses = listOf(
            bus("TJ-1", "C" to 12),
            bus("TJ-2", "B" to 2, "C" to 4),
            bus("TJ-3", "D" to 1), // sudah lewat C
            bus("TJ-4", "B" to null, "C" to null),
        ).associateBy { it.busNo }

        assertEquals("TJ-2", nextArrival(buses.values, "C")?.bus?.busNo)
        assertTrue(evaluate(arrival(LiveLead.Minutes(5)), buses) is WatchVerdict.Alert)
        assertTrue(evaluate(arrival(LiveLead.Minutes(3)), buses) is WatchVerdict.Waiting)
    }

    @Test
    fun `arrival ignores buses running the opposite direction through the same halte`() {
        val buses = listOf(
            bus("TJ-1", "C" to 1, trip = "1-R01"), // arah sebaliknya, lewat halte yang sama
            bus("TJ-2", "B" to 2, "C" to 8, trip = "1-L01"),
        ).associateBy { it.busNo }
        val watch = arrival(LiveLead.Minutes(5)).copy(tripIds = setOf("1-L01", "1-L02"))

        val verdict = evaluate(watch, buses)
        assertEquals("TJ-2", (verdict as WatchVerdict.Waiting).approach?.bus?.busNo)
    }

    @Test
    fun `arrival without any bus heading there waits`() {
        val buses = mapOf("TJ-3" to bus("TJ-3", "D" to 1))
        assertNull(nextArrival(buses.values, "C"))
        assertEquals(WatchVerdict.Waiting(null), evaluate(arrival(LiveLead.Minutes(5)), buses))
    }
}
