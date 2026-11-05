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
import androidx.compose.ui.unit.sp
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.presentation.components.posterBorder

/** Warna GTFS "RRGGBB"; rute tanpa warna valid jatuh ke warna utama tema. */
fun parseRouteColor(hex: String, fallback: Color): Color =
    hex.toLongOrNull(16)?.takeIf { hex.length == 6 }?.let { Color(0xFF000000 or it) } ?: fallback

/** Kode rute dengan warna resmi GTFS (route_color / route_text_color) — data, bukan token tema. */
@Composable
fun RouteBadge(route: TjRoute, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        color = parseRouteColor(route.color, MaterialTheme.colorScheme.primaryContainer),
        contentColor = parseRouteColor(route.textColor, Color.Black),
        border = posterBorder(),
    ) {
        Text(
            route.shortName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Lencana rute versi kecil untuk kartu padat (daftar "Sekitar Saya"). */
@Composable
fun RouteBadgeSmall(route: TjRoute, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(min = 32.dp),
        shape = RoundedCornerShape(8.dp),
        color = parseRouteColor(route.color, MaterialTheme.colorScheme.primaryContainer),
        contentColor = parseRouteColor(route.textColor, Color.Black),
        border = posterBorder(2.dp),
    ) {
        Text(
            route.shortName,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
