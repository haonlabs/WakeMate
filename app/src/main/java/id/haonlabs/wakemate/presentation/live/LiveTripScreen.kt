package id.haonlabs.wakemate.presentation.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.data.live.LiveWatchRepository
import id.haonlabs.wakemate.data.live.LiveWatchState
import id.haonlabs.wakemate.data.live.TjLiveFeed
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.LiveBus
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.label
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.CountdownCard
import id.haonlabs.wakemate.presentation.components.StatusPill
import id.haonlabs.wakemate.service.LiveAlarms
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveTripUi(val state: LiveWatchState? = null, val network: TjNetwork? = null, val buses: Map<String, LiveBus> = emptyMap())

@HiltViewModel
class LiveTripViewModel @Inject constructor(
    staticSource: TjStaticSource,
    feed: TjLiveFeed,
    watches: LiveWatchRepository,
    private val liveAlarms: LiveAlarms,
) : ViewModel() {
    private val network = MutableStateFlow<TjNetwork?>(null)

    // Rute yang sama sudah di-subscribe service; feed memakai satu koneksi bersama.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val buses = watches.state.map { it?.watch?.routeId }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyMap()) else feed.route(id) }

    val ui: StateFlow<LiveTripUi> = combine(watches.state, network, buses, ::LiveTripUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTripUi(state = watches.state.value))

    init { viewModelScope.launch { network.value = staticSource.network() } }

    fun cancel() = liveAlarms.stop()
}

/** LT6: perjalanan dengan alarm live aktif — angka besar yang terbaca sekilas oleh orang yang baru bangun. */
@Composable
fun LiveTripScreen(
    onBack: () -> Unit,
    onChangeStop: (routeId: String, busNo: String) -> Unit,
    viewModel: LiveTripViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Alarm bisa berakhir dari mana saja — notifikasi, timeout, atau data live yang hilang.
    LaunchedEffect(ui.state == null) { if (ui.state == null) onBack() }
    val active = ui.state ?: return
    val watch = active.watch
    val approach = active.approach
    val route = ui.network?.routeById?.get(watch.routeId)
    val routeColor = route?.let { parseRouteColor(it.color, MaterialTheme.colorScheme.primary) } ?: MaterialTheme.colorScheme.primary
    val bus = when (watch) {
        // Feed rute di layar ini baru mulai; posisi terakhir dari service dipakai sampai bus
        // itu melapor lagi (tiap bus melapor kira-kira sekali setengah menit).
        is LiveWatch.Onboard -> ui.buses[watch.busNo] ?: approach?.bus?.takeIf { it.busNo == watch.busNo }
        is LiveWatch.Arrival -> approach?.bus
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(R.drawable.ic_arrow_back, "Kembali", onBack)
                StatusPill(if (watch is LiveWatch.Onboard) "ALARM AKTIF" else "PENGINGAT AKTIF")
            }

            when (watch) {
                is LiveWatch.Onboard -> {
                    val remaining = approach?.stopsAway
                    val total = watch.initialStops?.coerceAtLeast(remaining ?: 0)
                    val wakeAt = (watch.lead as? LiveLead.Stops)?.let { lead -> total?.let { it - lead.count }?.takeIf { it >= 0 } }
                    CountdownCard(
                        eyebrow = "BANGUNKAN SEBELUM",
                        title = watch.halteName,
                        count = remaining?.toString() ?: "–",
                        unit = if (remaining == null) "menunggu data live" else "halte lagi" + (approach?.etaMin?.let { " · ±$it mnt" } ?: ""),
                        segments = total ?: 0,
                        done = if (total != null && remaining != null) total - remaining else 0,
                        wakeAt = wakeAt,
                        legend = "Bunyi ${watch.lead.label()}",
                    )
                }
                is LiveWatch.Arrival -> CountdownCard(
                    eyebrow = "MENUNGGU BUS ${route?.shortName.orEmpty()} DI",
                    title = watch.halteName,
                    count = approach?.etaMin?.toString() ?: approach?.stopsAway?.toString() ?: "–",
                    unit = when {
                        approach == null -> "belum ada bus menuju ke sini"
                        approach.etaMin != null -> "mnt lagi · ${approach.bus.busNo}"
                        else -> "halte lagi · ${approach.bus.busNo}"
                    },
                    legend = "Notifikasi ${watch.lead.label()}",
                )
            }

            val direction = bus?.let { b -> ui.network?.tripById?.get(b.tripId) }
            Box(Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp))) {
                LiveMap(
                    shape = direction?.shape.orEmpty(),
                    halte = direction?.halteIds?.mapNotNull { ui.network?.halte?.get(it) }.orEmpty(),
                    buses = listOfNotNull(bus),
                    activeTripIds = emptySet(),
                    selectedBusNo = bus?.busNo,
                    routeColor = routeColor.toArgb(),
                    onBusClick = {},
                    target = bus?.let { CameraTarget(it.lat, it.lng, zoom = 14.5) },
                    interactive = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    route?.let { RouteBadge(it) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(bus?.busNo ?: (watch as? LiveWatch.Onboard)?.busNo, bus?.busType?.takeIf { it.isNotBlank() }).joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            bus?.let { "Data live · ${updatedAgo(it.updatedAt)}" } ?: "Menunggu data live...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Text(
                "HP boleh dikunci dan ditaruh di saku. Alarm memakai posisi bus dari Transjakarta, bukan GPS ponselmu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (watch is LiveWatch.Onboard) {
                    FilledTonalButton(
                        onClick = { onChangeStop(watch.routeId, watch.busNo) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Ubah halte", style = MaterialTheme.typography.titleSmall) }
                }
                OutlinedButton(
                    onClick = viewModel::cancel,
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.weight(1f),
                ) { Text(if (watch is LiveWatch.Onboard) "Batalkan alarm" else "Batalkan pengingat", style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}
