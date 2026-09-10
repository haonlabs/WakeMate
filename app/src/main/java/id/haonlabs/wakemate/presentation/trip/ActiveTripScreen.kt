package id.haonlabs.wakemate.presentation.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveTripScreen(
    onFinished: () -> Unit,
    viewModel: ActiveTripViewModel = hiltViewModel(),
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()

    // Sesi bisa berakhir dari mana saja — tombol notifikasi, timeout, atau service yang berhenti.
    LaunchedEffect(trip) {
        if (trip == null) onFinished()
    }
    val current = trip ?: return

    Scaffold(topBar = { TopAppBar(title = { Text("Sesi berjalan") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Menuju", style = MaterialTheme.typography.labelLarge)
            Text(
                current.destination.name,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(current.lineName, style = MaterialTheme.typography.bodyMedium)

            when (val proximity = current.proximity) {
                null -> Text("Menunggu sinyal GPS pertama...")
                else -> ProgressBlock(proximity)
            }

            current.proximity?.let { WarningsFor(it, current) }

            OutlinedButton(
                onClick = { viewModel.onCancel() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Batalkan perjalanan")
            }
        }
    }
}

@Composable
private fun ProgressBlock(proximity: ProximityState) {
    Text(
        if (proximity.stationsRemaining <= 0) "Sudah sampai" else "${proximity.stationsRemaining}",
        style = MaterialTheme.typography.displayLarge,
    )
    if (proximity.stationsRemaining > 0) {
        Text("stasiun lagi", style = MaterialTheme.typography.titleMedium)
    }
    Text(
        String.format(
            Locale.US,
            "%.1f km · terdekat: %s",
            proximity.distanceToDestinationMeters / 1000,
            proximity.nearestStation.name,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun WarningsFor(proximity: ProximityState, trip: ActiveTrip) {
    val warning = when {
        proximity.isOffRoute ->
            "Posisimu jauh dari ${trip.lineName}. Pastikan kamu naik line yang benar."
        proximity.isDeadReckoned ->
            "Sinyal GPS hilang. Angka di atas estimasi dari kecepatan terakhir."
        proximity.direction == TravelDirection.AWAY_FROM_DESTINATION ->
            "Kamu bergerak menjauh dari tujuan. Cek arah keberangkatan."
        else -> null
    } ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(warning, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
