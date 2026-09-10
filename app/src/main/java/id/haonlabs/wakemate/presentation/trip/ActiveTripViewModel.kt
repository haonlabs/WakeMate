package id.haonlabs.wakemate.presentation.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import id.haonlabs.wakemate.domain.usecase.CancelTripUseCase
import id.haonlabs.wakemate.domain.usecase.CompleteTripUseCase
import id.haonlabs.wakemate.domain.usecase.ObserveTripProgressUseCase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActiveTripViewModel @Inject constructor(
    observeTripProgress: ObserveTripProgressUseCase,
    private val cancelTrip: CancelTripUseCase,
    private val completeTrip: CompleteTripUseCase,
) : ViewModel() {

    val trip: StateFlow<ActiveTrip?> = observeTripProgress()

    fun onCancel() = viewModelScope.launch { cancelTrip() }

    fun onComplete() = viewModelScope.launch { completeTrip() }
}
