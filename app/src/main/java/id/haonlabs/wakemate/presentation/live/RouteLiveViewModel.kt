package id.haonlabs.wakemate.presentation.live

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.data.live.FeedStatus
import id.haonlabs.wakemate.data.live.LiveWatchRepository
import id.haonlabs.wakemate.data.live.LiveWatchState
import id.haonlabs.wakemate.data.live.TjLiveFeed
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.Approach
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.LiveBus
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.domain.live.TjTrip
import id.haonlabs.wakemate.domain.live.approachOf
import id.haonlabs.wakemate.domain.live.nextArrival
import id.haonlabs.wakemate.presentation.RouteLive
import id.haonlabs.wakemate.presentation.TripPermissions
import id.haonlabs.wakemate.service.LiveAlarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HalteRow(val halte: Halte, val next: Approach?)

data class BusItem(val bus: LiveBus, val distanceMeters: Float?)

data class RouteLiveUi(
    val route: TjRoute? = null,
    val directions: List<TjTrip> = emptyList(),
    val directionIndex: Int = 0,
    /** Semua trip searah arah terpilih; bus di luar set ini sedang ke arah lain. */
    val directionTripIds: Set<String> = emptySet(),
    val buses: List<BusItem> = emptyList(),
    val selectedBus: LiveBus? = null,
    /** User sedang memilih halte turun untuk bus yang dipilih. */
    val boarding: Boolean = false,
    val halte: List<HalteRow> = emptyList(),
    val feed: FeedStatus = FeedStatus.IDLE,
    val watch: LiveWatchState? = null,
) {
    val direction: TjTrip? get() = directions.getOrNull(directionIndex)
}

private data class Selection(
    /** null sampai user memilih arah sendiri. */
    val directionIndex: Int? = null,
    val busNo: String? = null,
    val boarding: Boolean = false,
)

@HiltViewModel
class RouteLiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    staticSource: TjStaticSource,
    feed: TjLiveFeed,
    private val watches: LiveWatchRepository,
    private val liveAlarms: LiveAlarms,
) : ViewModel() {

    private val routeId = savedStateHandle.toRoute<RouteLive>().routeId
    private val network = MutableStateFlow<TjNetwork?>(null)
    private val selection = MutableStateFlow(Selection())
    private val userLocation = MutableStateFlow<Location?>(null)

    // Ikut siklus layar: begitu layar ditinggal, subscription MQTT rute ini ikut dilepas.
    private val buses = feed.route(routeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val state: StateFlow<RouteLiveUi> = combine(
        network, buses, selection, combine(feed.status, watches.state, userLocation, ::Triple),
    ) { net, live, sel, (feedStatus, watch, location) ->
        val route = net?.routeById?.get(routeId)
        val directions = net?.directions(routeId).orEmpty()
        // Default ke arah yang busnya paling banyak jalan: varian pertama di GTFS sering trip
        // khusus yang sedang tidak beroperasi, dan daftar halte tanpa satu bus pun tidak berguna.
        val directionIndex = sel.directionIndex ?: net?.let { n ->
            directions.indices.maxByOrNull { i ->
                val ids = n.tripIdsToward(directions[i])
                live.values.count { it.tripId in ids }
            }
        } ?: 0
        val direction = directions.getOrNull(directionIndex)
        val towardIds = direction?.let { net?.tripIdsToward(it) }.orEmpty()
        val toward = live.values.filter { it.tripId in towardIds }
        val selectedBus = sel.busNo?.let(live::get)
        val halte = when {
            net == null -> emptyList()
            sel.boarding && selectedBus != null -> selectedBus.remaining.mapNotNull { stop ->
                net.halte[stop.halteId]?.let { HalteRow(it, approachOf(selectedBus, it.id)) }
            }
            direction != null -> direction.halteIds.mapNotNull { id ->
                net.halte[id]?.let { HalteRow(it, nextArrival(toward, id)) }
            }
            else -> emptyList()
        }
        RouteLiveUi(
            route = route,
            directions = directions,
            directionIndex = directionIndex,
            directionTripIds = towardIds,
            buses = live.values
                .map { BusItem(it, location?.distanceTo(it)) }
                .sortedWith(
                    compareBy<BusItem>({ it.bus.tripId !in towardIds })
                        .thenBy(nullsLast()) { it.distanceMeters }
                        .thenBy { it.bus.busNo }
                ),
            selectedBus = selectedBus,
            boarding = sel.boarding && selectedBus != null,
            halte = halte,
            feed = feedStatus,
            watch = watch?.takeIf { it.watch.routeId == routeId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteLiveUi())

    init {
        viewModelScope.launch { network.value = staticSource.network() }
        locateOnce()
    }

    fun onDirectionSelected(index: Int) = selection.update { it.copy(directionIndex = index, boarding = false) }

    /** Ketuk bus di peta/daftar; ketuk tempat kosong (null) menutup pilihan. */
    fun onBusSelected(busNo: String?) {
        selection.update { current ->
            val bus = busNo?.let { buses.value[it] }
            // Arah ikut bus yang dipilih supaya daftar halte cocok dengan bus itu.
            val net = network.value
            val index = bus?.let { b ->
                state.value.directions.indexOfFirst { net != null && b.tripId in net.tripIdsToward(it) }
            }?.takeIf { it >= 0 } ?: current.directionIndex
            current.copy(busNo = busNo, boarding = false, directionIndex = index)
        }
    }

    fun onBoardingChanged(boarding: Boolean) = selection.update { it.copy(boarding = boarding) }

    /** Menunggu di halte: ingatkan saat bus mana pun di rute ini hampir tiba. */
    fun startArrival(halte: Halte, lead: LiveLead) = viewModelScope.launch {
        liveAlarms.start(
            LiveWatch.Arrival(
                routeId = routeId, halteId = halte.id, halteName = halte.name,
                lead = lead, startedAt = System.currentTimeMillis(),
                tripIds = state.value.directionTripIds,
            )
        )
    }

    /** Di dalam bus terpilih: bangunkan sebelum sampai [halte]. */
    fun startOnboard(halte: Halte, lead: LiveLead) = viewModelScope.launch {
        val bus = state.value.selectedBus ?: return@launch
        liveAlarms.start(
            LiveWatch.Onboard(
                busNo = bus.busNo, tripId = bus.tripId, routeId = routeId,
                halteId = halte.id, halteName = halte.name,
                lead = lead, startedAt = System.currentTimeMillis(),
            )
        )
        selection.update { it.copy(boarding = false) }
    }

    fun stopWatch() = liveAlarms.stop()

    /**
     * Satu kali saja, untuk mengurutkan bus terdekat (bus yang sedang dinaiki hampir pasti yang
     * paling dekat). Tanpa izin lokasi daftar tetap jalan, hanya tidak diurutkan per jarak.
     */
    @SuppressLint("MissingPermission")
    private fun locateOnce() {
        if (!TripPermissions.hasForegroundLocation(context)) return
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { userLocation.value = it }
    }
}

private fun Location.distanceTo(bus: LiveBus): Float {
    val result = FloatArray(1)
    Location.distanceBetween(latitude, longitude, bus.lat, bus.lng, result)
    return result[0]
}
