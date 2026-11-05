package id.haonlabs.wakemate.presentation.live

import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.TjRoute
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleNoteTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val krl = TjRoute("krl:BOGOR", "B", "Commuter Line Bogor", "KRL", "E30A16", "FFFFFF")
    private val bus = TjRoute("1", "1", "Blok M - Kota", "BRT", "D32027", "FFFFFF")
    private val net = TjNetwork(listOf(krl, bus), emptyMap(), emptyList(), fixedScheduleValidUntil = LocalDate.of(2026, 12, 31))

    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 9, 0, 0, 0, zone)

    @Test
    fun `hari kerja dan Minggu dalam masa berlaku tidak ada catatan`() {
        assertNull(scheduleNote(krl, net, at(2026, 11, 5)))  // Kamis
        assertNull(scheduleNote(krl, net, at(2026, 11, 8)))  // Minggu
    }

    @Test
    fun `Sabtu KRL diberi tahu datanya tidak ada`() {
        assertTrue(scheduleNote(krl, net, at(2026, 11, 7))!!.contains("Sabtu"))
    }

    @Test
    fun `setelah masa berlaku jadwal KRL ditandai kadaluarsa dengan tanggalnya`() {
        val note = scheduleNote(krl, net, at(2027, 1, 4))
        assertNotNull(note)
        assertTrue(note!!.contains("31 Desember 2026"))
        assertNull(scheduleNote(krl, net, at(2026, 12, 31))) // hari terakhir masih berlaku
    }

    @Test
    fun `rute selain KRL tidak terpengaruh`() {
        assertNull(scheduleNote(bus, net, at(2027, 1, 9)))  // Sabtu setelah kadaluarsa pun tidak
    }
}
