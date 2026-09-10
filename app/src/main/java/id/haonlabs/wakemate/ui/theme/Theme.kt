package id.haonlabs.wakemate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Gelap saja, dan warna dinamis sengaja tidak dipakai.
 *
 * Sistem desainnya hanya mendefinisikan varian gelap — membuat varian terang berarti mengarang
 * warna yang tidak ada di sana. Warna dinamis Android 12+ akan menimpa identitas teal/amber ini
 * dengan wallpaper user, dan identitas itu justru yang membuat notifikasi WakeMate dikenali
 * sekilas oleh orang yang baru bangun.
 */
private val WakeMateColors = darkColorScheme(
    primary = Teal,
    onPrimary = OnTeal,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTeal,
    secondary = Amber,
    onSecondary = OnAmber,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmber,
    tertiary = TealSoft,
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
    error = ErrorLight,
    onError = OnError,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerLight,
)

@Composable
fun WakeMateTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = WakeMateColors,
        typography = Typography,
        content = content,
    )
}
