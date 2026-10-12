package id.haonlabs.wakemate.presentation.live

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.haonlabs.wakemate.data.live.LiveWatchState
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.LiveWatch
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.domain.live.label
import id.haonlabs.wakemate.domain.live.summary

/** Warna GTFS "RRGGBB"; rute tanpa warna valid jatuh ke warna utama tema. */
fun parseRouteColor(hex: String, fallback: Color): Color =
    hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let { Color(0xFF000000 or it) } ?: fallback

@Composable
fun RouteBadge(route: TjRoute, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 48.dp),
        shape = RoundedCornerShape(10.dp),
        color = parseRouteColor(route.color, MaterialTheme.colorScheme.primary),
        contentColor = parseRouteColor(route.textColor, Color.Black),
    ) {
        Text(
            route.shortName,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

/**
 * Status alarm live yang sedang berjalan. Amber disimpan untuk hal-hal soal alarm, sama seperti
 * di alur GPS.
 */
@Composable
fun LiveWatchCard(state: LiveWatchState, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val ringing = state.alertedAt != null
    val watch = state.watch
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (ringing) MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (ringing) "ALARM BERBUNYI" else "ALARM LIVE AKTIF",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                when (watch) {
                    is LiveWatch.Onboard -> "Bangunkan sebelum ${watch.halteName}"
                    is LiveWatch.Arrival -> "Bus ${watch.routeId} tiba di ${watch.halteName}"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildString {
                    when (watch) {
                        is LiveWatch.Onboard -> append("Bus ${watch.busNo} · ")
                        is LiveWatch.Arrival -> state.approach?.let { append("${it.bus.busNo} · ") }
                    }
                    append(state.approach?.summary() ?: "Menunggu data live...")
                    append("\nBunyi ${watch.lead.label()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ringing) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Saya sudah bangun — matikan alarm", style = MaterialTheme.typography.titleSmall)
                }
            } else {
                TextButton(onClick = onStop, modifier = Modifier.align(Alignment.End)) {
                    Text("Batalkan")
                }
            }
        }
    }
}

/** Pilihan kapan alarm berbunyi; [onboard] menentukan pilihan dan default yang masuk akal. */
@Composable
fun LeadPickerDialog(
    title: String,
    description: String,
    onboard: Boolean,
    onConfirm: (LiveLead) -> Unit,
    onDismiss: () -> Unit,
) {
    // Di dalam bus jumlah halte lebih bisa dipegang (sama dengan default "2 stasiun" alur GPS);
    // di halte, orang berpikir dalam menit.
    val options = if (onboard) {
        listOf(LiveLead.Stops(1), LiveLead.Stops(2), LiveLead.Stops(3), LiveLead.Minutes(3), LiveLead.Minutes(5))
    } else {
        listOf(LiveLead.Minutes(3), LiveLead.Minutes(5), LiveLead.Minutes(10), LiveLead.Stops(1), LiveLead.Stops(2))
    }
    var selected by remember { mutableStateOf(options[1]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                onClick = { selected = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(option.label().replaceFirstChar(Char::uppercase), Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(selected) }) { Text("Pasang") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
