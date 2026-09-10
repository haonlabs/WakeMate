package id.haonlabs.wakemate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import id.haonlabs.wakemate.R

/**
 * Plus Jakarta Sans, sesuai sistem desain. Satu berkas font variabel dipakai untuk semua bobot
 * lewat FontVariation, jadi tidak perlu membundel tiga berkas statis.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PlusJakartaSans = FontFamily(
    jakarta(FontWeight.Normal),
    jakarta(FontWeight.Medium),
    jakarta(FontWeight.SemiBold),
    jakarta(FontWeight.Bold),
)

private val base = Typography()

/** Angka sisa stasiun dibaca sekilas oleh orang yang baru bangun, jadi displaynya dipertegas. */
val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = PlusJakartaSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = PlusJakartaSans),
    bodySmall = base.bodySmall.copy(fontFamily = PlusJakartaSans),
    labelLarge = base.labelLarge.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = base.labelSmall.copy(fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium),
)
