package id.haonlabs.wakemate.presentation.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.BuildConfig
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.StationWithLine
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.presentation.components.IconBadge
import id.haonlabs.wakemate.presentation.components.PosterSearchField
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.SegmentedControl
import id.haonlabs.wakemate.presentation.components.StatusPill
import id.haonlabs.wakemate.presentation.components.posterBorder
import id.haonlabs.wakemate.presentation.components.posterShadow
import id.haonlabs.wakemate.presentation.iconRes
import id.haonlabs.wakemate.ui.theme.SignalBlue
import id.haonlabs.wakemate.ui.theme.SignalRed
import id.haonlabs.wakemate.ui.theme.SignalYellow
import id.haonlabs.wakemate.ui.theme.enterIn

/** GPS1: alarm berbasis GPS ponsel untuk KRL, MRT, dan koridor BRT — bekerja tanpa internet. */
@Composable
fun HomeScreen(
    onStationSelected: (Station) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    val searching = query.isNotBlank()

    // Tanpa Surface pembungkus, LocalContentColor jatuh ke hitam dan teks tanpa warna eksplisit
    // hilang di atas latar gelap.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 52.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "judul") { Header(onOpenDiagnostics) }
            item(key = "moda") {
                // Label pendek supaya tiga moda selalu muat berjajar; ikonnya yang membedakan.
                SegmentedControl(
                    options = TransitMode.entries.map { it.shortLabel() },
                    selected = TransitMode.entries.indexOf(mode),
                    onSelect = { viewModel.onModeSelected(TransitMode.entries[it]) },
                    icons = TransitMode.entries.map { it.iconRes() },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.enterIn(1),
                )
            }
            item(key = "cari") {
                PosterSearchField(
                    query = query,
                    onChange = viewModel::onQueryChanged,
                    placeholder = "Cari stasiun tujuan",
                    enabled = isReady,
                    modifier = Modifier.enterIn(2),
                )
            }

            when {
                !isReady -> item(key = "memuat") { SeedingState() }
                searching -> {
                    item(key = "hasil-judul") {
                        SectionLabel("HASIL PENCARIAN", trailing = if (results.isEmpty()) "Tidak ada" else "${results.size} stasiun")
                    }
                    if (results.isEmpty()) item(key = "kosong") { EmptyState(query) }
                    itemsIndexed(results, key = { _, it -> it.station.id }) { index, result ->
                        StationRow(
                            result = result,
                            meta = result.lineName,
                            onClick = { onStationSelected(result.station) },
                            modifier = Modifier
                                .animateItem(fadeInSpec = tween(200), placementSpec = tween(220))
                                .enterIn(index + 2, fromX = 96.dp, fromY = 0.dp),
                        )
                    }
                }
                recent.isNotEmpty() -> {
                    item(key = "terakhir-judul") { SectionLabel("TUJUAN TERAKHIR") }
                    itemsIndexed(recent, key = { _, it -> "r-" + it.station.id }) { index, result ->
                        StationRow(
                            result = result,
                            meta = "${result.lineName} · ${result.station.mode.shortLabel()}",
                            onClick = { onStationSelected(result.station) },
                            modifier = Modifier.enterIn(index + 2, fromX = 96.dp, fromY = 0.dp),
                        )
                    }
                }
                else -> item(key = "ajakan") { SearchPrompt(mode) }
            }
        }
    }
}

private fun TransitMode.shortLabel() = when (this) {
    TransitMode.KRL -> "KRL"
    TransitMode.MRT -> "MRT"
    TransitMode.TRANSJAKARTA -> "TJ"
}

/** Warna blok moda (data identitas moda, seperti di Figma GPS1); ikon di atasnya selalu tinta. */
private fun TransitMode.blockColor(): Color = when (this) {
    TransitMode.KRL -> Color(0xFFFF9A3D)
    TransitMode.MRT -> SignalBlue
    TransitMode.TRANSJAKARTA -> SignalRed
}

@Composable
private fun Header(onOpenDiagnostics: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.enterIn(0), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill("WAKEMATE", color = SignalYellow)
                Text("Alarm GPS", style = MaterialTheme.typography.displaySmall)
            }
            // Alat ukur internal; tidak dimunculkan di rilis.
            if (BuildConfig.DEBUG) TextButton(onClick = onOpenDiagnostics) { Text("Audio") }
        }
        Text(
            "Untuk KRL, MRT, dan koridor BRT. Bekerja tanpa internet — posisimu dibaca dari GPS ponsel.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Baris tujuan: kartu poster dengan blok warna moda dan panah — lebih besar supaya aman diketuk. */
@Composable
private fun StationRow(result: StationWithLine, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onClick,
        shape = shape,
        color = scheme.surfaceContainer,
        border = posterBorder(),
        modifier = modifier.fillMaxWidth().posterShadow(shape, 3.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBadge(result.station.mode.iconRes(), tint = result.station.mode.blockColor())
            Column(Modifier.weight(1f)) {
                Text(result.station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = scheme.outline)
        }
    }
}

@Composable
private fun SeedingState() {
    CenteredState {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text("Menyiapkan data stasiun...", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchPrompt(mode: TransitMode) {
    CenteredState {
        IconBadge(R.drawable.ic_search, size = 64.dp)
        Text("Cari tujuanmu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ketik nama stasiun atau halte ${mode.shortLabel()} yang jadi tujuanmu. Tujuan yang pernah dipakai akan muncul di sini.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    CenteredState {
        Text("Tidak ada yang cocok dengan \"$query\"", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        Text(
            "Coba nama lain, atau ganti moda transportasinya.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}
