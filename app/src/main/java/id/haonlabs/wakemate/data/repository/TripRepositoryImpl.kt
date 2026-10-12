package id.haonlabs.wakemate.data.repository

import id.haonlabs.wakemate.data.local.LineDao
import id.haonlabs.wakemate.data.local.StationDao
import id.haonlabs.wakemate.data.local.TripSessionDao
import id.haonlabs.wakemate.data.local.TripSessionEntity
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.AlertThresholdType
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TripSession
import id.haonlabs.wakemate.domain.model.TripStatus
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.repository.TripRepository
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripSessionDao: TripSessionDao,
    private val stationDao: StationDao,
    private val lineDao: LineDao,
) : TripRepository {

    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    override val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()

    override suspend fun start(destination: Station, threshold: AlertThreshold): ActiveTrip {
        // Hanya boleh ada satu sesi aktif; sisa sesi lama dianggap ditinggalkan user.
        tripSessionDao.getActiveSession()?.let {
            tripSessionDao.update(it.copy(status = TripStatus.CANCELLED, endedAt = System.currentTimeMillis()))
        }
        val entity = TripSessionEntity(
            destinationStationId = destination.id,
            lineId = destination.lineId,
            mode = destination.mode,
            alertThresholdType = threshold.type,
            alertThresholdValue = threshold.value,
            startedAt = System.currentTimeMillis(),
            status = TripStatus.ACTIVE,
        )
        val id = tripSessionDao.insert(entity)
        val trip = ActiveTrip(
            session = entity.copy(id = id).toDomain(),
            destination = destination,
            lineName = lineName(destination.lineId),
        )
        _activeTrip.value = trip
        return trip
    }

    override suspend fun restoreActiveSession(): ActiveTrip? {
        _activeTrip.value?.let { return it }
        val entity = tripSessionDao.getActiveSession() ?: return null
        val destination = stationDao.findById(entity.destinationStationId)
        if (destination == null) {
            // Data seed berubah dan stasiun tujuan hilang: tutup sesinya daripada tracking buta.
            tripSessionDao.update(entity.copy(status = TripStatus.CANCELLED, endedAt = System.currentTimeMillis()))
            return null
        }
        return ActiveTrip(
            session = entity.toDomain(),
            destination = Station(
                destination.id, destination.name, destination.mode,
                destination.lat, destination.lng, destination.lineId, destination.sequenceInLine,
            ),
            lineName = lineName(entity.lineId),
        ).also { _activeTrip.value = it }
    }

    override fun publishProgress(proximity: ProximityState) {
        _activeTrip.update { it?.copy(proximity = proximity) }
    }

    override fun markAlerted(atMs: Long) {
        _activeTrip.update { it?.copy(alertedAt = atMs) }
    }

    override suspend fun complete() = finish(TripStatus.COMPLETED)

    override suspend fun cancel() = finish(TripStatus.CANCELLED)

    private suspend fun finish(status: TripStatus) {
        _activeTrip.value = null
        // Baca ulang dari Room, bukan dari state in-memory: sesi bisa dimulai di proses sebelumnya.
        tripSessionDao.getActiveSession()?.let {
            tripSessionDao.update(it.copy(status = status, endedAt = System.currentTimeMillis()))
        }
    }

    override fun recentDestinations(limit: Int): Flow<List<StationWithLine>> =
        tripSessionDao.history().map { sessions ->
            sessions.distinctBy { it.destinationStationId }.take(limit).mapNotNull { session ->
                // Data seed bisa berubah; tujuan yang sudah tidak ada dilewati saja.
                stationDao.findById(session.destinationStationId)?.let { s ->
                    StationWithLine(
                        Station(s.id, s.name, s.mode, s.lat, s.lng, s.lineId, s.sequenceInLine),
                        lineName(s.lineId),
                    )
                }
            }
        }

    private suspend fun lineName(lineId: String): String =
        lineDao.findById(lineId)?.name ?: lineId
}

private val AlertThreshold.type: AlertThresholdType
    get() = when (this) {
        is AlertThreshold.Distance -> AlertThresholdType.DISTANCE
        is AlertThreshold.StationCount -> AlertThresholdType.STATION_COUNT
    }

private val AlertThreshold.value: Float
    get() = when (this) {
        is AlertThreshold.Distance -> meters.toFloat()
        is AlertThreshold.StationCount -> stations.toFloat()
    }

private fun TripSessionEntity.toDomain() = TripSession(
    id = id,
    destinationStationId = destinationStationId,
    lineId = lineId,
    mode = mode,
    threshold = when (alertThresholdType) {
        AlertThresholdType.DISTANCE -> AlertThreshold.Distance(alertThresholdValue.toDouble())
        AlertThresholdType.STATION_COUNT -> AlertThreshold.StationCount(alertThresholdValue.toInt())
    },
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
)
