package id.haonlabs.wakemate.presentation.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import id.haonlabs.wakemate.domain.usecase.CancelTripUseCase
import id.haonlabs.wakemate.domain.usecase.ObserveTripProgressUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    observeTripProgress: ObserveTripProgressUseCase,
    private val stationRepository: StationRepository,
    private val cancelTrip: CancelTripUseCase,
) : ViewModel() {

    val trip: StateFlow<ActiveTrip?> = observeTripProgress()

    private val route = MutableStateFlow<List<Station>>(emptyList())

    val stops: StateFlow<List<RouteStop>> = combine(trip, route) { current, stations ->
        current?.let { routeStops(it, stations) }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            trip.collect { current ->
                // Rute hanya dimuat ulang kalau line-nya berganti, bukan tiap update lokasi.
                val lineId = current?.session?.lineId ?: return@collect
                if (route.value.firstOrNull()?.lineId != lineId) {
                    route.value = stationRepository.stationsInLine(lineId)
                }
            }
        }
    }

    fun onCancel() = viewModelScope.launch { cancelTrip() }
}
