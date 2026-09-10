package id.haonlabs.wakemate.presentation.trip

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
    val stops by viewModel.stops.collectAsStateWithLifecycle()

    // Sesi bisa berakhir dari mana saja — tombol notifikasi, timeout, atau service yang berhenti.
    LaunchedEffect(trip) {
        if (trip == null) onFinished()
    }
    val current = trip ?: return

    Scaffold(topBar = { TopAppBar(title = { Text("Perjalanan aktif") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeadlineCard(current) }

            item {
                // Peringatan muncul dan hilang mengikuti kondisi GPS, jadi jangan menyentak.
                AnimatedVisibility(
                    visible = current.proximity?.let { warningFor(it, current) } != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val warning = current.proximity?.let { warningFor(it, current) }.orEmpty()
                    WarningCard(warning)
                }
            }

            if (stops.isNotEmpty()) {
                item {
                    Text(
                        "Rute & posisi",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(stops, key = { it.station.id }) {
                    StopRow(it, Modifier.animateItem(placementSpec = tween(250)))
                }
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.onCancel() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("Batalkan pantauan perjalanan")
                }
            }
            item {
                Text(
                    "HP boleh dikunci dan ditaruh di saku. WakeMate tetap memantau di latar belakang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun HeadlineCard(trip: ActiveTrip) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "MENUJU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                trip.destination.name,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                trip.lineName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            when (val proximity = trip.proximity) {
                null -> Text("Menunggu sinyal GPS pertama...")
                else -> {
                    // Angka ini yang paling sering dilirik orang setengah tidur; pergantiannya
                    // digeser ke atas supaya terbaca sebagai hitungan mundur, bukan kedipan.
                    AnimatedContent(
                        targetState = proximity.stationsRemaining,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn()) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut())
                        },
                        label = "sisa",
                    ) { remaining ->
                        Text(
                            if (remaining <= 0) "Sampai" else "$remaining",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (proximity.stationsRemaining > 0) {
                        Text("stasiun lagi", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        String.format(
                            Locale.US, "%.1f km sepanjang rute",
                            proximity.distanceToDestinationMeters / 1000,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopRow(stop: RouteStop, modifier: Modifier = Modifier) {
    val accent = when (stop.status) {
        StopStatus.CURRENT -> MaterialTheme.colorScheme.primary
        StopStatus.WAKE_POINT -> MaterialTheme.colorScheme.secondary
        StopStatus.DESTINATION -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val faded = stop.status == StopStatus.PASSED

    val dotSize by animateDpAsState(
        if (stop.status == StopStatus.CURRENT) 14.dp else 10.dp,
        label = "titik",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(dotSize)
                .background(if (faded) MaterialTheme.colorScheme.outlineVariant else accent, CircleShape)
        )
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                stop.station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (stop.status == StopStatus.PASSED) FontWeight.Normal else FontWeight.SemiBold,
                color = if (faded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (faded) TextDecoration.LineThrough else null,
            )
            stop.status.caption()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = accent)
            }
        }
        stop.status.badge()?.let { StatusBadge(it, accent) }
    }
}

@Composable
private fun StatusBadge(label: String, accent: Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun StopStatus.caption(): String? = when (this) {
    StopStatus.CURRENT -> "Posisimu sekarang"
    StopStatus.WAKE_POINT -> "Alarm mulai berbunyi di sini"
    StopStatus.DESTINATION -> "Tujuan akhir"
    else -> null
}

private fun StopStatus.badge(): String? = when (this) {
    StopStatus.PASSED -> "Lewat"
    StopStatus.CURRENT -> "Saat ini"
    StopStatus.WAKE_POINT -> "Titik bangun"
    StopStatus.DESTINATION -> "Tujuan"
    StopStatus.UPCOMING -> null
}

private fun warningFor(proximity: ProximityState, trip: ActiveTrip): String? = when {
    proximity.isOffRoute ->
        "Posisimu jauh dari ${trip.lineName}. Pastikan kamu naik line yang benar."
    proximity.isDeadReckoned ->
        "Sinyal GPS hilang. Angka di atas estimasi dari kecepatan terakhir."
    proximity.direction == TravelDirection.AWAY_FROM_DESTINATION ->
        "Kamu bergerak menjauh dari tujuan. Cek arah keberangkatan."
    else -> null
}

@Composable
private fun WarningCard(warning: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp)) {
            Spacer(Modifier.width(0.dp))
            Text(warning, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
