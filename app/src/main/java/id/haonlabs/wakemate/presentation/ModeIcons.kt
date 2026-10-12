package id.haonlabs.wakemate.presentation

import androidx.annotation.DrawableRes
import id.haonlabs.wakemate.R
import id.haonlabs.wakemate.domain.model.TransitMode

/** Pemetaan moda ke ikon disimpan di sini, bukan di enum, supaya domain tetap bebas Android. */
@DrawableRes
fun TransitMode.iconRes(): Int = when (this) {
    TransitMode.KRL -> R.drawable.ic_mode_krl
    TransitMode.MRT -> R.drawable.ic_mode_mrt
    TransitMode.TRANSJAKARTA -> R.drawable.ic_mode_tj
}
