package id.haonlabs.wakemate.presentation.live

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.data.live.FeedStatus
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.WakePreview
import id.haonlabs.wakemate.domain.live.summary
import id.haonlabs.wakemate.presentation.TripPermissions
import id.haonlabs.wakemate.presentation.components.AlarmBar
import id.haonlabs.wakemate.presentation.components.BusCard
import id.haonlabs.wakemate.presentation.components.ChipChoices
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.IconBadge
import id.haonlabs.wakemate.presentation.components.InfoRow
import id.haonlabs.wakemate.presentation.components.PillChip
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.SegmentedControl
import id.haonlabs.wakemate.presentation.components.TimelineRow
import id.haonlabs.wakemate.presentation.components.TimelineStatus
import kotlinx.coroutines.launch

private val PEEK = 400.dp
private val ONBOARD_LEADS = listOf(LiveLead.Stops(1), LiveLead.Stops(2), LiveLead.Stops(3), LiveLead.Minutes(3), LiveLead.Minutes(5))
private val ARRIVAL_LEADS = listOf(LiveLead.Minutes(3), LiveLead.Minutes(5), LiveLead.Minutes(10), LiveLead.Stops(1), LiveLead.Stops(2))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteLiveScreen(
    onBack: () -> Unit,
    onOpenLiveTrip: () -> Unit,
    viewModel: RouteLiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val routeColor = state.route?.let { parseRouteColor(it.color, MaterialTheme.colorScheme.primary) }
        ?: MaterialTheme.colorScheme.primary
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded, skipHiddenState = true)
    val scaffold = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    var arrivalFor by remember { mutableStateOf<Halte?>(null) }
    var cameraTarget by remember { mutableStateOf<CameraTarget?>(null) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }

    // Tanpa izin notifikasi alarm tetap berbunyi, tapi tombol "Matikan" di notifikasi tidak
    // terlihat — jadi diminta, namun penolakan tidak menghalangi.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStart?.invoke(); pendingStart = null
    }
    fun withNotificationPermission(start: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !TripPermissions.hasNotifications(context)) {
            pendingStart = start
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else start()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.locate()
    }
    fun expand() = scope.launch { sheetState.expand() }

    BackHandler(enabled = state.mode != SheetMode.Browse) { viewModel.onSheetBack() }

    // Memilih bus dan halte turun butuh daftar yang utuh: sheet dibuka penuh (termasuk saat datang
    // dari "Ubah halte") dan dikunci di sana sampai user kembali ke mode jelajah.
    val choosing = state.mode != SheetMode.Browse
    // Kunci geser bawaan tidak menahan tarikan yang datang dari gulir daftar, jadi sheet yang
    // turun selama memilih langsung dibuka lagi.
    LaunchedEffect(choosing, sheetState.currentValue) {
        if (choosing && sheetState.currentValue != SheetValue.Expanded) sheetState.expand()
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var panelHeight by remember { mutableStateOf(0.dp) }
    // Halte yang baru diketuk tetap terlihat walau panel ambang membesar di bawahnya.
    LaunchedEffect(state.destination?.id) {
        val id = state.destination?.id ?: return@LaunchedEffect
        val row = state.boardingStops.indexOfFirst { it.halte.id == id }
        if (row >= 0) listState.animateScrollToItem((BOARDING_HEADER_ITEMS + row - 1).coerceAtLeast(0))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight - 96.dp
        BottomSheetScaffold(
            scaffoldState = scaffold,
            sheetPeekHeight = PEEK,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            sheetSwipeEnabled = !choosing,
            sheetDragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
            sheetContent = {
                val bottomInset = if (state.mode is SheetMode.Boarding) panelHeight + 16.dp else 24.dp
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = sheetMax),
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (val mode = state.mode) {
                        SheetMode.Browse -> browseItems(
                            state = state,
                            routeColor = routeColor,
                            onOpenLiveTrip = onOpenLiveTrip,
                            onStopWatch = viewModel::stopWatch,
                            onDirection = viewModel::onDirectionSelected,
                            onBusChosen = { viewModel.onBusChosen(it); expand() },
                            onPickBus = { viewModel.onPickBus(); expand() },
                            onWaitAtHalte = {
                                val nearest = state.nearestHalte
                                if (nearest != null) arrivalFor = nearest
                                else { hint = "Ketuk halte tempat kamu menunggu di daftar di bawah."; expand() }
                            },
                            onHalte = { arrivalFor = it },
                            hint = hint,
                        )
                        SheetMode.PickBus -> pickBusItems(
                            state = state,
                            onBack = { viewModel.onSheetBack() },
                            onBusChosen = viewModel::onBusChosen,
                            needsLocation = state.userLocation == null && !TripPermissions.hasForegroundLocation(context),
                            onAskLocation = { locationLauncher.launch(TripPermissions.foreground) },
                        )
                        is SheetMode.Boarding -> boardingItems(
                            state = state,
                            routeColor = routeColor,
                            onBack = { viewModel.onSheetBack() },
                            onDestination = viewModel::onDestinationSelected,
                            busMissing = state.boardingBus == null,
                            busNo = mode.busNo,
                        )
                    }
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                val boardingNo = (state.mode as? SheetMode.Boarding)?.busNo
                LiveMap(
                    shape = state.direction?.shape.orEmpty(),
                    halte = state.halte.map { it.halte },
                    buses = state.buses.map { it.bus },
                    activeTripIds = state.directionTripIds,
                    selectedBusNo = boardingNo ?: (state.watch?.watch as? LiveWatch.Onboard)?.busNo,
                    routeColor = routeColor.toArgb(),
                    onBusClick = { no -> if (no != null) { viewModel.onBusChosen(no); expand() } },
                    bottomPadding = PEEK,
                    target = cameraTarget,
                    modifier = Modifier.fillMaxSize(),
                )
                TopOverlay(state, onBack = { if (!viewModel.onSheetBack()) onBack() }, onFavorite = viewModel::toggleFavorite)
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

        if (state.mode is SheetMode.Boarding && state.boardingBus != null) {
            BoardingPanel(
                state = state,
                onLead = viewModel::onLeadSelected,
                onStart = { withNotificationPermission { viewModel.startOnboard(onStarted = onOpenLiveTrip) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { panelHeight = with(density) { it.height.toDp() } },
            )
        }
    }

    arrivalFor?.let { halte ->
        ArrivalSheet(
            halte = halte,
            state = state,
            onDismiss = { arrivalFor = null },
            onStart = { lead ->
                arrivalFor = null
                withNotificationPermission { viewModel.startArrival(halte, lead) }
            },
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
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = 2.dp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.route?.let { RouteBadge(it) }
                Column(Modifier.weight(1f)) {
                    Text(state.route?.longName.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val (text, color) = when (state.feed) {
                        FeedStatus.LIVE -> "Live · ${state.buses.size} bus" to MaterialTheme.colorScheme.primary
                        FeedStatus.OFFLINE -> "Data live tidak tersedia" to MaterialTheme.colorScheme.error
                        else -> "Menghubungkan..." to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
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

private fun androidx.compose.foundation.lazy.LazyListScope.browseItems(
    state: RouteLiveUi,
    routeColor: androidx.compose.ui.graphics.Color,
    onOpenLiveTrip: () -> Unit,
    onStopWatch: () -> Unit,
    onDirection: (Int) -> Unit,
    onBusChosen: (String) -> Unit,
    onPickBus: () -> Unit,
    onWaitAtHalte: () -> Unit,
    onHalte: (Halte) -> Unit,
    hint: String?,
) {
    // Satu item atas yang selalu ada: kalau bar alarm/arah baru disisipkan setelah data dimuat,
    // LazyColumn mempertahankan posisi pada item lama dan keduanya terdorong keluar layar.
    item(key = "atas") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.watch?.let { active ->
                val w = active.watch
                AlarmBar(
                    title = when (w) {
                        is LiveWatch.Onboard -> "Bangunkan sebelum ${w.halteName}"
                        is LiveWatch.Arrival -> "Bus tiba di ${w.halteName}"
                    },
                    subtitle = active.approach?.let { "${it.bus.busNo} · ${it.summary()}" } ?: "Menunggu data live...",
                    ringing = active.alertedAt != null,
                    onClick = onOpenLiveTrip,
                    onStop = onStopWatch,
                )
            }
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
    item(key = "terdekat-judul") {
        SectionLabel(
            "BUS TERDEKAT DARIMU",
            trailing = (state.towardBuses.size - 1).takeIf { it > 0 }?.let { "$it lainnya" },
        )
    }
    item(key = "terdekat") {
        val nearest = state.nearestBus
        if (nearest == null) {
            InfoRow(
                R.drawable.ic_mode_tj,
                title = if (state.feed == FeedStatus.LIVE) "Belum ada bus live ke arah ini" else "Menunggu data live...",
                subtitle = "Rute dan halte tetap bisa dilihat.",
                iconTint = MaterialTheme.colorScheme.outline,
            )
        } else {
            BusCard(
                busNo = nearest.bus.busNo,
                info = listOf(nearest.bus.busType, "ke ${nearest.bus.headsign}").filter { it.isNotBlank() }.joinToString(" · "),
                distance = nearest.distanceMeters?.let(::formatDistance),
                onClick = { onBusChosen(nearest.bus.busNo) },
            )
        }
    }
    item(key = "aksi") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPickBus,
                enabled = state.towardBuses.isNotEmpty() || state.buses.isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Saya di bus ini", style = MaterialTheme.typography.titleSmall) }
            FilledTonalButton(
                onClick = onWaitAtHalte,
                // Tonal bawaan memakai secondaryContainer, yang di tema ini warna alarm.
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Tunggu di halte", style = MaterialTheme.typography.titleSmall) }
        }
    }
    hint?.let { item(key = "petunjuk") { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } }
    item(key = "halte-judul") { SectionLabel("HALTE · KETUK UNTUK PENGINGAT", trailing = "${state.halte.size} halte") }
    itemsIndexed(state.halte, key = { i, row -> "h-${row.halte.id}-$i" }) { i, row ->
        TimelineRow(
            name = row.halte.name,
            status = if (row.busHere != null) TimelineStatus.BUS_HERE else TimelineStatus.DEFAULT,
            lineColor = routeColor,
            eta = row.next?.etaLabel(),
            detail = row.busHere?.busNo ?: row.next?.bus?.busNo,
            isFirst = i == 0,
            isLast = i == state.halte.lastIndex,
            onClick = { onHalte(row.halte) },
        )
    }
}

@Composable
private fun DirectionChips(state: RouteLiveUi, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    // Arah default dipilih otomatis dan bisa jatuh di luar layar.
    LaunchedEffect(state.directionIndex) { listState.animateScrollToItem(state.directionIndex) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(state.directions, key = { _, trip -> trip.id }) { index, trip ->
            PillChip("Ke ${trip.headsign}", index == state.directionIndex, onClick = { onSelect(index) })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pickBusItems(
    state: RouteLiveUi,
    onBack: () -> Unit,
    onBusChosen: (String) -> Unit,
    needsLocation: Boolean,
    onAskLocation: () -> Unit,
) {
    item(key = "pilih-judul") {
        SheetHeader(
            title = "Kamu di bus yang mana?",
            subtitle = "Diurutkan dari yang paling dekat dengan posisimu. Nomor bus tertempel di kaca depan dan di atas pintu.",
            onBack = onBack,
        )
    }
    if (needsLocation) {
        item(key = "izin-lokasi") {
            Surface(onClick = onAskLocation, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                InfoRow(
                    R.drawable.ic_my_location,
                    title = "Izinkan lokasi",
                    subtitle = "Supaya bus yang kamu naiki muncul paling atas.",
                    container = androidx.compose.ui.graphics.Color.Transparent,
                )
            }
        }
    }
    val list = state.towardBuses.ifEmpty { state.buses }
    itemsIndexed(list, key = { _, it -> "bus-${it.bus.busNo}" }) { index, item ->
        val best = index == 0 && item.distanceMeters != null
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (best) {
                Row(
                    Modifier.padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(0.dp))
                    Text("PALING MUNGKIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            BusCard(
                busNo = item.bus.busNo,
                info = listOf(item.bus.busType, "ke ${item.bus.headsign}").filter { it.isNotBlank() }.joinToString(" · "),
                distance = item.distanceMeters?.let(::formatDistance),
                highlighted = best,
                onClick = { onBusChosen(item.bus.busNo) },
            )
        }
    }
    item(key = "pilih-alt") {
        Text(
            "Tidak ada di daftar? Ketuk busnya langsung di peta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.boardingItems(
    state: RouteLiveUi,
    routeColor: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
    onDestination: (Halte) -> Unit,
    busMissing: Boolean,
    busNo: String,
) {
    item(key = "turun-judul") {
        SheetHeader(
            title = "Turun di halte mana?",
            subtitle = if (busMissing) "Bus $busNo belum terlihat di data live. Tunggu sebentar atau pilih bus lain."
            else "$busNo · ${state.route?.shortName?.let { "Rute $it " }.orEmpty()}ke ${state.boardingBus?.headsign.orEmpty()}",
            onBack = onBack,
        )
    }
    itemsIndexed(state.boardingStops, key = { i, row -> "t-${row.halte.id}-$i" }) { i, row ->
        val isDest = row.halte.id == state.destination?.id
        TimelineRow(
            name = row.halte.name,
            status = if (isDest) TimelineStatus.DESTINATION else TimelineStatus.DEFAULT,
            lineColor = routeColor,
            eta = row.next?.etaLabel(),
            isFirst = i == 0,
            isLast = i == state.boardingStops.lastIndex,
            onClick = { onDestination(row.halte) },
        )
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 0.dp)) {
            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Kembali")
        }
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Item di atas daftar halte turun (judul); dipakai untuk menggulir ke halte terpilih. */
private const val BOARDING_HEADER_ITEMS = 1

/**
 * Panel ambang yang menempel di bawah: tombol pasang selalu terjangkau jempol. Sebelum halte
 * dipilih panel ini hanya satu baris petunjuk — chip dan tombol baru berguna setelah ada tujuan,
 * dan panel penuh sejak awal justru menutupi daftar halte yang harus dipilih.
 */
@Composable
private fun BoardingPanel(state: RouteLiveUi, onLead: (LiveLead) -> Unit, onStart: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 12.dp,
    ) {
        val destination = state.destination
        if (destination == null) {
            Row(
                Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_expand_less), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Ketuk halte tempat kamu turun di daftar di atas.", style = MaterialTheme.typography.titleSmall)
            }
            return@Surface
        }
        Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("BANGUNKAN SAYA")
            ChipChoices(ONBOARD_LEADS, state.lead, { it.chipLabel() }, onLead)
            val explanation = when (val p = state.preview) {
                null -> "Bus belum terlihat di data live — tunggu sebentar."
                WakePreview.Immediately -> "Bus sudah sedekat itu — alarm langsung berbunyi begitu dipasang."
                is WakePreview.AtHalte -> buildString {
                    append("Bunyi setelah bus meninggalkan ${state.halteNames[p.halteId] ?: p.halteId}")
                    p.minutesBefore?.takeIf { it > 0 }?.let { append(" — sekitar $it menit sebelum ${destination.name}") }
                    append(".")
                }
                is WakePreview.Minutes -> "Bunyi sekitar ${p.minutes} menit sebelum ${destination.name}, menurut perkiraan Transjakarta."
            }
            InfoRow(
                R.drawable.ic_alarm,
                title = explanation,
                iconTint = MaterialTheme.colorScheme.secondary,
                container = MaterialTheme.colorScheme.secondaryContainer,
            )
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pasang alarm · ${destination.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalSheet(halte: Halte, state: RouteLiveUi, onDismiss: () -> Unit, onStart: (LiveLead) -> Unit) {
    var lead by remember { mutableStateOf<LiveLead>(LiveLead.Minutes(5)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ingatkan saat bus tiba di ${halte.name}", style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.route?.let { RouteBadge(it) }
                    Text("Ke ${state.direction?.headsign.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val next = state.halte.firstOrNull { it.halte.id == halte.id }?.next
            Row(
                Modifier.fillMaxWidth().padding(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(R.drawable.ic_mode_tj, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text("Bus berikutnya", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(next?.bus?.busNo ?: "Belum ada yang menuju ke sini", style = MaterialTheme.typography.titleSmall)
                        }
                        next?.let { Text(it.etaLabel(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
            SectionLabel("BERI TAHU SAAT BUS TINGGAL")
            ChipChoices(ARRIVAL_LEADS, lead, { it.chipLabel() }, { lead = it })
            Text(
                "Notifikasi muncul sekali, lalu pengingat berhenti sendiri. Hanya bus ke arah ${state.direction?.headsign.orEmpty()} yang dihitung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Button(
                onClick = { onStart(lead) },
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pasang pengingat", style = MaterialTheme.typography.titleMedium) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Batal") }
        }
    }
}
