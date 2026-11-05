package id.haonlabs.wakemate.presentation.live

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.alarmMode
import id.haonlabs.wakemate.domain.live.isRail
import id.haonlabs.wakemate.presentation.TripPermissions
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.InfoRow
import id.haonlabs.wakemate.presentation.components.ChipChoices
import id.haonlabs.wakemate.presentation.components.PillChip
import id.haonlabs.wakemate.presentation.components.PosterButton
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.SegmentedControl
import id.haonlabs.wakemate.presentation.components.TimelineRow
import id.haonlabs.wakemate.presentation.components.TimelineStatus
import id.haonlabs.wakemate.presentation.components.posterBorder
import id.haonlabs.wakemate.presentation.components.posterShadow
import id.haonlabs.wakemate.ui.theme.Wake
import id.haonlabs.wakemate.ui.theme.enterIn

private val PEEK = 400.dp

/** Border poster di tepi atas dan samping sheet — tepi bawah menempel nav bar, tak perlu garis. */
@Composable
internal fun Modifier.topSideBorder(cornerRadius: Dp, color: Color = Wake.colors.border): Modifier = drawBehind {
    val width = 2.5.dp.toPx()
    val inset = width / 2f
    val r = cornerRadius.toPx()
    val path = Path().apply {
        moveTo(inset, size.height)
        lineTo(inset, r)
        quadraticTo(inset, 0f, inset + r, 0f)
        lineTo(size.width - inset - r, 0f)
        quadraticTo(size.width - inset, 0f, size.width - inset, r)
        lineTo(size.width - inset, size.height)
    }
    drawPath(path, color, style = Stroke(width))
}

/** Peta rute: jalur + halte/stasiun di peta, daftar halte dengan perkiraan tiba menurut jadwal di sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLiveScreen(
    onBack: () -> Unit,
    /** Buka Atur pengingat (alarm GPS) untuk stasiun ini. */
    onWakeAt: (stationId: String, lineId: String) -> Unit,
    viewModel: RouteLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val routeColor = state.route?.let { parseRouteColor(it.color, MaterialTheme.colorScheme.primary) }
        ?: MaterialTheme.colorScheme.primary
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    var cameraTarget by remember { mutableStateOf<CameraTarget?>(null) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.locate()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight - 96.dp
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = PEEK,
            sheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            sheetContainerColor = MaterialTheme.colorScheme.background,
            sheetTonalElevation = 0.dp,
            sheetDragHandle = { BottomSheetDefaults.DragHandle(color = Wake.colors.border) },
            sheetContent = {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = sheetMax).topSideBorder(32.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    routeItems(state = state, routeColor = routeColor, onDirection = viewModel::onDirectionSelected, onHalte = viewModel::onHalteSelected)
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                RouteMap(
                    shape = state.direction?.shape.orEmpty(),
                    halte = state.halte.map { it.halte },
                    routeColor = routeColor.toArgb(),
                    onHalteClick = { id -> state.halte.firstOrNull { it.halte.id == id }?.let { viewModel.onHalteSelected(it.halte) } },
                    trains = state.trains,
                    bottomPadding = PEEK,
                    target = cameraTarget,
                    modifier = Modifier.fillMaxSize(),
                )
                // Gradasi lembut di atas peta: teks status bar dan kapsul judul tetap terbaca di atas peta yang ramai.
                Box(
                    Modifier.fillMaxWidth().height(140.dp).background(
                        Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background.copy(alpha = 0.92f), Color.Transparent))
                    ),
                )
                TopOverlay(state, onBack = onBack, onFavorite = viewModel::toggleFavorite)
                if (state.route?.kind == "KRL" && state.scheduleNote == null) {
                    // Di kiri-bawah, sebaris dengan tombol lokasiku: tidak menutupi jalur dan judul di atas.
                    ScheduleLabel(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = PEEK + 26.dp))
                }
                CircleIconButton(
                    icon = R.drawable.ic_my_location,
                    contentDescription = "Lokasiku",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = {
                        if (!TripPermissions.hasForegroundLocation(context)) locationLauncher.launch(TripPermissions.foreground)
                        else {
                            viewModel.locate()
                            state.userLocation?.let { cameraTarget = CameraTarget(it.latitude, it.longitude, id = System.currentTimeMillis()) }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = PEEK + 16.dp),
                )
            }
        }
    }

    state.selected?.let { row ->
        HalteSheet(
            state = state,
            row = row,
            onDismiss = { viewModel.onHalteSelected(null) },
            onWake = { station ->
                viewModel.onHalteSelected(null)
                onWakeAt(station.id, station.lineId)
            },
        )
    }
}

/** Sheet satu halte/stasiun: perkiraan tiba menurut jadwal dan tombol "Bangunkan saya di sini". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HalteSheet(
    state: RouteLiveUi,
    row: HalteRow,
    onDismiss: () -> Unit,
    onWake: (id.haonlabs.wakemate.domain.model.Station) -> Unit,
) {
    val unit = if (state.route?.isRail() == true) "stasiun" else "halte"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Wake.colors.border) },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(row.halte.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(state.route?.longName, state.route?.kind).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.selectedNext.isNotEmpty()) {
                val vehicle = if (state.route?.isRail() == true) "KERETA" else "BUS"
                SectionLabel("$vehicle BERIKUTNYA · KE ${state.direction?.headsign.orEmpty().uppercase()}")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.selectedNext.forEachIndexed { i, d ->
                        DepartureCard(d, highlighted = i == 0, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - state.selectedNext.size) { Box(Modifier.weight(1f)) }
                }
            } else {
                InfoRow(
                    R.drawable.ic_alarm,
                    title = "Perkiraan tiba belum tersedia",
                    subtitle = state.scheduleNote,
                    iconTint = MaterialTheme.colorScheme.outline,
                )
            }
            sheetNote(state.route?.kind)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val station = state.alarmStation
            when {
                station != null -> PosterButton(
                    label = "Bangunkan saya di sini",
                    onClick = { onWake(station) },
                    container = MaterialTheme.colorScheme.secondary,
                    content = MaterialTheme.colorScheme.onSecondary,
                )
                state.alarmResolved -> InfoRow(
                    R.drawable.ic_bell,
                    title = "Bangunin belum tersedia untuk $unit ini",
                    subtitle = "Bangunin mencakup KRL, MRT, LRT, dan koridor BRT.",
                    iconTint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Label permanen di atas peta KRL: titik kereta berasal dari jadwal, bukan GPS. Jangan dihapus. */
@Composable
private fun ScheduleLabel(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondary,
        contentColor = Wake.colors.onColor,
        border = posterBorder(2.dp),
        modifier = modifier,
    ) {
        Text(
            "Posisi kereta menurut jadwal, bukan posisi nyata",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TopOverlay(state: RouteLiveUi, onBack: () -> Unit, onFavorite: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleIconButton(R.drawable.ic_arrow_back, "Kembali", onBack)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = posterBorder(),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).enterIn(0, fromY = (-40).dp).posterShadow(CircleShape, 3.dp),
        ) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.route?.let { RouteBadge(it) }
                Column(Modifier.weight(1f)) {
                    Text(state.route?.longName.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    state.route?.let {
                        val unit = if (it.isRail()) "stasiun" else "halte"
                        Text("${it.kind} · ${state.halte.size} $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        CircleIconButton(
            if (state.favorite) R.drawable.ic_star_filled else R.drawable.ic_star,
            if (state.favorite) "Hapus dari favorit" else "Jadikan favorit",
            onFavorite,
            tint = if (state.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.routeItems(
    state: RouteLiveUi,
    routeColor: Color,
    onDirection: (Int) -> Unit,
    onHalte: (Halte) -> Unit,
) {
    // Satu item atas yang selalu ada: kalau pemilih arah disisipkan setelah data dimuat, LazyColumn
    // mempertahankan posisi pada item lama dan daftar terdorong keluar layar.
    item(key = "atas") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.directions.size in 2..3 -> SegmentedControl(
                    options = state.directions.map { "Ke ${it.headsign}" },
                    selected = state.directionIndex,
                    onSelect = onDirection,
                )
                state.directions.size > 3 -> DirectionChips(state, onDirection)
            }
        }
    }
    val unit = if (state.route?.isRail() == true) "stasiun" else "halte"
    val canWake = state.route?.alarmMode() != null
    item(key = "halte-judul") {
        SectionLabel(
            if (canWake) "${unit.uppercase()} · KETUK UNTUK ALARM" else unit.uppercase(),
            trailing = "${state.halte.size} $unit",
        )
    }
    if (state.halte.any { it.scheduleEta != null }) {
        item(key = "keterangan-jadwal") {
            val vehicle = if (state.route?.isRail() == true) "Kereta" else "Bus"
            Text(
                "$vehicle berikutnya di tiap $unit, menurut jadwal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (state.halte.isNotEmpty() && state.halte.none { it.scheduleEta != null }) {
        item(key = "tanpa-jadwal") {
            InfoRow(
                R.drawable.ic_alarm,
                title = "Perkiraan tiba belum tersedia",
                subtitle = state.scheduleNote ?: "Rute dan $unit tetap bisa dilihat di peta dan daftar ini.",
                iconTint = MaterialTheme.colorScheme.outline,
            )
        }
    }
    itemsIndexed(state.halte, key = { i, row -> "h-${row.halte.id}-$i" }) { i, row ->
        TimelineRow(
            name = row.halte.name,
            status = TimelineStatus.DEFAULT,
            lineColor = routeColor,
            eta = row.scheduleEta?.let { "±$it mnt" },
            detail = row.scheduleAt,
            isFirst = i == 0,
            isLast = i == state.halte.lastIndex,
            onClick = { onHalte(row.halte) },
        )
    }
    state.route?.let { route ->
        item(key = "sumber") {
            Text(sourceNote(route.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Atribusi wajib: Open-KRL (CC-BY 4.0) untuk jadwal KRL, OpenStreetMap (ODbL) untuk jalur dan stasiun rel. */
private fun sourceNote(kind: String): String = when {
    kind == "KRL" -> "Jadwal: Open-KRL (CC-BY 4.0), disusun dari papan jadwal kci.id; tidak resmi dan bisa berubah tanpa pemberitahuan. Stasiun dan peta © kontributor OpenStreetMap."
    kind == "MRT" || kind.startsWith("LRT") -> "Jalur dan stasiun © kontributor OpenStreetMap. Jadwal belum tersedia di aplikasi ini."
    else -> "Rute, halte, dan jadwal: GTFS Transjakarta. Perkiraan menurut jadwal, bukan posisi bus."
}

/**
 * Pemilih arah untuk rute bertujuan banyak (KRL Cikarang: 10): dua chip pertama + "+N lainnya" yang
 * membuka daftar lengkap, bukan deretan chip melebar. Arah yang dipilih dari daftar menggantikan
 * chip kedua supaya selalu terlihat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionChips(state: RouteLiveUi, onSelect: (Int) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    val visible = if (state.directionIndex >= 2) listOf(0, state.directionIndex) else listOf(0, 1)
    val hidden = state.directions.size - visible.size
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { index ->
            PillChip("Ke ${state.directions[index].headsign}", index == state.directionIndex, onClick = { onSelect(index) })
        }
        PillChip("+$hidden lainnya", selected = false, onClick = { showAll = true })
    }
    if (showAll) {
        ModalBottomSheet(
            onDismissRequest = { showAll = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            tonalElevation = 0.dp,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Wake.colors.border) },
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Pilih arah", style = MaterialTheme.typography.headlineSmall)
                ChipChoices(
                    options = state.directions.indices.toList(),
                    selected = state.directionIndex,
                    label = { "Ke ${state.directions[it].headsign}" },
                    onSelect = { onSelect(it); showAll = false },
                )
            }
        }
    }
}

/** Satu kedatangan menurut jadwal: jam besar + "dalam N mnt". Yang pertama (terdekat) terisi mint. */
@Composable
private fun DepartureCard(departure: Departure, highlighted: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        shape = shape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = posterBorder(if (highlighted) 3.5.dp else 2.5.dp),
        modifier = modifier.posterShadow(shape, if (highlighted) 5.dp else 3.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(departure.at, style = MaterialTheme.typography.displaySmall.copy(fontSize = 24.sp))
            Text("dalam ${departure.minutes} mnt", style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Catatan sumber/ketidakpastian jadwal di sheet stasiun. */
private fun sheetNote(kind: String?): String? = when {
    kind == "KRL" -> "Menurut jadwal KRL (Open-KRL). Bukan posisi nyata dan bisa berubah tanpa pemberitahuan."
    kind == "BRT" || kind == "Angkutan Umum Integrasi" || kind == "Mikrotrans" || kind == "Transjabodetabek" ->
        "Menurut jadwal GTFS Transjakarta, bukan posisi bus."
    else -> null
}
