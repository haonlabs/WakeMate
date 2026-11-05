package id.haonlabs.wakemate.domain.live

import id.haonlabs.wakemate.domain.model.TransitMode

/**
 * Penghubung peta rute ke data alarm GPS (`stations_seed.json`). Tidak semua rute punya jalur di
 * data alarm: hanya KRL, MRT, LRT, dan koridor BRT. Mikrotrans, Royaltrans, dsb. tidak — tombol
 * alarm disembunyikan untuknya, bukan dibiarkan gagal.
 */
fun TjRoute.alarmMode(): TransitMode? = when {
    kind == "KRL" -> TransitMode.KRL
    kind == "MRT" -> TransitMode.MRT
    kind.startsWith("LRT") -> TransitMode.LRT
    kind == "BRT" && shortName.isNotEmpty() && shortName.all(Char::isDigit) -> TransitMode.TRANSJAKARTA
    else -> null
}

/** Jalur di data alarm yang sesuai rute ini, untuk memilih jalur yang benar di stasiun transit. */
fun TjRoute.alarmLineHint(): String? = when (id) {
    "krl:BOGOR" -> "krl-bogor"
    "krl:CIKARANG" -> "krl-cikarang-manggarai"
    "krl:RANGKASBITUNG" -> "krl-rangkasbitung"
    "krl:TANGERANG" -> "krl-tangerang"
    "krl:TANJUNGPRIUK" -> "krl-tanjung-priuk"
    "mrt:north-south" -> "mrt-north-south"
    "lrtj:selatan" -> "lrt-jakarta-selatan"
    "lrtjbd:cibubur" -> "lrt-jabodebek-cibubur"
    "lrtjbd:bekasi" -> "lrt-jabodebek-bekasi"
    else -> if (alarmMode() == TransitMode.TRANSJAKARTA) "tj-koridor-$shortName" else null
}
