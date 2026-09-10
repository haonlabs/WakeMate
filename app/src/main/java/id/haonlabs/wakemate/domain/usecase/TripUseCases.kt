package id.haonlabs.wakemate.domain.usecase

import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.repository.TripRepository
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import id.haonlabs.wakemate.domain.trip.TripTracker
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val stationRepository: StationRepository,
    private val tracker: TripTracker,
) {
    /**
     * Rute divalidasi sebelum service jalan: kalau line-nya cuma punya satu stasiun atau tujuan
     * tidak ada di dalamnya, lebih baik gagal di sini daripada service hidup tanpa bisa menghitung.
     */
    suspend operator fun invoke(destination: Station, threshold: AlertThreshold): Result<ActiveTrip> {
        val route = stationRepository.stationsInLine(destination.lineId)
        if (route.size < 2) {
            return Result.failure(IllegalStateException("Line ${destination.lineId} tidak punya cukup stasiun"))
        }
        if (route.none { it.id == destination.id }) {
            return Result.failure(IllegalStateException("${destination.name} tidak ada di line-nya"))
        }
        val trip = tripRepository.start(destination, threshold)
        tracker.start()
        return Result.success(trip)
    }
}

class ObserveTripProgressUseCase @Inject constructor(
    private val tripRepository: TripRepository,
) {
    operator fun invoke(): StateFlow<ActiveTrip?> = tripRepository.activeTrip
}

class CancelTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tracker: TripTracker,
) {
    suspend operator fun invoke() {
        tracker.stop()
        tripRepository.cancel()
    }
}

class CompleteTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tracker: TripTracker,
) {
    suspend operator fun invoke() {
        tracker.stop()
        tripRepository.complete()
    }
}

/** Dipanggil saat app dibuka: kalau ada sesi yang masih ACTIVE, UI langsung menyusul ke sana. */
class RestoreActiveTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val tracker: TripTracker,
) {
    suspend operator fun invoke(): ActiveTrip? =
        tripRepository.restoreActiveSession()?.also { tracker.start() }
}
