package id.haonlabs.wakemate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.usecase.ObserveTripProgressUseCase
import id.haonlabs.wakemate.domain.usecase.RestoreActiveTripUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    observeTripProgress: ObserveTripProgressUseCase,
    private val restoreActiveTrip: RestoreActiveTripUseCase,
) : ViewModel() {

    val hasActiveTrip: StateFlow<Boolean> = observeTripProgress()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { restoreActiveTrip() }
    }
}
