package id.haonlabs.wakemate.presentation.live

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.NearbyMode
import id.haonlabs.wakemate.domain.live.NearbyStop
import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.domain.live.alarmLineHint
import id.haonlabs.wakemate.domain.live.alarmMode
import id.haonlabs.wakemate.domain.live.departuresAt
import id.haonlabs.wakemate.domain.live.nearbyStops
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.usecase.FindAlarmStationUseCase
import id.haonlabs.wakemate.presentation.TripPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Satu halte/stasiun di daftar: [next] = ringkasan kedatangan berikutnya ("1 ke Kota ±4 mnt"), null kalau tak ada jadwal. */
data class NearbyStopUi(val stop: NearbyStop, val next: String?)

/** Baris "rute yang melewati" di sheet detail; [at] = jam tiba ("15:12"). */
data class DepartureUi(val route: TjRoute, val headsign: String, val minutes: Int?, val at: String?)

data class NearbyUi(
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val location: Location? = null,
    val updatedAtMs: Long = 0,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val mode: NearbyMode? = null,
    val stops: List<NearbyStopUi> = emptyList(),
    val selected: NearbyStopUi? = null,
    val departures: List<DepartureUi> = emptyList(),
    /** Stasiun yang cocok di data alarm (Bangunin); null + [alarmResolved] = tidak ada yang cocok. */
    val alarmStation: Station? = null,
    val alarmResolved: Boolean = false,
)

const val DEFAULT_RADIUS_METERS = 1_000
val RADIUS_OPTIONS_METERS = listOf(500, 1_000, 2_000, 5_000)

/**
 * "Sekitar Saya": halte/stasiun terdekat dari lokasi user dan rute yang lewat. Semua dari data statis
 * di HP; lokasi hanya dibaca saat layar terbuka (izin foreground) dan tidak dikirim ke mana pun.
 */
@HiltViewModel
class NearbyViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    staticSource: TjStaticSource,
    private val findAlarmStation: FindAlarmStationUseCase,
) : ViewModel() {

    private val network = MutableStateFlow<TjNetwork?>(null)
    private val permission = MutableStateFlow(TripPermissions.hasForegroundLocation(context))
    private val location = MutableStateFlow<Pair<Location, Long>?>(null)
    private val radius = MutableStateFlow(DEFAULT_RADIUS_METERS)
    private val mode = MutableStateFlow<NearbyMode?>(null)
    private val selectedId = MutableStateFlow<String?>(null)
    private val alarm = MutableStateFlow<Pair<Station?, Boolean>>(null to false)
    /** Berdetak per 10 dtk supaya ETA ikut dihitung ulang tanpa data baru. */
    private val clock = MutableStateFlow(0L)

    val state: StateFlow<NearbyUi> = combine(
        combine(network, permission, location) { net, perm, loc -> Triple(net, perm, loc) },
        combine(radius, mode, selectedId) { r, m, s -> Triple(r, m, s) },
        alarm, clock,
    ) { (net, perm, loc), (r, m, sel), alarmState, _ -> build(net, perm, loc, r, m, sel, alarmState) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NearbyUi(permissionGranted = permission.value))

    private fun build(
        net: TjNetwork?,
        granted: Boolean,
        loc: Pair<Location, Long>?,
        radiusMeters: Int,
        nearbyMode: NearbyMode?,
        selId: String?,
        alarmState: Pair<Station?, Boolean>,
    ): NearbyUi {
        if (net == null || loc == null) {
            return NearbyUi(loading = granted, permissionGranted = granted, radiusMeters = radiusMeters, mode = nearbyMode)
        }
        val now = ZonedDateTime.now(JAKARTA)
        val stops = nearbyStops(net, loc.first.latitude, loc.first.longitude, radiusMeters.toDouble(), nearbyMode).map { stop ->
            val next = departuresAt(net, stop.halte.id, now).firstOrNull { it.minutes != null }
            NearbyStopUi(stop, next?.let { "${it.route.shortName} ke ${it.headsign} ±${it.minutes} mnt" })
        }
        val selected = selId?.let { id -> stops.firstOrNull { it.stop.halte.id == id } }
        return NearbyUi(
            loading = false,
            permissionGranted = granted,
            location = loc.first,
            updatedAtMs = loc.second,
            radiusMeters = radiusMeters,
            mode = nearbyMode,
            stops = stops,
            selected = selected,
            departures = selected?.let { s ->
                departuresAt(net, s.stop.halte.id, now).map { DepartureUi(it.route, it.headsign, it.minutes, it.minutes?.let { m -> now.plusMinutes(m.toLong()).format(CLOCK) }) }
            }.orEmpty(),
            alarmStation = alarmState.first,
            alarmResolved = alarmState.second,
        )
    }

    init {
        viewModelScope.launch { network.value = staticSource.network() }
        viewModelScope.launch { while (isActive) { delay(10_000); clock.value = System.currentTimeMillis() } }
    }

    fun onPermissionResult() {
        permission.value = TripPermissions.hasForegroundLocation(context)
        refresh()
    }

    /** Ambil lokasi terbaru; dipanggil saat layar tampil, berkala, dan dari tombol lokasi. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val granted = TripPermissions.hasForegroundLocation(context)
        permission.value = granted
        if (!granted) return
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) location.value = loc to System.currentTimeMillis() }
    }

    fun onRadiusSelected(meters: Int) { radius.value = meters }

    fun onModeSelected(value: NearbyMode?) { mode.value = value }

    /** Buka (atau tutup dengan null) sheet detail sebuah halte; sekalian mencari stasiun Bangunin yang cocok. */
    fun onStopSelected(stop: NearbyStop?) {
        selectedId.value = stop?.halte?.id
        alarm.value = null to false
        val route = stop?.routes?.firstOrNull { it.alarmMode() != null }
        val mode = route?.alarmMode()
        if (stop == null || route == null || mode == null) {
            alarm.value = null to (stop != null)
            return
        }
        viewModelScope.launch {
            alarm.value = findAlarmStation(mode, stop.halte.lat, stop.halte.lng, route.alarmLineHint()) to true
        }
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val JAKARTA = ZoneId.of("Asia/Jakarta")
