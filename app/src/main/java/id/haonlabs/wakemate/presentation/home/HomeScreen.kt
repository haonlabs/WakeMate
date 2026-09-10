package id.haonlabs.wakemate.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import id.haonlabs.wakemate.presentation.chipLabel
import id.haonlabs.wakemate.presentation.iconRes

@Composable
fun HomeScreen(
    onStationSelected: (Station) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    val searching = query.isNotBlank()

    // Tanpa Surface pembungkus, LocalContentColor jatuh ke hitam dan teks tanpa warna eksplisit
    // hilang di atas latar gelap.
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BrandBar(onOpenDiagnostics) }
            item { HeroCard() }
            item { ModeSelector(mode, viewModel::onModeSelected) }
            item { SearchField(query, viewModel::onQueryChanged, enabled = isReady) }

            when {
                !isReady -> item { SeedingState() }

                // Daftar lengkap 400+ stasiun bukan sesuatu yang dibaca orang; yang berguna
                // muncul begitu mereka mengetik.
                !searching -> item { SearchPrompt(mode) }

                else -> {
                    item { ResultsLabel(results.size) }
                    if (results.isEmpty()) {
                        item { EmptyState(query) }
                    } else {
                        items(results, key = { it.station.id }) { result ->
                            StationCard(
                                result = result,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(200),
                                    placementSpec = tween(220),
                                ),
                                onClick = { onStationSelected(result.station) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandBar(onOpenDiagnostics: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Vector muka ikon sudah diskalakan ke zona aman peluncur, jadi di badge kecil ia tampak
        // mengambang. Digambar lebih besar lalu dipotong kotaknya.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(70.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "WAKEMATE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Transit Alarm", style = MaterialTheme.typography.titleLarge)
        }
        // Alat ukur internal; tidak dimunculkan di rilis.
        if (BuildConfig.DEBUG) {
            TextButton(onClick = onOpenDiagnostics) { Text("Audio") }
        }
    }
}

@Composable
private fun HeroCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Mau turun di stasiun mana?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "WakeMate menjaga rutenya dan membangunkanmu tepat waktu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModeSelector(selected: TransitMode, onSelect: (TransitMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "MODA TRANSPORTASI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Cakupan datanya memang hanya Jabodetabek; disebut supaya tidak jadi kejutan.
            Text(
                "Jabodetabek",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Label penuh tidak muat berjajar tiga di layar sempit, jadi barisnya bisa digeser.
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TransitMode.entries.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option.chipLabel()) },
                    leadingIcon = {
                        Icon(
                            painterResource(option.iconRes()),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    // Teal untuk seleksi; amber disimpan khusus untuk hal-hal soal alarm.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(query: String, onQueryChanged: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        enabled = enabled,
        placeholder = { Text("Cari stasiun atau halte") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Hapus pencarian")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultsLabel(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "HASIL PENCARIAN",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Crossfade(count, label = "jumlah") { total ->
            Text(
                if (total == 0) "Tidak ada" else "$total stasiun",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StationCard(
    result: StationWithLine,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(result.station.mode.iconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    result.station.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    result.lineName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        Box(
            Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text("Cari tujuanmu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ketik nama stasiun atau halte ${mode.chipLabel()} yang jadi tujuanmu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState(query: String) {
    CenteredState {
        Text(
            "Tidak ada yang cocok dengan \"$query\"",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
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
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content()
    }
}
