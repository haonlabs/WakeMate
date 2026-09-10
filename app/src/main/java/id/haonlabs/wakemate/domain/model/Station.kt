package id.haonlabs.wakemate.domain.model

data class Line(
    val id: String,
    val name: String,
    val mode: TransitMode,
)

data class Station(
    val id: String,
    val name: String,
    val mode: TransitMode,
    val lat: Double,
    val lng: Double,
    val lineId: String,
    val sequenceInLine: Int,
)

/** Hasil pencarian: satu nama stasiun bisa muncul di beberapa line (mis. Manggarai), lihat FR-3. */
data class StationWithLine(
    val station: Station,
    val lineName: String,
)
