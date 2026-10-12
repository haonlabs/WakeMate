package id.haonlabs.wakemate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Gelap dan terang mengikuti setelan sistem; warna dinamis sengaja tidak dipakai.
 *
 * Warna dinamis Android 12+ akan menimpa identitas teal/amber dengan wallpaper user, dan identitas
 * itu justru yang membuat alarm WakeMate dikenali sekilas oleh orang yang baru bangun.
 *
 * secondaryContainer = latar lembut untuk hal soal alarm (sorotan halte tujuan, status "alarm
 * aktif"); di tema gelap ia amber transparan supaya tetap menyatu dengan permukaan di bawahnya.
 */
private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = OnTeal,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTeal,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = Amber.copy(alpha = 0.18f),
    onSecondaryContainer = Amber,
    tertiary = Teal,
    onTertiary = OnTeal,
    background = Midnight,
    onBackground = Moonlight,
    surface = Midnight,
    onSurface = Moonlight,
    surfaceVariant = MidnightHighest,
    onSurfaceVariant = Haze,
    surfaceTint = Teal,
    surfaceBright = MidnightBright,
    surfaceDim = Midnight,
    surfaceContainerLowest = MidnightLowest,
    surfaceContainerLow = MidnightLow,
    surfaceContainer = MidnightContainer,
    surfaceContainerHigh = MidnightHigh,
    surfaceContainerHighest = MidnightHighest,
    outline = Outline,
    outlineVariant = MidnightHighest,
    scrim = Color(0xFF010914),
    error = ErrorLight,
    onError = OnError,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerLight,
)

private val LightColors = lightColorScheme(
    primary = TealDeep,
    onPrimary = Color.White,
    primaryContainer = TealMist,
    onPrimaryContainer = OnTeal,
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = AmberMist,
    onSecondaryContainer = AmberDeep,
    tertiary = TealDeep,
    onTertiary = Color.White,
    background = Daylight,
    onBackground = Ink,
    surface = Daylight,
    onSurface = Ink,
    surfaceVariant = DaylightHighest,
    onSurfaceVariant = InkSoft,
    surfaceTint = TealDeep,
    surfaceBright = Color.White,
    surfaceDim = DaylightHighest,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DaylightLow,
    surfaceContainer = Color.White,
    surfaceContainerHigh = DaylightHigh,
    surfaceContainerHighest = DaylightHighest,
    outline = InkMuted,
    outlineVariant = Hairline,
    scrim = Ink,
    error = ErrorDeep,
    onError = Color.White,
    errorContainer = ErrorMist,
    onErrorContainer = OnErrorMist,
)

@Composable
fun WakeMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
