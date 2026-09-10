package id.haonlabs.wakemate.domain.model

import id.haonlabs.wakemate.domain.proximity.ProximityState

enum class TripStatus { ACTIVE, COMPLETED, CANCELLED }

enum class AlertThresholdType { DISTANCE, STATION_COUNT }

sealed interface AlertThreshold {
    data class Distance(val meters: Double) : AlertThreshold
    data class StationCount(val stations: Int) : AlertThreshold

    companion object {
        /** FR-6: default 2 stasiun, karena jarak km tidak seragam antar stasiun. */
        val DEFAULT: AlertThreshold = StationCount(2)
    }
}

data class TripSession(
    val id: Long = 0,
    val destinationStationId: String,
    val lineId: String,
    val mode: TransitMode,
    val threshold: AlertThreshold,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: TripStatus = TripStatus.ACTIVE,
)

/**
 * Tidak perlu mengecek arah perjalanan di sini: kalau user menjauh, kedua metrik justru membesar
 * sehingga threshold tidak tercapai dengan sendirinya.
 */
fun ProximityState.hasReached(threshold: AlertThreshold): Boolean = when (threshold) {
    is AlertThreshold.Distance -> distanceToDestinationMeters <= threshold.meters
    is AlertThreshold.StationCount -> stationsRemaining <= threshold.stations
}
