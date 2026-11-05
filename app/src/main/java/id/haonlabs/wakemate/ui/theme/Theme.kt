package id.haonlabs.wakemate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Token di luar skema Material yang dibutuhkan gaya "Signal Pop": warna border dan bayangan keras
 * (tinta di tema terang, krem di tema gelap), plus warna aksi yang sama di kedua tema.
 */
@Immutable
class WakeColors(
    val border: Color,
    val shadow: Color,
    val live: Color,
    val alarm: Color,
    val onColor: Color,
)

private val LightWake = WakeColors(border = Ink, shadow = Ink, live = Live, alarm = Alarm, onColor = OnColor)
private val DarkWake = WakeColors(border = CreamText, shadow = CreamText, live = Live, alarm = Alarm, onColor = OnColor)

private val LocalWakeColors = staticCompositionLocalOf { LightWake }

/** `Wake.colors.border` dst. — pasangan MaterialTheme.colorScheme untuk token yang tak punya slot di Material. */
object Wake {
    val colors: WakeColors
        @Composable @ReadOnlyComposable get() = LocalWakeColors.current
}

/**
 * Gelap dan terang mengikuti setelan sistem; warna dinamis sengaja tidak dipakai (identitas
 * mint/amber yang membuat alarm dikenali sekilas oleh orang yang baru bangun).
 *
 * Pemetaan ke Material:
 *  - primary = tinta (terang) / krem (gelap): teks penekanan DAN isi elemen terpilih (chip, segmen,
 *    tab aktif) — di desain keduanya satu warna "inverse".
 *  - primaryContainer = mint: tombol aksi utama dan penanda live.
 *  - secondary / secondaryContainer = amber: semua yang soal alarm; teks di atasnya selalu tinta.
 *  - outline = warna teks redup; outlineVariant = warna border tebal.
 */
private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Cream,
    primaryContainer = Live,
    onPrimaryContainer = OnColor,
    secondary = Alarm,
    onSecondary = OnColor,
    secondaryContainer = Alarm,
    onSecondaryContainer = OnColor,
    tertiary = Live,
    onTertiary = OnColor,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = CreamSunken,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = Paper,
    surfaceDim = CreamSunken,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = CreamSunken,
    surfaceContainerHighest = CreamSunken,
    outline = InkMuted,
    outlineVariant = Ink,
    scrim = Ink,
    error = SignalRed,
    onError = OnColor,
    errorContainer = SignalRed,
    onErrorContainer = OnColor,
)

private val DarkColors = darkColorScheme(
    primary = CreamText,
    onPrimary = InkBg,
    primaryContainer = Live,
    onPrimaryContainer = OnColor,
    secondary = Alarm,
    onSecondary = OnColor,
    secondaryContainer = Alarm,
    onSecondaryContainer = OnColor,
    tertiary = Live,
    onTertiary = OnColor,
    background = InkBg,
    onBackground = CreamText,
    surface = InkBg,
    onSurface = CreamText,
    surfaceVariant = InkSunken,
    onSurfaceVariant = CreamMuted,
    surfaceTint = Color.Transparent,
    surfaceBright = InkSunken,
    surfaceDim = InkBg,
    surfaceContainerLowest = InkBg,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSunken,
    surfaceContainerHighest = InkSunken,
    outline = CreamMuted,
    outlineVariant = CreamText,
    scrim = Color(0xFF000000),
    error = SignalRed,
    onError = OnColor,
    errorContainer = SignalRed,
    onErrorContainer = OnColor,
)

@Composable
fun WakeMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalWakeColors provides if (darkTheme) DarkWake else LightWake) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content,
        )
    }
}
