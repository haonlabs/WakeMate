package id.haonlabs.wakemate.domain.usecase

import id.haonlabs.wakemate.domain.model.Line
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindAlarmStationTest {

    private fun st(id: String, line: String, lat: Double, lng: Double, seq: Int = 0) =
        Station(id, id, TransitMode.KRL, lat, lng, line, seq)

    // Manggarai ada di dua jalur (dua stasiun terpisah ~20 m), Tebet hanya di Bogor.
    private val bogorManggarai = st("bogor:manggarai", "krl-bogor", -6.2100, 106.8500)
    private val cikarangManggarai = st("cikarang:manggarai", "krl-cikarang", -6.2101, 106.8501)
    private val tebet = st("bogor:tebet", "krl-bogor", -6.2260, 106.8580, 1)

    private val repo = object : StationRepository {
        private val lines = mapOf("krl-bogor" to listOf(bogorManggarai, tebet), "krl-cikarang" to listOf(cikarangManggarai))
        override suspend fun ensureSeeded() = Unit
        override fun search(mode: TransitMode, query: String): Flow<List<StationWithLine>> = emptyFlow()
        override suspend fun stationsInLine(lineId: String) = lines[lineId].orEmpty()
        override suspend fun lines(mode: TransitMode) = lines.keys.map { Line(it, it, mode) }
    }

    private val useCase = FindAlarmStationUseCase(repo)

    @Test
    fun `memilih stasiun terdekat dalam radius`() = runBlocking {
        assertEquals("bogor:tebet", useCase(TransitMode.KRL, -6.2262, 106.8581)?.id)
    }

    @Test
    fun `jalur yang disukai menang di stasiun transit`() = runBlocking {
        assertEquals("cikarang:manggarai", useCase(TransitMode.KRL, -6.2100, 106.8500, preferredLineId = "krl-cikarang")?.id)
        assertEquals("bogor:manggarai", useCase(TransitMode.KRL, -6.2100, 106.8500, preferredLineId = "krl-bogor")?.id)
    }

    @Test
    fun `jalur yang disukai tapi stasiunnya jauh jatuh ke jalur lain`() = runBlocking {
        // Tebet tidak ada di Cikarang: jatuh ke stasiun terdekat di jalur mana pun.
        assertEquals("bogor:tebet", useCase(TransitMode.KRL, -6.2260, 106.8580, preferredLineId = "krl-cikarang")?.id)
    }

    @Test
    fun `di luar radius tidak ada hasil`() = runBlocking {
        assertNull(useCase(TransitMode.KRL, -6.30, 106.90))
    }

    @Test
    fun `radius halte bus lebih sempit daripada stasiun rel`() {
        assertEquals(120.0, matchRadiusMeters(TransitMode.TRANSJAKARTA), 0.0)
        assertEquals(300.0, matchRadiusMeters(TransitMode.LRT), 0.0)
    }
}
