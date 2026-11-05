package id.haonlabs.wakemate.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import id.haonlabs.wakemate.domain.usecase.CompleteTripUseCase
import id.haonlabs.wakemate.domain.usecase.ObserveTripProgressUseCase
import id.haonlabs.wakemate.domain.usecase.RestoreActiveTripUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Status alarm di tingkat aplikasi: bar alarm yang menempel di atas tab dan layar "alarm berbunyi"
 * harus muncul di layar mana pun user sedang berada.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    observeTripProgress: ObserveTripProgressUseCase,
    private val restoreActiveTrip: RestoreActiveTripUseCase,
    private val completeTrip: CompleteTripUseCase,
) : ViewModel() {

    val gpsTrip: StateFlow<ActiveTrip?> = observeTripProgress()

    val hasActiveTrip: StateFlow<Boolean> = gpsTrip
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { restoreActiveTrip() }
    }

    /** "Saya sudah bangun" untuk alarm GPS: sama dengan tombol Selesai di notifikasi. */
    fun onGpsAwake() = viewModelScope.launch { completeTrip() }
}
