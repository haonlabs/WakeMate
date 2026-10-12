package id.haonlabs.wakemate.data.live

import id.haonlabs.wakemate.domain.live.HalteEta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArmadaPayloadTest {

    /** Payload asli dari broker TJ, 12 Okt 2026: halte pertama tanpa field eta. */
    private val sample = javaClass.getResource("/live/armada_sample.json")!!.readText()

    @Test
    fun `parses a real armada payload`() {
        val bus = parseArmada(sample, receivedAt = 42)!!
        assertEquals("DMR-250201", bus.busNo)
        assertEquals("14A", bus.routeId)
        assertEquals("14A-L02", bus.tripId)
        assertEquals("JIS", bus.headsign)
        assertEquals(1791779503000, bus.updatedAt)
        assertEquals(-6.140586, bus.lat, 1e-9)
        assertEquals(
            listOf(
                HalteEta("H00286P", null),
                HalteEta("B04789P", 2),
                HalteEta("B05550P", 3),
                HalteEta("H00287P", 4),
            ),
            bus.remaining,
        )
    }

    @Test
    fun `tolerates empty eta, merges gates of one halte, and rejects junk`() {
        val payload = """
            {"latitude":-6.2,"longitude":106.8,"route_code":"1","bus_body_no":"TJ-1","trip_id":"1-L01",
             "stops":[{"parent_stop_id":"H1","stop_id":"G1","eta":""},
                      {"parent_stop_id":"H1","stop_id":"G2","eta":1},
                      {"parent_stop_id":"H2","eta":3}],
             "new_field_from_the_future":true}
        """.trimIndent()
        val bus = parseArmada(payload, receivedAt = 42)!!
        assertEquals(listOf(HalteEta("H1", null), HalteEta("H2", 3)), bus.remaining)
        assertEquals(42, bus.updatedAt)

        assertNull(parseArmada("not json", 0))
        assertNull(parseArmada("""{"route_code":"1","bus_body_no":"TJ-1"}""", 0))
    }
}
