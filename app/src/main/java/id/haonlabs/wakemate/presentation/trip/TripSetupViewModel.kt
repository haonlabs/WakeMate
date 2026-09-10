package id.haonlabs.wakemate.presentation.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.usecase.StartTripUseCase
import id.haonlabs.wakemate.presentation.TripSetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.navigation.toRoute
import javax.inject.Inject

data class TripSetupUiState(
    val station: Station? = null,
    val lineName: String = "",
    val threshold: AlertThreshold = AlertThreshold.DEFAULT,
    val started: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TripSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stationRepository: StationRepository,
    private val startTrip: StartTripUseCase,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<TripSetup>()

    private val _state = MutableStateFlow(TripSetupUiState())
    val state: StateFlow<TripSetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val stations = stationRepository.stationsInLine(route.lineId)
            val station = stations.firstOrNull { it.id == route.stationId }
            val lineName = stationRepository.lines(station?.mode ?: return@launch)
                .firstOrNull { it.id == route.lineId }?.name.orEmpty()
            _state.update { it.copy(station = station, lineName = lineName) }
        }
    }

    fun onThresholdChanged(threshold: AlertThreshold) {
        _state.update { it.copy(threshold = threshold) }
    }

    fun onStart() {
        val station = _state.value.station ?: return
        viewModelScope.launch {
            startTrip(station, _state.value.threshold)
                .onSuccess { _state.update { s -> s.copy(started = true, error = null) } }
                .onFailure { error -> _state.update { s -> s.copy(error = error.message) } }
        }
    }

    fun onErrorShown() {
        _state.update { it.copy(error = null) }
    }
}
