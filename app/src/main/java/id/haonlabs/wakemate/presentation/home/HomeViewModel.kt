package id.haonlabs.wakemate.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.repository.StationRepository
import id.haonlabs.wakemate.domain.usecase.SearchStationsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: StationRepository,
    searchStations: SearchStationsUseCase,
) : ViewModel() {

    private val _mode = MutableStateFlow(TransitMode.KRL)
    val mode: StateFlow<TransitMode> = _mode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<StationWithLine>> =
        combine(_isReady, _mode, _query) { ready, mode, query -> Triple(ready, mode, query) }
            .flatMapLatest { (ready, mode, query) ->
                if (ready) searchStations(mode, query) else flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            _isReady.value = true
        }
    }

    fun onModeSelected(mode: TransitMode) {
        _mode.value = mode
    }

    fun onQueryChanged(query: String) {
        _query.value = query
    }
}
