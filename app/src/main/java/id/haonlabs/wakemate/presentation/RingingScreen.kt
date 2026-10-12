package id.haonlabs.wakemate.presentation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import id.haonlabs.wakemate.R

/**
 * LT7: alarm berbunyi, layar penuh amber. Dipakai alarm live maupun GPS — saat alarm berbunyi,
 * satu-satunya hal yang penting adalah "bangun", jadi tidak ada navigasi lain di layar ini.
 *
 * Tombol kembali sengaja tidak menutupnya: menutup layar tanpa mematikan alarm hanya membuat
 * bunyinya terus jalan di belakang tanpa kontrol yang terlihat.
 */
@Composable
fun RingingScreen(
    destination: String,
    detail: String,
    meta: String?,
    onAwake: () -> Unit,
    onSnooze: (() -> Unit)?,
) {
    BackHandler {}
    val scheme = MaterialTheme.colorScheme
    // Latar amber terang di tema gelap butuh ikon status bar gelap; kembalikan saat layar ditutup.
    val view = LocalView.current
    val lightBackground = scheme.secondary.luminance() > 0.5f
    DisposableEffect(view, lightBackground) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = lightBackground
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
    val pulse by rememberInfiniteTransition(label = "denyut").animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skala",
    )
    Surface(Modifier.fillMaxSize(), color = scheme.secondary, contentColor = scheme.onSecondary) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(200.dp).scale(pulse).background(scheme.onSecondary.copy(alpha = 0.12f), CircleShape))
                Box(Modifier.size(152.dp).background(scheme.onSecondary.copy(alpha = 0.22f), CircleShape))
                Box(Modifier.size(104.dp).background(scheme.onSecondary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_alarm), contentDescription = null, tint = scheme.secondary, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("BERSIAP TURUN", style = MaterialTheme.typography.labelMedium)
            Text(destination, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            meta?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onAwake,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onSecondary, contentColor = scheme.secondary),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Saya sudah bangun", style = MaterialTheme.typography.titleLarge) }
            if (onSnooze != null) {
                TextButton(onClick = onSnooze, colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSecondary)) {
                    Text("Ingatkan lagi di halte berikutnya", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
