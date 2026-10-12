package id.haonlabs.wakemate.presentation.trip

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.proximity.ProximityState
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import id.haonlabs.wakemate.presentation.components.CircleIconButton
import id.haonlabs.wakemate.presentation.components.CountdownCard
import id.haonlabs.wakemate.presentation.components.InfoRow
import id.haonlabs.wakemate.presentation.components.SectionLabel
import id.haonlabs.wakemate.presentation.components.StatusPill
import id.haonlabs.wakemate.presentation.components.TimelineRow
import id.haonlabs.wakemate.presentation.components.TimelineStatus
import java.util.Locale

/**
 * GPS3: pola yang sama dengan perjalanan live (LT6) supaya dua jenis alarm terasa satu aplikasi.
 * Saat alarm berbunyi, layar penuh di tingkat aplikasi (RingingScreen) yang mengambil alih.
 */
@Composable
fun ActiveTripScreen(
    onBack: () -> Unit,
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
    val proximity = current.proximity

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "atas") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircleIconButton(R.drawable.ic_arrow_back, "Kembali", onBack)
                    StatusPill("ALARM AKTIF")
                }
            }
            item(key = "hitungan") {
                val currentIndex = stops.indexOfFirst { it.status == StopStatus.CURRENT }
                val remaining = proximity?.stationsRemaining
                CountdownCard(
                    eyebrow = "MENUJU",
                    title = current.destination.name,
                    count = when {
                        remaining == null -> "–"
                        remaining <= 0 -> "Sampai"
                        else -> remaining.toString()
                    },
                    unit = when {
                        proximity == null -> "menunggu sinyal GPS"
                        proximity.stationsRemaining <= 0 -> current.lineName
                        else -> String.format(Locale.US, "stasiun lagi · %.1f km", proximity.distanceToDestinationMeters / 1000).replace('.', ',')
                    },
                    segments = (stops.size - 1).coerceAtLeast(0),
                    done = currentIndex.coerceAtLeast(0),
                    wakeAt = stops.indexOfFirst { it.status == StopStatus.WAKE_POINT }.takeIf { it >= 0 },
                    legend = stops.firstOrNull { it.status == StopStatus.WAKE_POINT }?.let { "Bunyi di ${it.station.name}" },
                )
            }
            item(key = "sinyal") { SignalRow(proximity, current) }

            if (stops.isNotEmpty()) {
                item(key = "judul-stasiun") { SectionLabel("STASIUN BERIKUTNYA", trailing = current.lineName) }
                itemsIndexed(stops, key = { _, it -> it.station.id }) { index, stop ->
                    TimelineRow(
                        name = stop.station.name,
                        status = when (stop.status) {
                            StopStatus.PASSED -> TimelineStatus.PASSED
                            StopStatus.DESTINATION -> TimelineStatus.DESTINATION
                            else -> TimelineStatus.DEFAULT
                        },
                        lineColor = MaterialTheme.colorScheme.primary,
                        detail = when (stop.status) {
                            StopStatus.CURRENT -> "Posisimu"
                            StopStatus.WAKE_POINT -> "Alarm bunyi"
                            StopStatus.PASSED -> "Lewat"
                            else -> null
                        },
                        isFirst = index == 0,
                        isLast = index == stops.lastIndex,
                        modifier = Modifier.animateItem(placementSpec = tween(250)),
                    )
                }
            }

            item(key = "batal") {
                OutlinedButton(
                    onClick = { viewModel.onCancel() },
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        if (current.hasAlerted) "Batalkan tanpa menandai sampai" else "Batalkan pantauan",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            item(key = "catatan") {
                Text(
                    "HP boleh dikunci dan ditaruh di saku. WakeMate tetap memantau di latar belakang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Status sinyal selalu terlihat — termasuk peringatan salah jalur dan estimasi di terowongan. */
@Composable
private fun SignalRow(proximity: ProximityState?, trip: ActiveTrip) {
    val scheme = MaterialTheme.colorScheme
    val (title, subtitle, warn) = when {
        proximity == null -> Triple("Menunggu sinyal GPS pertama...", "Pastikan lokasi ponsel menyala.", false)
        proximity.isOffRoute -> Triple("Posisi jauh dari ${trip.lineName}", "Pastikan kamu naik line yang benar.", true)
        proximity.isDeadReckoned -> Triple("Sinyal GPS hilang", "Angka di atas estimasi dari kecepatan terakhir.", true)
        proximity.direction == TravelDirection.AWAY_FROM_DESTINATION -> Triple("Bergerak menjauh dari tujuan", "Cek arah keberangkatan.", true)
        else -> Triple("Sinyal GPS baik", "Arah perjalanan benar.", false)
    }
    InfoRow(
        R.drawable.ic_my_location,
        title = title,
        subtitle = subtitle,
        iconTint = if (warn) scheme.error else scheme.primary,
        container = if (warn) scheme.errorContainer else scheme.surfaceContainer,
    )
}
