package id.haonlabs.wakemate.presentation.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.proximity.routeDistanceMeters
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.settings.AlarmSettings
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
    val speakerFallback: Boolean = true,
    val started: Boolean = false,
    val error: String? = null,
    /** Jumlah stasiun -> jarak sebenarnya dari titik itu ke tujuan, sepanjang rute. */
    val leadDistanceMeters: Map<Int, Double> = emptyMap(),
)

@HiltViewModel
class TripSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stationRepository: StationRepository,
    private val alarmSettings: AlarmSettings,
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
            // Ambang "2 stasiun" berarti hal yang sangat berbeda antar line: di Lin Bogor ruas
            // terakhirnya belasan kilometer. Jaraknya ditunjukkan supaya user tidak dibangunkan
            // jauh lebih awal dari yang dikiranya.
            val destinationIndex = stations.indexOfFirst { it.id == route.stationId }
            val leads = STATION_OPTIONS.mapNotNull { count ->
                val wakeIndex = destinationIndex - count
                val reverseIndex = destinationIndex + count
                val index = if (wakeIndex in stations.indices) wakeIndex
                else if (reverseIndex in stations.indices) reverseIndex
                else return@mapNotNull null
                count to routeDistanceMeters(stations, index, destinationIndex)
            }.toMap()
            _state.update {
                it.copy(station = station, lineName = lineName, leadDistanceMeters = leads)
            }
        }
        viewModelScope.launch {
            alarmSettings.speakerFallback.collect { allowed ->
                _state.update { it.copy(speakerFallback = allowed) }
            }
        }
    }

    fun onSpeakerFallbackChanged(allowed: Boolean) {
        viewModelScope.launch { alarmSettings.setSpeakerFallback(allowed) }
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

    companion object {
        val STATION_OPTIONS = listOf(1, 2, 3)
    }
}
