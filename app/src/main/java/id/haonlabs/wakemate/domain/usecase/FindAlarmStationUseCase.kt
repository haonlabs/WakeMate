package id.haonlabs.wakemate.domain.usecase

import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.repository.StationRepository
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Menyambung peta rute ke alarm GPS: dari koordinat halte/stasiun di peta, cari stasiun yang sama
 * di data alarm (`stations_seed.json`). Keduanya dari sumber berbeda (GTFS vs OpenStreetMap), jadi
 * dicocokkan lewat jarak, bukan id. Null kalau tidak ada yang cukup dekat (mis. halte mikrotrans,
 * yang tidak punya jalur di data alarm).
 */
class FindAlarmStationUseCase @Inject constructor(
    private val stations: StationRepository,
) {
    /**
     * [preferredLineId] memenangkan stasiun di jalur itu kalau ada yang dalam radius — stasiun
     * transit (mis. Manggarai) ada di banyak jalur dan user melihat salah satunya di peta.
     */
    suspend operator fun invoke(mode: TransitMode, lat: Double, lng: Double, preferredLineId: String? = null): Station? {
        val lines = stations.lines(mode)
        val perLine = lines.associate { it.id to stations.stationsInLine(it.id) }
        val radius = matchRadiusMeters(mode)
        preferredLineId?.let { id -> nearestStation(perLine[id].orEmpty(), lat, lng, radius)?.let { return it } }
        return nearestStation(perLine.values.flatten(), lat, lng, radius)
    }
}

/** Halte bus ~400 m satu sama lain, jadi radiusnya sempit; stasiun rel berjauhan dan peronnya besar. */
fun matchRadiusMeters(mode: TransitMode): Double = when (mode) {
    TransitMode.TRANSJAKARTA -> 120.0
    TransitMode.KRL, TransitMode.MRT, TransitMode.LRT -> 300.0
}

/** Stasiun terdekat dari ([lat], [lng]) dalam [radiusMeters], atau null. */
fun nearestStation(candidates: List<Station>, lat: Double, lng: Double, radiusMeters: Double): Station? =
    candidates.minByOrNull { distanceMeters(it.lat, it.lng, lat, lng) }
        ?.takeIf { distanceMeters(it.lat, it.lng, lat, lng) <= radiusMeters }

/** Jarak datar lokal; di Jabodetabek galatnya jauh di bawah radius pencocokan. */
private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val k = 111_194.93
    return hypot((lat1 - lat2) * k, (lng1 - lng2) * k * cos((lat1 + lat2) / 2 * PI / 180.0))
}
