package id.haonlabs.wakemate.data.repository

import id.haonlabs.wakemate.data.local.StationDao
import id.haonlabs.wakemate.data.local.StationEntity
import id.haonlabs.wakemate.data.local.StationWithLineRow
import id.haonlabs.wakemate.data.local.LineDao
import id.haonlabs.wakemate.data.local.LineEntity
import id.haonlabs.wakemate.data.seed.DatabaseSeeder
import id.haonlabs.wakemate.domain.model.Line
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.repository.StationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StationRepositoryImpl @Inject constructor(
    private val stationDao: StationDao,
    private val lineDao: LineDao,
    private val seeder: DatabaseSeeder,
) : StationRepository {

    override suspend fun ensureSeeded() = seeder.ensureSeeded()

    override fun search(mode: TransitMode, query: String): Flow<List<StationWithLine>> =
        stationDao.search(mode, query).map { rows -> rows.map { it.toDomain() } }

    override suspend fun stationsInLine(lineId: String): List<Station> =
        stationDao.getStationsInLine(lineId).map { it.toDomain() }

    override suspend fun lines(mode: TransitMode): List<Line> =
        lineDao.getLines(mode).map { it.toDomain() }
}

private fun StationEntity.toDomain() =
    Station(id, name, mode, lat, lng, lineId, sequenceInLine)

private fun StationWithLineRow.toDomain() = StationWithLine(
    station = Station(id, name, mode, lat, lng, lineId, sequenceInLine),
    lineName = lineName,
)

private fun LineEntity.toDomain() = Line(id, name, mode)
