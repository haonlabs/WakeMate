package id.haonlabs.wakemate.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Bahasa gerak WakeMate (spesifikasi sama dengan style animasi "Motion" di Figma):
 *  - Enter: konten naik 24dp + fade, per item selisih [StaggerMs].
 *  - Pop: kartu/lencana membesar 70% → 100% dengan spring memantul.
 *  - Pulse: dot live dan indikator alarm "bernapas".
 *
 * Semua gerak mati kalau user mematikan animasi sistem ([rememberReducedMotion]) — gerak tak
 * berhingga seperti riak alarm juga berhenti. Konten tetap tampil di posisi akhirnya.
 */
object Motion {
    const val StaggerMs = 60L
    const val MaxStagger = 8
    val Pop = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    val Settle = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

/** True kalau "Remove animations" / skala animator = 0 di setelan sistem. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Masuk dari bawah (atau samping) + fade, tertunda sesuai urutan [index]. Hanya [Motion.MaxStagger] item pertama. */
fun Modifier.enterIn(index: Int = 0, fromX: Dp = 0.dp, fromY: Dp = 24.dp): Modifier = composed {
    val reduced = rememberReducedMotion()
    val skip = reduced || index >= Motion.MaxStagger
    val progress = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!skip) {
            delay(index * Motion.StaggerMs)
            progress.animateTo(1f, Motion.Settle)
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = (p * 1.6f).coerceIn(0f, 1f)
        translationX = (1f - p) * fromX.toPx()
        translationY = (1f - p) * fromY.toPx()
    }
}

/** Membesar dari [from] dengan spring memantul, tertunda [delayMs]. */
fun Modifier.popIn(delayMs: Long = 0, from: Float = 0.7f): Modifier = composed {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(delayMs)
            progress.animateTo(1f, Motion.Pop)
        }
    }
    graphicsLayer {
        val s = from + (1f - from) * progress.value
        scaleX = s
        scaleY = s
        alpha = (progress.value * 3f).coerceIn(0f, 1f)
    }
}

/**
 * Nilai 0..1 yang naik-turun terus (denyut). Saat gerak dikurangi, nilainya tetap 0 sehingga
 * pemakainya tinggal menampilkan keadaan diam.
 */
@Composable
fun rememberPulse(millis: Int = 1200): Float {
    if (rememberReducedMotion()) return 0f
    return rememberInfiniteTransition(label = "denyut").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "denyut",
    ).value
}

/** Denyut membesar [amount] dari ukuran asli — untuk dot live dan lencana "ALARM AKTIF". */
@Composable
fun Modifier.pulsing(amount: Float = 0.08f, millis: Int = 1200): Modifier {
    val p = rememberPulse(millis)
    return graphicsLayer {
        val s = 1f + amount * p
        scaleX = s
        scaleY = s
    }
}

/** Bayangan keras (tanpa blur) bergeser [offset] ke kanan-bawah, khas gaya poster. Gambar di belakang konten. */
fun Modifier.hardShadow(shape: Shape, color: Color, offset: Dp = 4.dp): Modifier = drawBehind {
    val o = offset.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    translate(o, o) { drawOutline(outline, color) }
}
