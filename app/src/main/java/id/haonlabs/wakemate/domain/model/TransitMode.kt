package id.haonlabs.wakemate.domain.model

/**
 * Tuning per moda. Angka mengacu PRD §7.3: kepadatan halte Transjakarta jauh lebih tinggi
 * daripada jarak antar stasiun KRL, jadi toleransi tidak boleh seragam.
 */
enum class TransitMode(
    val label: String,
    /** Sejauh apa user boleh menyimpang dari polyline line sebelum dianggap salah naik. */
    val offRouteToleranceMeters: Double,
    /** Radius yang dianggap "sudah sampai" tujuan. */
    val arrivalRadiusMeters: Double,
) {
    KRL("KRL Commuter Line", offRouteToleranceMeters = 1_500.0, arrivalRadiusMeters = 250.0),
    MRT("MRT Jakarta", offRouteToleranceMeters = 1_000.0, arrivalRadiusMeters = 200.0),
    TRANSJAKARTA("Transjakarta", offRouteToleranceMeters = 400.0, arrivalRadiusMeters = 100.0),
}
