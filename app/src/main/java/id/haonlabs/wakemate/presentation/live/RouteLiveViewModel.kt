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
import id.haonlabs.wakemate.data.live.LiveRoutePrefs
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
import id.haonlabs.wakemate.domain.live.WakePreview
import id.haonlabs.wakemate.domain.live.approachOf
import id.haonlabs.wakemate.domain.live.nextArrival
import id.haonlabs.wakemate.domain.live.wakePreview
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

/** [busHere] = bus searah yang sedang berada di halte ini (digambar langsung di garis waktu). */
data class HalteRow(val halte: Halte, val next: Approach?, val busHere: LiveBus? = null)

data class BusItem(val bus: LiveBus, val distanceMeters: Float?)

/** Isi bottom sheet: jelajah rute, memilih bus yang dinaiki, lalu memilih halte turun. */
sealed interface SheetMode {
    data object Browse : SheetMode
    data object PickBus : SheetMode
    data class Boarding(val busNo: String) : SheetMode
}

data class RouteLiveUi(
    val route: TjRoute? = null,
    val directions: List<TjTrip> = emptyList(),
    val directionIndex: Int = 0,
    /** Semua trip searah arah terpilih; bus di luar set ini sedang ke arah lain. */
    val directionTripIds: Set<String> = emptySet(),
    val buses: List<BusItem> = emptyList(),
    /** Bus searah, terdekat dulu — kandidat "bus yang kunaiki". */
    val towardBuses: List<BusItem> = emptyList(),
    val halte: List<HalteRow> = emptyList(),
    val mode: SheetMode = SheetMode.Browse,
    val boardingBus: LiveBus? = null,
    val boardingStops: List<HalteRow> = emptyList(),
    val destination: Halte? = null,
    val lead: LiveLead = LiveLead.Stops(2),
    val preview: WakePreview? = null,
    val feed: FeedStatus = FeedStatus.IDLE,
    val watch: LiveWatchState? = null,
    val favorite: Boolean = false,
    val userLocation: Location? = null,
    /** Halte searah terdekat dari posisi user, untuk "Tunggu di halte". */
    val nearestHalte: Halte? = null,
    val halteNames: Map<String, String> = emptyMap(),
) {
    val direction: TjTrip? get() = directions.getOrNull(directionIndex)
    val nearestBus: BusItem? get() = towardBuses.firstOrNull()
}

private data class Selection(
    /** null sampai user memilih arah sendiri. */
    val directionIndex: Int? = null,
    val mode: SheetMode = SheetMode.Browse,
    val destinationId: String? = null,
    val lead: LiveLead = LiveLead.Stops(2),
)

private data class Extras(val feed: FeedStatus, val watch: LiveWatchState?, val location: Location?, val favorite: Boolean)

@HiltViewModel
class RouteLiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    staticSource: TjStaticSource,
    feed: TjLiveFeed,
    private val liveAlarms: LiveAlarms,
    private val prefs: LiveRoutePrefs,
    watches: LiveWatchRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<RouteLive>()
    private val routeId = args.routeId
    private val network = MutableStateFlow<TjNetwork?>(null)
    private val selection = MutableStateFlow(
        Selection(mode = args.boardBus?.let { SheetMode.Boarding(it) } ?: SheetMode.Browse)
    )
    private val userLocation = MutableStateFlow<Location?>(null)

    // Ikut siklus layar: begitu layar ditinggal, subscription MQTT rute ini ikut dilepas.
    private val buses = feed.route(routeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val extras = combine(feed.status, watches.state, userLocation, prefs.favorites) { status, watch, location, favs ->
        Extras(status, watch, location, routeId in favs)
    }

    val state: StateFlow<RouteLiveUi> = combine(network, buses, selection, extras, ::build)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteLiveUi())

    private fun build(net: TjNetwork?, live: Map<String, LiveBus>, sel: Selection, ex: Extras): RouteLiveUi {
        val directions = net?.directions(routeId).orEmpty()
        // Default ke arah yang busnya paling banyak jalan: varian pertama di GTFS sering trip
        // khusus yang sedang tidak beroperasi, dan daftar halte tanpa satu bus pun tidak berguna.
        val directionIndex = sel.directionIndex ?: net?.let { n ->
            directions.indices.maxByOrNull { i -> n.tripIdsToward(directions[i]).let { ids -> live.values.count { it.tripId in ids } } }
        } ?: 0
        val direction = directions.getOrNull(directionIndex)
        val towardIds = direction?.let { net?.tripIdsToward(it) }.orEmpty()
        val items = live.values.map { BusItem(it, ex.location?.distanceTo(it.lat, it.lng)) }
        val byDistance = compareBy<BusItem, Float?>(nullsLast()) { it.distanceMeters }.thenBy { it.bus.busNo }
        val toward = items.filter { it.bus.tripId in towardIds }.sortedWith(byDistance)
        val towardBuses = toward.map { it.bus }

        val halteRows = if (net == null || direction == null) emptyList() else direction.halteIds.mapNotNull { id ->
            net.halte[id]?.let { h ->
                HalteRow(
                    halte = h,
                    next = nextArrival(towardBuses, id),
                    // Halte pertama di feed tanpa ETA = bus sedang/baru di halte itu.
                    busHere = towardBuses.firstOrNull { b ->
                        b.remaining.firstOrNull()?.let { it.halteId == id && (it.etaMin ?: 0) <= 0 } == true
                    },
                )
            }
        }
        val boardingBus = (sel.mode as? SheetMode.Boarding)?.let { live[it.busNo] }
        val boardingStops = if (net == null || boardingBus == null) emptyList() else boardingBus.remaining.mapNotNull { stop ->
            net.halte[stop.halteId]?.let { HalteRow(it, approachOf(boardingBus, it.id)) }
        }
        val destination = sel.destinationId?.let { net?.halte?.get(it) }
        return RouteLiveUi(
            route = net?.routeById?.get(routeId),
            directions = directions,
            directionIndex = directionIndex,
            directionTripIds = towardIds,
            buses = items.sortedWith(compareBy<BusItem> { it.bus.tripId !in towardIds }.then(byDistance)),
            towardBuses = toward,
            halte = halteRows,
            mode = sel.mode,
            boardingBus = boardingBus,
            boardingStops = boardingStops,
            destination = destination,
            lead = sel.lead,
            preview = if (boardingBus != null && destination != null) wakePreview(boardingBus, destination.id, sel.lead) else null,
            feed = ex.feed,
            watch = ex.watch?.takeIf { it.watch.routeId == routeId },
            favorite = ex.favorite,
            userLocation = ex.location,
            nearestHalte = ex.location?.let { loc -> halteRows.minByOrNull { loc.distanceTo(it.halte.lat, it.halte.lng) }?.halte },
            halteNames = net?.halte?.mapValues { it.value.name }.orEmpty(),
        )
    }

    init {
        viewModelScope.launch { network.value = staticSource.network() }
        viewModelScope.launch { prefs.markOpened(routeId) }
        locate()
    }

    fun onDirectionSelected(index: Int) = selection.update { it.copy(directionIndex = index) }

    fun onPickBus() = selection.update { it.copy(mode = SheetMode.PickBus) }

    /** Bus dipilih dari daftar atau diketuk di peta: langsung lanjut ke memilih halte turun. */
    fun onBusChosen(busNo: String?) {
        val bus = busNo?.let { buses.value[it] } ?: return
        val net = network.value ?: return
        val index = state.value.directions.indexOfFirst { bus.tripId in net.tripIdsToward(it) }
        selection.update { current ->
            current.copy(
                mode = SheetMode.Boarding(bus.busNo),
                destinationId = null,
                directionIndex = index.takeIf { it >= 0 } ?: current.directionIndex,
            )
        }
    }

    fun onDestinationSelected(halte: Halte) = selection.update { it.copy(destinationId = halte.id) }

    fun onLeadSelected(lead: LiveLead) = selection.update { it.copy(lead = lead) }

    /** Mundur satu langkah di sheet; false kalau sudah di mode jelajah (biar layarnya yang ditutup). */
    fun onSheetBack(): Boolean {
        val mode = selection.value.mode
        if (mode == SheetMode.Browse) return false
        selection.update {
            it.copy(mode = if (mode is SheetMode.Boarding) SheetMode.PickBus else SheetMode.Browse, destinationId = null)
        }
        return true
    }

    fun toggleFavorite() = viewModelScope.launch { prefs.toggleFavorite(routeId) }

    /** Menunggu di halte: ingatkan saat bus searah mana pun hampir tiba. */
    fun startArrival(halte: Halte, lead: LiveLead) = viewModelScope.launch {
        liveAlarms.start(
            LiveWatch.Arrival(
                routeId = routeId, halteId = halte.id, halteName = halte.name,
                lead = lead, startedAt = System.currentTimeMillis(),
                tripIds = state.value.directionTripIds,
            )
        )
    }

    /**
     * Di dalam bus terpilih: bangunkan sebelum sampai halte tujuan. [onStarted] dipanggil setelah
     * watch tersimpan — layar perjalanan aktif yang dibuka lebih awal akan menutup diri karena
     * belum melihat alarm apa pun.
     */
    fun startOnboard(onStarted: () -> Unit) = viewModelScope.launch {
        val ui = state.value
        val bus = ui.boardingBus ?: return@launch
        val destination = ui.destination ?: return@launch
        liveAlarms.start(
            LiveWatch.Onboard(
                busNo = bus.busNo, tripId = bus.tripId, routeId = routeId,
                halteId = destination.id, halteName = destination.name,
                lead = ui.lead, startedAt = System.currentTimeMillis(),
                initialStops = approachOf(bus, destination.id)?.stopsAway,
            )
        )
        selection.update { it.copy(mode = SheetMode.Browse, destinationId = null) }
        onStarted()
    }

    fun stopWatch() = liveAlarms.stop()

    /**
     * Untuk mengurutkan bus terdekat (bus yang sedang dinaiki hampir pasti yang paling dekat) dan
     * tombol lokasiku. Tanpa izin lokasi semuanya tetap jalan, hanya tidak diurutkan per jarak.
     */
    @SuppressLint("MissingPermission")
    fun locate() {
        if (!TripPermissions.hasForegroundLocation(context)) return
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) userLocation.value = loc }
    }
}

private fun Location.distanceTo(lat: Double, lng: Double): Float {
    val result = FloatArray(1)
    Location.distanceBetween(latitude, longitude, lat, lng, result)
    return result[0]
}
