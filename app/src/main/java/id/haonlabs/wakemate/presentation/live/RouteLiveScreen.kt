package id.haonlabs.wakemate.presentation.live

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.data.live.FeedStatus
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.LiveBus
import id.haonlabs.wakemate.domain.live.summary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLiveScreen(
    onBack: () -> Unit,
    viewModel: RouteLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routeColor = state.route?.let { parseRouteColor(it.color, MaterialTheme.colorScheme.primary) }
        ?: MaterialTheme.colorScheme.primary

    var picking by remember { mutableStateOf<Halte?>(null) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Tanpa izin notifikasi alarm tetap berbunyi, tapi tombol "Matikan" di notifikasi tidak
    // terlihat — jadi diminta, namun penolakan tidak menghalangi.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingStart?.invoke()
        pendingStart = null
    }
    fun withNotificationPermission(start: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingStart = start
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Kembali")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.route?.let { RouteBadge(it) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                state.route?.longName.orEmpty(),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            FeedLabel(state.feed, state.buses.size)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LiveMap(
                shape = state.direction?.shape.orEmpty(),
                halte = state.halte.map { it.halte },
                buses = state.buses.map { it.bus },
                activeTripIds = state.directionTripIds,
                selectedBusNo = state.selectedBus?.busNo,
                routeColor = routeColor.toArgb(),
                onBusClick = viewModel::onBusSelected,
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.watch?.let { watch ->
                    item { LiveWatchCard(watch, onStop = viewModel::stopWatch) }
                }

                if (state.directions.size > 1 && !state.boarding) {
                    item { DirectionChips(state, onSelect = viewModel::onDirectionSelected) }
                }

                val selected = state.selectedBus
                when {
                    selected != null -> item {
                        SelectedBusCard(
                            bus = selected,
                            boarding = state.boarding,
                            onBoard = { viewModel.onBoardingChanged(true) },
                            onCancelBoarding = { viewModel.onBoardingChanged(false) },
                            onClose = { viewModel.onBusSelected(null) },
                        )
                    }
                    state.buses.isNotEmpty() -> item {
                        BusStrip(state.buses, onSelect = viewModel::onBusSelected)
                    }
                }

                item {
                    Text(
                        if (state.boarding) "PILIH HALTE TURUN" else "HALTE · KETUK UNTUK PENGINGAT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                itemsIndexed(state.halte, key = { index, row -> "${row.halte.id}-$index" }) { index, row ->
                    HalteRowItem(
                        row = row,
                        accent = routeColor,
                        isFirst = index == 0,
                        isLast = index == state.halte.lastIndex,
                        showBus = !state.boarding,
                        onClick = { picking = row.halte },
                    )
                }
            }
        }
    }

    picking?.let { halte ->
        val bus = state.selectedBus
        val onboard = state.boarding && bus != null
        LeadPickerDialog(
            title = if (onboard) "Bangunkan sebelum ${halte.name}" else "Ingatkan bus di ${halte.name}",
            description = if (onboard) "Alarm bertahap berbunyi saat bus ${bus?.busNo} mendekati halte ini."
            else "Notifikasi saat bus ${state.route?.shortName} mana pun hampir tiba di halte ini.",
            onboard = onboard,
            onConfirm = { lead ->
                picking = null
                withNotificationPermission {
                    if (onboard) viewModel.startOnboard(halte, lead) else viewModel.startArrival(halte, lead)
                }
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun DirectionChips(state: RouteLiveUi, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // Arah default dipilih otomatis dan bisa jatuh di luar layar; tanpa ini user tidak tahu
    // arah mana yang sedang ditampilkan.
    LaunchedEffect(state.directionIndex) { listState.animateScrollToItem(state.directionIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.directions, key = { _, trip -> trip.id }) { index, trip ->
            FilterChip(
                selected = index == state.directionIndex,
                onClick = { onSelect(index) },
                label = { Text("Ke ${trip.headsign}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(14.dp),
            )
        }
    }
}

@Composable
private fun FeedLabel(status: FeedStatus, busCount: Int) {
    val (text, color) = when (status) {
        FeedStatus.LIVE -> "Live · $busCount bus" to MaterialTheme.colorScheme.primary
        FeedStatus.OFFLINE -> "Data live tidak tersedia" to MaterialTheme.colorScheme.error
        FeedStatus.CONNECTING, FeedStatus.IDLE -> "Menghubungkan..." to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun BusStrip(buses: List<BusItem>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "BUS AKTIF · KETUK BUS YANG KAMU NAIKI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            buses.forEach { item ->
                Surface(
                    onClick = { onSelect(item.bus.busNo) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(item.bus.busNo, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(
                                "ke ${item.bus.headsign}",
                                item.distanceMeters?.let(::formatDistance),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedBusCard(
    bus: LiveBus,
    boarding: Boolean,
    onBoard: () -> Unit,
    onCancelBoarding: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(bus.busNo, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(bus.busType, "ke ${bus.headsign}", updatedAgo(bus.updatedAt))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Tutup")
                }
            }
            if (boarding) {
                Text(
                    "Ketuk halte tempat kamu turun di bawah.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                OutlinedButton(onClick = onCancelBoarding, modifier = Modifier.fillMaxWidth()) {
                    Text("Batal pilih halte")
                }
            } else {
                Button(onClick = onBoard, modifier = Modifier.fillMaxWidth()) {
                    Text("Saya di bus ini — bangunkan saya")
                }
            }
        }
    }
}

/** Satu halte di garis waktu rute, dengan bus berikutnya yang menuju ke sana. */
@Composable
private fun HalteRowItem(
    row: HalteRow,
    accent: Color,
    isFirst: Boolean,
    isLast: Boolean,
    showBus: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.height(54.dp).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(3.dp).weight(1f).background(if (isFirst) Color.Transparent else accent))
                    Box(Modifier.width(3.dp).weight(1f).background(if (isLast) Color.Transparent else accent))
                }
                Box(
                    Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.background, CircleShape)
                        .padding(2.dp)
                        .background(accent, CircleShape)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                row.halte.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            row.next?.let { next ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        next.etaMin?.let { "±$it mnt" } ?: next.summary(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (showBus) {
                        Text(
                            next.bus.busNo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Float): String =
    if (meters < 1_000) "${meters.toInt()} m" else String.format(java.util.Locale.US, "%.1f km", meters / 1_000)

private fun updatedAgo(updatedAt: Long): String {
    val seconds = ((System.currentTimeMillis() - updatedAt) / 1_000).coerceAtLeast(0)
    return if (seconds < 60) "diperbarui $seconds dtk lalu" else "diperbarui ${seconds / 60} mnt lalu"
}
