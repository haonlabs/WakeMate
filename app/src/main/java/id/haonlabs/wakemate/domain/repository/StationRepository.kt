package id.haonlabs.wakemate.domain.repository

import id.haonlabs.wakemate.domain.model.Line
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import kotlinx.coroutines.flow.Flow

interface StationRepository {
    /** Pastikan database stasiun sudah terisi; idempoten. */
    suspend fun ensureSeeded()

    fun search(mode: TransitMode, query: String): Flow<List<StationWithLine>>

    /** Urut sesuai `sequenceInLine`, siap dipakai ProximityCalculator. */
    suspend fun stationsInLine(lineId: String): List<Station>

    suspend fun lines(mode: TransitMode): List<Line>
}
