package id.haonlabs.wakemate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import id.haonlabs.wakemate.R

/**
 * Tiga keluarga font (semua OFL, berkas variabel — satu berkas untuk semua bobot):
 *  - Unbounded: angka dan judul besar (display) — lebar, tebal, terbaca sekilas saat baru bangun.
 *  - Bricolage Grotesque: judul kartu dan tombol (title/headline).
 *  - Plus Jakarta Sans: teks bacaan, label, keterangan.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(res: Int, weight: FontWeight) = Font(
    resId = res,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private fun family(res: Int, vararg weights: FontWeight) = FontFamily(weights.map { variable(res, it) })

val PlusJakartaSans = family(R.font.plus_jakarta_sans, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
val BricolageGrotesque = family(R.font.bricolage_grotesque, FontWeight.Bold, FontWeight.ExtraBold)
val Unbounded = family(R.font.unbounded, FontWeight.Bold, FontWeight.Black)

private fun display(size: Int) = TextStyle(
    fontFamily = Unbounded, fontWeight = FontWeight.Black,
    fontSize = size.sp, lineHeight = (size * 1.15f).sp, letterSpacing = (-0.03).em,
)

private fun title(size: Int, lineHeight: Float = 1.2f) = TextStyle(
    fontFamily = BricolageGrotesque, fontWeight = FontWeight.ExtraBold,
    fontSize = size.sp, lineHeight = (size * lineHeight).sp, letterSpacing = (-0.02).em,
)

private fun body(size: Int, weight: FontWeight, lineHeight: Float = 1.4f) = TextStyle(
    fontFamily = PlusJakartaSans, fontWeight = weight, fontSize = size.sp, lineHeight = (size * lineHeight).sp,
)

val Typography = Typography(
    displayLarge = display(44),
    displayMedium = display(34),
    displaySmall = display(28),
    headlineLarge = title(30),
    headlineMedium = title(26),
    headlineSmall = title(22),
    titleLarge = title(20),
    titleMedium = title(16),
    titleSmall = title(14),
    bodyLarge = body(15, FontWeight.SemiBold),
    bodyMedium = body(14, FontWeight.SemiBold),
    bodySmall = body(12, FontWeight.Medium),
    labelLarge = body(14, FontWeight.Bold),
    labelMedium = body(11, FontWeight.Bold).copy(letterSpacing = 0.1.em),
    labelSmall = body(11, FontWeight.Medium),
)
