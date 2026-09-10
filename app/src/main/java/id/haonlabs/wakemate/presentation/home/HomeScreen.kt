package id.haonlabs.wakemate.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.BuildConfig
import id.haonlabs.wakemate.domain.model.TransitMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStationSelected: (id.haonlabs.wakemate.domain.model.Station) -> Unit,
    onOpenDiagnostics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WakeMate") },
                actions = {
                    // Alat ukur internal; tidak dimunculkan di rilis.
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onOpenDiagnostics) { Text("Audio") }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransitMode.entries.forEach { option ->
                    FilterChip(
                        selected = option == mode,
                        onClick = { viewModel.onModeSelected(option) },
                        label = { Text(option.label.substringBefore(' ')) },
                        // Teal untuk seleksi; amber disimpan khusus untuk hal-hal soal alarm,
                        // supaya warnanya tetap berarti saat benar-benar dipakai.
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text("Cari stasiun/halte tujuan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isReady) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Menyiapkan data stasiun...", modifier = Modifier.padding(top = 16.dp))
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.station.id }) { result ->
                    // Satu nama bisa muncul di beberapa line (mis. Manggarai), jadi line selalu ditampilkan.
                    ListItem(
                        headlineContent = { Text(result.station.name) },
                        supportingContent = { Text(result.lineName) },
                        modifier = Modifier.clickable { onStationSelected(result.station) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
