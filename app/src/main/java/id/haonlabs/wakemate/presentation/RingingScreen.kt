package id.haonlabs.wakemate.presentation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.ui.theme.enterIn
import id.haonlabs.wakemate.ui.theme.rememberReducedMotion

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
    val reduced = rememberReducedMotion()
    val transition = rememberInfiniteTransition(label = "alarm")
    // Tiga riak mengembang bergantian (selisih 650ms), seperti gelombang bunyi yang keluar dari jam.
    @Composable
    fun ripple(offsetMs: Int): State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1950, easing = FastOutSlowInEasing), RepeatMode.Restart, initialStartOffset = androidx.compose.animation.core.StartOffset(offsetMs)),
        label = "riak",
    )
    val ripple1 = ripple(0)
    val ripple2 = ripple(650)
    val ripple3 = ripple(1300)
    // Jam bergoyang kiri-kanan sekali (±14°) lalu diam sebentar, berulang.
    val wobble by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2400
                0f at 0 using LinearEasing
                -14f at 150
                14f at 400
                -14f at 650
                14f at 900
                0f at 1150
            },
        ),
        label = "goyang",
    )
    Surface(Modifier.fillMaxSize(), color = scheme.secondary, contentColor = scheme.onSecondary) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                listOf(ripple1, ripple2, ripple3).forEach { r ->
                    Box(
                        Modifier
                            .size(240.dp)
                            .graphicsLayer {
                                val p = if (reduced) 0.5f else r.value
                                val s = 0.5f + 0.5f * p
                                scaleX = s
                                scaleY = s
                                alpha = if (reduced) 0.16f else 0.3f * (1f - p)
                            }
                            .background(scheme.onSecondary, CircleShape),
                    )
                }
                Box(Modifier.size(112.dp).background(scheme.onSecondary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_alarm),
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(52.dp).graphicsLayer { rotationZ = if (reduced) 0f else wobble },
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("BERSIAP TURUN", style = MaterialTheme.typography.labelMedium, modifier = Modifier.enterIn(0))
            Text(destination, style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center, modifier = Modifier.enterIn(1, fromY = 40.dp))
            Text(detail, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.enterIn(2))
            meta?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.enterIn(3)) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onAwake,
                shape = RoundedCornerShape(22.dp),
                contentPadding = PaddingValues(vertical = 24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = scheme.onSecondary, contentColor = scheme.secondary),
                modifier = Modifier.fillMaxWidth().enterIn(4, fromY = 80.dp),
            ) { Text("Saya sudah bangun", style = MaterialTheme.typography.titleLarge) }
            if (onSnooze != null) {
                TextButton(onClick = onSnooze, colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSecondary)) {
                    Text("Ingatkan lagi di halte berikutnya", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
