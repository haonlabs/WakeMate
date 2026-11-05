package id.haonlabs.wakemate.domain.live

import id.haonlabs.wakemate.domain.model.TransitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmLinkTest {
    private fun route(id: String, short: String, kind: String) = TjRoute(id, short, "", kind, "000000", "FFFFFF")

    @Test
    fun `moda alarm per jenis rute`() {
        assertEquals(TransitMode.KRL, route("krl:BOGOR", "B", "KRL").alarmMode())
        assertEquals(TransitMode.MRT, route("mrt:north-south", "MRT", "MRT").alarmMode())
        assertEquals(TransitMode.LRT, route("lrtjbd:bekasi", "LRT-B", "LRT Jabodebek").alarmMode())
        assertEquals(TransitMode.LRT, route("lrtj:selatan", "LRTJ", "LRT Jakarta").alarmMode())
        assertEquals(TransitMode.TRANSJAKARTA, route("1", "1", "BRT").alarmMode())
    }

    @Test
    fun `rute tanpa jalur di data alarm tidak punya tombol alarm`() {
        assertNull(route("JAK.31", "JAK.31", "Mikrotrans").alarmMode())
        assertNull(route("1F", "1F", "BRT").alarmMode()) // cabang BRT bukan koridor di data alarm
        assertNull(route("5C", "5C", "Angkutan Umum Integrasi").alarmMode())
    }

    @Test
    fun `petunjuk jalur`() {
        assertEquals("krl-bogor", route("krl:BOGOR", "B", "KRL").alarmLineHint())
        assertEquals("lrt-jabodebek-cibubur", route("lrtjbd:cibubur", "LRT-C", "LRT Jabodebek").alarmLineHint())
        assertEquals("tj-koridor-6", route("6", "6", "BRT").alarmLineHint())
        assertNull(route("krl:MERAK", "LM", "KRL").alarmLineHint())
    }
}
