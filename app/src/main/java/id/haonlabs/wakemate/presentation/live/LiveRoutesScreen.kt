package id.haonlabs.wakemate.presentation.live

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.data.live.LiveRoutePrefs
import id.haonlabs.wakemate.data.live.LiveWatchRepository
import id.haonlabs.wakemate.data.live.TjLiveFeed
import id.haonlabs.wakemate.data.live.TjStaticSource
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.presentation.components.LivePill
import id.haonlabs.wakemate.presentation.components.PillChip
import id.haonlabs.wakemate.presentation.components.PosterSearchField
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.StatusPill
import id.haonlabs.wakemate.presentation.components.posterBorder
import id.haonlabs.wakemate.presentation.components.posterShadow
import id.haonlabs.wakemate.ui.theme.Motion
import id.haonlabs.wakemate.ui.theme.SignalRed
import id.haonlabs.wakemate.ui.theme.SignalYellow
import id.haonlabs.wakemate.ui.theme.Wake
import id.haonlabs.wakemate.ui.theme.enterIn
import id.haonlabs.wakemate.ui.theme.popIn
import id.haonlabs.wakemate.service.LiveAlarms
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kelompok route_desc GTFS untuk filter. "Lainnya" = Rusun, Royaltrans, Bus Wisata. */
enum class RouteKind(val label: String) {
    SEMUA("Semua"), BRT("BRT"), INTEGRASI("Integrasi"), MIKROTRANS("Mikrotrans"), JABODETABEK("Jabodetabek"), LAINNYA("Lainnya");

    fun matches(route: TjRoute): Boolean = when (this) {
        SEMUA -> true
        BRT -> route.kind == "BRT"
        INTEGRASI -> route.kind == "Angkutan Umum Integrasi"
        MIKROTRANS -> route.kind == "Mikrotrans"
        JABODETABEK -> route.kind == "Transjabodetabek"
        LAINNYA -> route.kind !in setOf("BRT", "Angkutan Umum Integrasi", "Mikrotrans", "Transjabodetabek")
    }
}

data class LiveRoutesUi(
    val loading: Boolean = true,
    val query: String = "",
    val kind: RouteKind = RouteKind.SEMUA,
    val recent: List<TjRoute> = emptyList(),
    /** Favorit di atas, lalu sisanya; sudah tersaring query dan jenis. */
    val routes: List<TjRoute> = emptyList(),
    val favorites: Set<String> = emptySet(),
    /** Jumlah bus live, hanya untuk rute favorit dan yang terakhir dibuka. */
    val liveCounts: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class LiveRoutesViewModel @Inject constructor(
    staticSource: TjStaticSource,
    feed: TjLiveFeed,
    watches: LiveWatchRepository,
    private val prefs: LiveRoutePrefs,
    private val liveAlarms: LiveAlarms,
) : ViewModel() {

    private val all = MutableStateFlow<List<TjRoute>?>(null)
    private val query = MutableStateFlow("")
    private val kind = MutableStateFlow(RouteKind.SEMUA)

    /**
     * ponytail: jumlah live hanya untuk ≤6 rute pilihan user. Menghitung semua 240 rute berarti
     * subscribe seluruh armada (>100 pesan/detik) — beban yang sengaja dihindari di TjLiveFeed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val liveCounts = combine(prefs.favorites, prefs.recent) { favs, recent -> (recent + favs).distinct().take(6) }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyMap())
            else combine(ids.map { id -> feed.route(id).map { id to it.size } }) { pairs -> pairs.toMap() }
        }

    val state: StateFlow<LiveRoutesUi> = combine(
        all, combine(query, kind, ::Pair), prefs.favorites, prefs.recent, liveCounts,
    ) { routes, (q, k), favs, recentIds, counts ->
        if (routes == null) return@combine LiveRoutesUi(loading = true)
        val byId = routes.associateBy { it.id }
        val needle = q.trim()
        val filtered = routes.filter { r ->
            k.matches(r) && (needle.isEmpty() || r.shortName.contains(needle, true) || r.longName.contains(needle, true))
        }
        LiveRoutesUi(
            loading = false,
            query = q,
            kind = k,
            recent = recentIds.mapNotNull(byId::get),
            routes = filtered.sortedByDescending { it.id in favs },
            favorites = favs,
            liveCounts = counts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveRoutesUi())

    init {
        viewModelScope.launch {
            all.value = staticSource.network().routes
            // Sesi yang selamat dari proses dibunuh harus terlihat lagi dan benar-benar dipantau,
            // bukan cuma tampil di layar.
            if (watches.restore() != null) liveAlarms.resume()
        }
    }

    fun onQueryChanged(value: String) { query.value = value }
    fun onKindSelected(value: RouteKind) { kind.value = value }
    fun toggleFavorite(routeId: String) = viewModelScope.launch { prefs.toggleFavorite(routeId) }
}

@Composable
fun LiveRoutesScreen(
    onRouteSelected: (String) -> Unit,
    viewModel: LiveRoutesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searching = state.query.isNotBlank()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "judul") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.enterIn(0)) {
                    StatusPill("WAKEMATE LIVE", color = SignalYellow, modifier = Modifier.padding(end = 8.dp))
                    Text("Bus\nTransjakarta", style = MaterialTheme.typography.displaySmall)
                }
            }
            item(key = "cari") { PosterSearchField(state.query, viewModel::onQueryChanged, "Cari rute: 1, 9D, JAK.31, Blok M…", Modifier.enterIn(1)) }
            item(key = "jenis") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RouteKind.entries, key = { it.name }) { k ->
                        PillChip(k.label, k == state.kind, onClick = { viewModel.onKindSelected(k) })
                    }
                }
            }
            when {
                state.loading -> item(key = "memuat") {
                    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
                }
                else -> {
                    if (!searching && state.recent.isNotEmpty()) {
                        item(key = "terakhir-judul") { SectionLabel("TERAKHIR DIBUKA") }
                        item(key = "terakhir") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                itemsIndexed(state.recent, key = { _, r -> r.id }) { index, route ->
                                    RecentCard(route, state.liveCounts[route.id], onClick = { onRouteSelected(route.id) }, modifier = Modifier.popIn(300L + index * 110L, from = 0.6f))
                                }
                            }
                        }
                    }
                    item(key = "semua-judul") {
                        SectionLabel(if (searching) "HASIL PENCARIAN" else "SEMUA RUTE", trailing = "${state.routes.size} rute")
                    }
                    if (state.routes.isEmpty()) {
                        item(key = "kosong") {
                            Text(
                                "Tidak ada rute yang cocok. Coba kode lain atau ganti filter jenis.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    itemsIndexed(state.routes, key = { _, r -> r.id }) { index, route ->
                        RouteCard(
                            route = route,
                            live = state.liveCounts[route.id],
                            favorite = route.id in state.favorites,
                            onClick = { onRouteSelected(route.id) },
                            onFavorite = { viewModel.toggleFavorite(route.id) },
                            modifier = Modifier
                                .animateItem(fadeInSpec = tween(180), placementSpec = tween(220))
                                .enterIn(index + 3, fromX = 96.dp, fromY = 0.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Kartu "terakhir dibuka": blok penuh warna resmi rute (GTFS), nomor besar, jumlah bus live. */
@Composable
private fun RecentCard(route: TjRoute, live: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val fill = parseRouteColor(route.color, MaterialTheme.colorScheme.primaryContainer)
    val onFill = parseRouteColor(route.textColor, Wake.colors.onColor)
    Surface(
        onClick = onClick,
        shape = shape,
        color = fill,
        contentColor = onFill,
        border = posterBorder(),
        modifier = modifier.width(172.dp).posterShadow(shape),
    ) {
        Column(Modifier.padding(16.dp).heightIn(min = 112.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(route.shortName, style = MaterialTheme.typography.displaySmall, maxLines = 1)
            Text(route.longName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            live?.let { LivePill("$it bus", container = Wake.colors.onColor, content = Wake.colors.live) }
        }
    }
}

@Composable
private fun RouteCard(
    route: TjRoute,
    live: Int?,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    // Bintang memantul saat dijadikan favorit.
    val star by animateFloatAsState(if (favorite) 1.2f else 1f, Motion.Pop, label = "bintang")
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().posterShadow(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = posterBorder(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteBadge(route)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(route.longName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live != null) LivePill("$live bus")
                else Text(route.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    painterResource(if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = if (favorite) "Hapus dari favorit" else "Jadikan favorit",
                    tint = if (favorite) SignalRed else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp).graphicsLayer { scaleX = star; scaleY = star },
                )
            }
        }
    }
}
