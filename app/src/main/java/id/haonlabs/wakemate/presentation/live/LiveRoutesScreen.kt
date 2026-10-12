package id.haonlabs.wakemate.presentation.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import id.haonlabs.wakemate.presentation.components.SectionLabel
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
                Column {
                    Text("WAKEMATE LIVE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Bus Transjakarta", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item(key = "cari") { SearchField(state.query, viewModel::onQueryChanged) }
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
                                items(state.recent, key = { it.id }) { route ->
                                    RecentCard(route, state.liveCounts[route.id], onClick = { onRouteSelected(route.id) })
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
                    items(state.routes, key = { it.id }) { route ->
                        RouteCard(
                            route = route,
                            live = state.liveCounts[route.id],
                            favorite = route.id in state.favorites,
                            onClick = { onRouteSelected(route.id) },
                            onFavorite = { viewModel.toggleFavorite(route.id) },
                            modifier = Modifier.animateItem(fadeInSpec = tween(180), placementSpec = tween(220)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Cari rute: 1, 9D, JAK.31, Blok M…") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onChange("") }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Hapus pencarian") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RecentCard(route: TjRoute, live: Int?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.width(156.dp)) {
        Column(Modifier.padding(12.dp).heightIn(min = 104.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RouteBadge(route)
            Text(route.longName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            live?.let { LivePill("$it bus") }
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
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteBadge(route)
            Column(Modifier.weight(1f)) {
                Text(route.longName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(route.kind, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            live?.let { LivePill("$it bus") }
            IconButton(onClick = onFavorite) {
                Icon(
                    painterResource(if (favorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = if (favorite) "Hapus dari favorit" else "Jadikan favorit",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
