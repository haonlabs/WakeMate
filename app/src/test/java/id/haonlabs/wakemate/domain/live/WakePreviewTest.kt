package id.haonlabs.wakemate.domain.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePreviewTest {

    private val bus = LiveBus(
        busNo = "MYS-18171", routeId = "1", tripId = "1-R01", headsign = "Blok M", busType = "Single Bus",
        lat = 0.0, lng = 0.0, bearing = 0.0, updatedAt = 0,
        remaining = listOf(
            HalteEta("BI", 2), HalteEta("HI", 4), HalteEta("TOS", 5), HalteEta("DA", 8), HalteEta("SET", 10),
        ),
    )

    @Test
    fun `stops lead names the halte the alarm fires at, matching evaluate`() {
        // Dukuh Atas 4 halte lagi; "2 halte sebelumnya" = bunyi setelah bus meninggalkan Bundaran HI.
        assertEquals(WakePreview.AtHalte("HI", minutesBefore = 4), wakePreview(bus, "DA", LiveLead.Stops(2)))

        // Bukti sejalan dengan evaluate(): setelah HI ditinggalkan, ambang tercapai.
        val afterHi = bus.copy(remaining = bus.remaining.drop(2))
        assertEquals(true, approachOf(afterHi, "DA")!!.hasReached(LiveLead.Stops(2)))
        val atHi = bus.copy(remaining = bus.remaining.drop(1))
        assertEquals(false, approachOf(atHi, "DA")!!.hasReached(LiveLead.Stops(2)))
    }

    @Test
    fun `lead already reached fires immediately`() {
        assertEquals(WakePreview.Immediately, wakePreview(bus, "HI", LiveLead.Stops(3)))
        assertEquals(WakePreview.Immediately, wakePreview(bus, "DA", LiveLead.Minutes(10)))
    }

    @Test
    fun `minutes lead and unknown destination`() {
        assertEquals(WakePreview.Minutes(5), wakePreview(bus, "DA", LiveLead.Minutes(5)))
        assertNull(wakePreview(bus, "KOTA", LiveLead.Stops(2)))
    }

    @Test
    fun `missing eta keeps the halte but drops the minutes`() {
        val noEta = bus.copy(remaining = bus.remaining.map { if (it.halteId == "HI") it.copy(etaMin = null) else it })
        assertEquals(WakePreview.AtHalte("HI", minutesBefore = null), wakePreview(noEta, "DA", LiveLead.Stops(2)))
    }
}
