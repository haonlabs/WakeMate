package id.haonlabs.wakemate.presentation.live

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import id.haonlabs.wakemate.domain.live.Approach
import id.haonlabs.wakemate.domain.live.LiveLead
import id.haonlabs.wakemate.domain.live.TjRoute
import java.util.Locale

/** Warna GTFS "RRGGBB"; rute tanpa warna valid jatuh ke warna utama tema. */
fun parseRouteColor(hex: String, fallback: Color): Color =
    hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let { Color(0xFF000000 or it) } ?: fallback

/** Kode rute dengan warna resmi GTFS (route_color / route_text_color) — data, bukan token tema. */
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
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** "±4 mnt", atau "Tiba" saat bus sudah di halte itu. */
fun Approach.etaLabel(): String = when {
    stopsAway <= 1 && (etaMin ?: 0) <= 0 -> "Tiba"
    etaMin != null -> "±$etaMin mnt"
    stopsAway <= 1 -> "berikutnya"
    else -> "$stopsAway halte"
}

/** Label pendek untuk chip ambang: "2 halte", "5 menit". */
fun LiveLead.chipLabel(): String = when (this) {
    is LiveLead.Stops -> "$count halte"
    is LiveLead.Minutes -> "$minutes menit"
}

fun formatDistance(meters: Float): String =
    if (meters < 1_000) "${meters.toInt()} m" else String.format(Locale.US, "%.1f km", meters / 1_000).replace('.', ',')

fun updatedAgo(updatedAt: Long): String {
    val seconds = ((System.currentTimeMillis() - updatedAt) / 1_000).coerceAtLeast(0)
    return if (seconds < 60) "diperbarui $seconds dtk lalu" else "diperbarui ${seconds / 60} mnt lalu"
}
