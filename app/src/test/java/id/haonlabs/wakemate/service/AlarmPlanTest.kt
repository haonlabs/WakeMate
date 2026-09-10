package id.haonlabs.wakemate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPlanTest {

    @Test
    fun `speaker tidak pernah dipakai kalau user melarangnya`() {
        // Janji yang paling mudah dilanggar diam-diam; dikunci untuk kedua kondisi headset.
        assertNull(alarmPlan(hasHeadset = true, allowSpeaker = false).speakerAfterMs)
        assertNull(alarmPlan(hasHeadset = false, allowSpeaker = false).speakerAfterMs)
    }

    @Test
    fun `headset dipakai kalau ada`() {
        assertTrue(alarmPlan(hasHeadset = true, allowSpeaker = true).useHeadset)
    }

    @Test
    fun `tanpa headset speaker datang jauh lebih cepat`() {
        val withHeadset = alarmPlan(hasHeadset = true, allowSpeaker = true).speakerAfterMs!!
        val without = alarmPlan(hasHeadset = false, allowSpeaker = true).speakerAfterMs!!

        // Kalau tidak ada headset, sejauh ini hanya getar yang menanggung — menunggu selama
        // kondisi bersuara justru membuat user kebablasan.
        assertTrue("$without harus jauh di bawah $withHeadset", without < withHeadset / 2)
    }

    @Test
    fun `dengan headset speaker ditahan beberapa menit dulu`() {
        assertEquals(
            SPEAKER_AFTER_HEADSET_MS,
            alarmPlan(hasHeadset = true, allowSpeaker = true).speakerAfterMs,
        )
        assertTrue(SPEAKER_AFTER_HEADSET_MS >= 2 * 60_000L)
    }

    @Test
    fun `tanpa headset dan speaker dilarang, hanya getar yang tersisa`() {
        val plan = alarmPlan(hasHeadset = false, allowSpeaker = false)

        assertTrue(!plan.useHeadset && plan.speakerAfterMs == null)
    }
}
