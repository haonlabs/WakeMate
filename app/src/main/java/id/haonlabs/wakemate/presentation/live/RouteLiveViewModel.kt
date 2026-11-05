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
import id.haonlabs.wakemate.data.live.LiveRoutePrefs
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.domain.live.TjTrip
import id.haonlabs.wakemate.domain.live.scheduleEtas
import id.haonlabs.wakemate.presentation.RouteLive
import id.haonlabs.wakemate.presentation.TripPermissions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** [scheduleEta] = menit dari sekarang menurut jadwal; null kalau rute ini tidak punya data jadwal. */
data class HalteRow(
    val halte: Halte,
    val scheduleEta: Int? = null,
)

data class RouteLiveUi(
    val route: TjRoute? = null,
    val directions: List<TjTrip> = emptyList(),
    val directionIndex: Int = 0,
    val halte: List<HalteRow> = emptyList(),
    /** Alasan ETA jadwal tidak tampil (KRL: jadwal kadaluarsa / hari Sabtu), null kalau tidak ada masalah. */
    val scheduleNote: String? = null,
    val favorite: Boolean = false,
    val userLocation: Location? = null,
) {
    val direction: TjTrip? get() = directions.getOrNull(directionIndex)
}

/**
 * Peta satu rute: arah, daftar halte/stasiun, dan perkiraan tiba menurut jadwal. Semuanya data
 * statis (GTFS / OpenStreetMap) — tidak ada posisi kendaraan.
 */
@HiltViewModel
class RouteLiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    staticSource: TjStaticSource,
    private val prefs: LiveRoutePrefs,
) : ViewModel() {

    private val routeId = savedStateHandle.toRoute<RouteLive>().routeId
    private val network = MutableStateFlow<TjNetwork?>(null)
    /** null sampai user memilih arah sendiri. */
    private val chosenDirection = MutableStateFlow<Int?>(null)
    private val userLocation = MutableStateFlow<Location?>(null)

    /** Berdetak per 30 dtk supaya perkiraan tiba ikut dihitung ulang tanpa data baru. */
    private val clock = MutableStateFlow(0L)

    val state: StateFlow<RouteLiveUi> = combine(network, chosenDirection, userLocation, prefs.favorites, clock) { net, chosen, location, favs, _ ->
        build(net, chosen, location, routeId in favs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteLiveUi())

    private fun build(net: TjNetwork?, chosen: Int?, location: Location?, favorite: Boolean): RouteLiveUi {
        val directions = net?.directions(routeId).orEmpty()
        val now = ZonedDateTime.now(JAKARTA)
        // Arah pertama yang punya trip beroperasi hari ini: varian pertama di GTFS sering trip khusus
        // yang sedang tidak jalan, dan daftar halte tanpa satu pun jadwal tidak berguna.
        val directionIndex = chosen ?: net?.let { n ->
            directions.indexOfFirst { d -> n.tripIdsToward(d).any { n.tripById[it]?.runsOn(now.dayOfWeek.value) == true } }
                .takeIf { it >= 0 }
        } ?: 0
        val direction = directions.getOrNull(directionIndex)
        val route = net?.routeById?.get(routeId)
        val note = scheduleNote(route, net, now)
        val towardTrips = direction?.let { d -> net?.tripIdsToward(d)?.mapNotNull { net.tripById[it] } }.orEmpty()
        val expired = note != null && note.startsWith(EXPIRED_PREFIX)
        val rows = if (net == null || direction == null) emptyList() else direction.halteIds.mapNotNull { id ->
            net.halte[id]?.let { HalteRow(it, if (expired) null else scheduleEtas(towardTrips, id, now).firstOrNull()) }
        }
        return RouteLiveUi(
            route = route,
            directions = directions,
            directionIndex = directionIndex,
            halte = rows,
            scheduleNote = note,
            favorite = favorite,
            userLocation = location,
        )
    }

    init {
        viewModelScope.launch { network.value = staticSource.network() }
        viewModelScope.launch { prefs.markOpened(routeId) }
        viewModelScope.launch { while (isActive) { delay(30_000); clock.value = System.currentTimeMillis() } }
        locate()
    }

    fun onDirectionSelected(index: Int) { chosenDirection.value = index }

    fun toggleFavorite() = viewModelScope.launch { prefs.toggleFavorite(routeId) }

    /** Untuk tombol lokasiku. Tanpa izin lokasi semuanya tetap jalan. */
    @SuppressLint("MissingPermission")
    fun locate() {
        if (!TripPermissions.hasForegroundLocation(context)) return
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) userLocation.value = loc }
    }
}

/**
 * KRL memakai jadwal tetap yang punya masa berlaku dan tidak mencakup hari Sabtu (sumber datanya
 * hanya merekam hari kerja dan Minggu). Lebih baik bilang tidak tahu daripada menampilkan jam
 * yang salah.
 */
private fun scheduleNote(route: TjRoute?, net: TjNetwork?, now: ZonedDateTime): String? {
    if (route?.kind != "KRL") return null
    val until = net?.fixedScheduleValidUntil
    return when {
        until != null && now.toLocalDate().isAfter(until) ->
            "$EXPIRED_PREFIX ${until.format(DATE_ID)}. Perbarui aplikasi untuk jadwal terbaru."
        now.dayOfWeek == DayOfWeek.SATURDAY -> "Jadwal KRL hari Sabtu belum tersedia."
        else -> null
    }
}

private const val EXPIRED_PREFIX = "Jadwal KRL di aplikasi ini hanya berlaku sampai"
private val DATE_ID = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("id-ID"))

/** Semua waktu jadwal mengikuti zona operator (WIB), bukan zona device. */
private val JAKARTA = ZoneId.of("Asia/Jakarta")
