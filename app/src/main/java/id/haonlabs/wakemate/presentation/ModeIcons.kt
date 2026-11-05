package id.haonlabs.wakemate.presentation

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.ui.theme.SignalBlue
import id.haonlabs.wakemate.ui.theme.SignalRed
import id.haonlabs.wakemate.ui.theme.SignalYellow

/** Pemetaan moda ke ikon disimpan di sini, bukan di enum, supaya domain tetap bebas Android. */
@DrawableRes
fun TransitMode.iconRes(): Int = when (this) {
    TransitMode.KRL -> R.drawable.ic_mode_krl
    TransitMode.MRT -> R.drawable.ic_mode_mrt
    TransitMode.LRT -> R.drawable.ic_mode_lrt
    TransitMode.TRANSJAKARTA -> R.drawable.ic_mode_tj
}

/** Oranye moda KRL (Figma GPS1/GPS2). */
val KrlOrange = Color(0xFFFF9A3D)

/** Warna blok moda (identitas moda, seperti di Figma GPS1); ikon di atasnya selalu tinta. */
fun TransitMode.blockColor(): Color = when (this) {
    TransitMode.KRL -> KrlOrange
    TransitMode.MRT -> SignalBlue
    TransitMode.LRT -> SignalYellow
    TransitMode.TRANSJAKARTA -> SignalRed
}
