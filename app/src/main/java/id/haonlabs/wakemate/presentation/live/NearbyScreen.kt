package id.haonlabs.wakemate.presentation.live

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.live.NearbyMode
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.presentation.TripPermissions
import id.haonlabs.wakemate.presentation.components.ChipChoices
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.InfoRow
import id.haonlabs.wakemate.presentation.components.PillChip
import id.haonlabs.wakemate.presentation.components.PosterButton
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.posterBorder
import id.haonlabs.wakemate.presentation.components.posterShadow
import id.haonlabs.wakemate.ui.theme.Wake
import id.haonlabs.wakemate.ui.theme.enterIn
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

private val PEEK = 392.dp
private val USER_BLUE = Color(0xFF7C93FF)

/**
 * Tab "Sekitar": halte/stasiun terdekat dari lokasi user dan rute yang melewatinya. Belum ada izin
 * lokasi -> layar izin; tidak ada titik dalam radius -> saran memperbesar radius.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onOpenRoute: (routeId: String) -> Unit,
    onWakeAt: (stationId: String, lineId: String) -> Unit,
    onBrowseRoutes: () -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }
    // Lokasi diperbarui selama layar terlihat saja: begitu tab ditinggalkan, pembaruan berhenti.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { viewModel.refresh(); delay(30_000) }
        }
    }

    if (!state.permissionGranted) {
        PermissionPrompt(
            onAllow = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
            onBrowse = onBrowseRoutes,
        )
        return
    }

    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    var recenter by remember { mutableLongStateOf(0L) }
    var showRadius by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); nowMs = System.currentTimeMillis() } }
    val border = Wake.colors.border

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight - 96.dp
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = PEEK,
            sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetTonalElevation = 0.dp,
            sheetDragHandle = { BottomSheetDefaults.DragHandle(color = border) },
            sheetContent = {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = sheetMax).topSideBorder(32.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "moda") {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillChip("Semua", state.mode == null, onClick = { viewModel.onModeSelected(null) })
                            NearbyMode.entries.forEach { m -> PillChip(m.label, state.mode == m, onClick = { viewModel.onModeSelected(m) }) }
                        }
                    }
                    when {
                        state.loading -> item(key = "memuat") {
                            InfoRow(R.drawable.ic_my_location, title = "Mencari lokasimu…", subtitle = "Pastikan GPS menyala.", iconTint = MaterialTheme.colorScheme.outline)
                        }
                        state.stops.isEmpty() -> item(key = "kosong") {
                            EmptyNearby(state.radiusMeters, state.mode, viewModel::onRadiusSelected, onBrowseRoutes)
                        }
                        else -> {
                            item(key = "judul") {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("DEKAT KAMU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${state.stops.size} titik · ≤ ${radiusLabel(state.radiusMeters)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.clickable { showRadius = true },
                                    )
                                }
                            }
                            items(state.stops, key = { it.stop.halte.id }) { item ->
                                StopCard(item, onClick = { viewModel.onStopSelected(item.stop) }, modifier = Modifier.enterIn(0))
                            }
                        }
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                NearbyMap(
                    userLat = state.location?.latitude,
                    userLng = state.location?.longitude,
                    radiusMeters = state.radiusMeters,
                    stops = state.stops.map { it.stop },
                    selectedId = state.selected?.stop?.halte?.id,
                    borderColor = border.toArgb(),
                    onStopClick = { id -> state.stops.firstOrNull { it.stop.halte.id == id }?.let { viewModel.onStopSelected(it.stop) } },
                    bottomPadding = PEEK,
                    recenterToken = recenter,
                    modifier = Modifier.fillMaxSize(),
                )
                // Gradasi lembut di atas peta supaya status bar dan kapsul judul terbaca.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 140.dp).background(
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.92f), Color.Transparent))
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NearbyHeader(updatedAgo(state.updatedAtMs, nowMs), Modifier.weight(1f))
                    CircleIconButton(R.drawable.ic_my_location, "Lokasiku", onClick = { viewModel.refresh(); recenter = System.currentTimeMillis() })
                }
            }
        }
    }

    state.selected?.let { item ->
        StopDetailSheet(
            item = item,
            departures = state.departures,
            alarmStation = state.alarmStation,
            alarmResolved = state.alarmResolved,
            onDismiss = { viewModel.onStopSelected(null) },
            onOpenRoute = { id -> viewModel.onStopSelected(null); onOpenRoute(id) },
            onWake = { station -> viewModel.onStopSelected(null); onWakeAt(station.id, station.lineId) },
        )
    }
    if (showRadius) {
        RadiusSheet(state.radiusMeters, onSelect = { viewModel.onRadiusSelected(it); showRadius = false }, onDismiss = { showRadius = false })
    }
}

@Composable
private fun NearbyHeader(updated: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = posterBorder(),
        modifier = modifier.heightIn(min = 48.dp).enterIn(0, fromY = (-40).dp).posterShadow(CircleShape, 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(24.dp).background(USER_BLUE, CircleShape).border(2.5.dp, Wake.colors.border, CircleShape))
            Column {
                Text("Sekitar Saya", style = MaterialTheme.typography.titleSmall)
                Text("Lokasimu · $updated", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModeBlock(mode: NearbyMode, size: androidx.compose.ui.unit.Dp = 46.dp) {
    val shape = RoundedCornerShape(size / 3)
    Box(
        Modifier.size(size).background(Color(mode.argb()), shape).border(2.5.dp, Wake.colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (mode == NearbyMode.BUS) "TJ" else mode.label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
            color = Wake.colors.onColor,
        )
    }
}

@Composable
private fun StopCard(item: NearbyStopUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    val stop = item.stop
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = posterBorder(),
        modifier = modifier.fillMaxWidth().posterShadow(shape, 4.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeBlock(stop.mode)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(stop.halte.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMeters(stop.distanceMeters), style = MaterialTheme.typography.titleMedium)
                        Text("± ${stop.walkMinutes} mnt jalan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    stop.routes.take(MAX_BADGES).forEach { RouteBadgeSmall(it) }
                    (stop.routes.size - MAX_BADGES).takeIf { it > 0 }?.let { more ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, border = posterBorder(2.dp)) {
                            Text("+$more", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(
                    item.next?.let { "Berikutnya · $it" } ?: "Jadwal belum tersedia",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val MAX_BADGES = 4

/** SK4: tidak ada halte/stasiun dalam radius. */
@Composable
private fun EmptyNearby(radius: Int, mode: NearbyMode?, onRadius: (Int) -> Unit, onBrowse: () -> Unit) {
    val bigger = RADIUS_OPTIONS_METERS.firstOrNull { it > radius }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val what = when (mode) {
            null -> "halte atau stasiun"
            NearbyMode.BUS -> "halte bus"
            else -> "stasiun ${mode.label}"
        }
        Text("Tidak ada $what\ndalam ${radiusLabel(radius)}", style = MaterialTheme.typography.headlineSmall)
        Text("Coba perbesar radius pencarian, atau cari rute secara manual.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel("RADIUS")
        ChipChoices(RADIUS_OPTIONS_METERS, radius, ::radiusLabel, onRadius)
        bigger?.let { PosterButton("Perbesar ke ${radiusLabel(it)}", onClick = { onRadius(it) }) }
        PosterButton("Cari rute manual", onClick = onBrowse, container = MaterialTheme.colorScheme.surfaceContainer, content = MaterialTheme.colorScheme.onSurface)
    }
}

/** SK3: belum ada izin lokasi. */
@Composable
private fun PermissionPrompt(onAllow: () -> Unit, onBrowse: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            listOf(260.dp to 0.2f, 190.dp to 0.35f, 120.dp to 0.5f).forEach { (d, a) ->
                Box(Modifier.size(d).alpha(a).border(2.dp, Wake.colors.border, CircleShape))
            }
            Box(Modifier.size(92.dp).background(USER_BLUE, CircleShape).border(3.dp, Wake.colors.border, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape).border(3.dp, Wake.colors.border, CircleShape))
            }
        }
        Spacer(Modifier.size(24.dp))
        Text("Izinkan lokasi", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.size(12.dp))
        Text(
            "WakeMate memakai lokasimu untuk menampilkan halte dan stasiun terdekat, serta rute apa saja yang lewat. Lokasi hanya diproses di HP, tidak dikirim ke mana pun.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        PosterButton("Izinkan lokasi", onClick = onAllow)
        Spacer(Modifier.size(14.dp))
        PosterButton("Cari rute manual", onClick = onBrowse, container = MaterialTheme.colorScheme.surfaceContainer, content = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.size(20.dp))
    }
}

/** SK2: rute yang melewati satu halte/stasiun, kedatangan berikutnya, dan tombol Bangunin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopDetailSheet(
    item: NearbyStopUi,
    departures: List<DepartureUi>,
    alarmStation: Station?,
    alarmResolved: Boolean,
    onDismiss: () -> Unit,
    onOpenRoute: (String) -> Unit,
    onWake: (Station) -> Unit,
) {
    val stop = item.stop
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Wake.colors.border) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ModeBlock(stop.mode, size = 50.dp)
                Column {
                    Text(stop.halte.name, style = MaterialTheme.typography.headlineSmall)
                    Text("${formatMeters(stop.distanceMeters)} dari kamu · ± ${stop.walkMinutes} mnt jalan kaki", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel("RUTE YANG MELEWATI · ${stop.routes.size}")
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                departures.forEach { dep -> DepartureRow(dep, onClick = { onOpenRoute(dep.route.id) }) }
            }
            Text("Perkiraan menurut jadwal, bukan posisi kendaraan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                alarmStation != null -> PosterButton(
                    label = "Bangunkan saya di sini",
                    onClick = { onWake(alarmStation) },
                    container = MaterialTheme.colorScheme.secondary,
                    content = MaterialTheme.colorScheme.onSecondary,
                )
                alarmResolved -> InfoRow(
                    R.drawable.ic_bell,
                    title = "Bangunin belum tersedia untuk ${if (stop.mode == NearbyMode.BUS) "halte" else "stasiun"} ini",
                    subtitle = "Bangunin mencakup KRL, MRT, LRT, dan koridor BRT.",
                    iconTint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DepartureRow(dep: DepartureUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = posterBorder(),
        modifier = Modifier.fillMaxWidth().posterShadow(shape, 3.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteBadgeSmall(dep.route)
            Column(Modifier.weight(1f)) {
                Text("Ke ${dep.headsign}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dep.route.longName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (dep.minutes != null) {
                    Text("±${dep.minutes} mnt", style = MaterialTheme.typography.titleSmall)
                    dep.at?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text("Belum ada jadwal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RadiusSheet(radius: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Wake.colors.border) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Radius pencarian", style = MaterialTheme.typography.headlineSmall)
            ChipChoices(RADIUS_OPTIONS_METERS, radius, ::radiusLabel, onSelect)
        }
    }
}

internal fun radiusLabel(meters: Int): String = if (meters < 1_000) "$meters m" else "${meters / 1_000} km"

/** "90 m" (dibulatkan 10 m) atau "1,2 km". */
internal fun formatMeters(meters: Double): String =
    if (meters < 1_000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} m"
    else String.format(Locale.US, "%.1f km", meters / 1_000).replace('.', ',')

internal fun updatedAgo(updatedAtMs: Long, nowMs: Long): String {
    if (updatedAtMs == 0L) return "mencari…"
    val seconds = ((nowMs - updatedAtMs) / 1_000).coerceAtLeast(0)
    return if (seconds < 60) "diperbarui $seconds dtk lalu" else "diperbarui ${seconds / 60} mnt lalu"
}
