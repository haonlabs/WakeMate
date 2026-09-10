package id.haonlabs.wakemate.presentation.trip

import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.trip.ActiveTrip

enum class StopStatus { PASSED, CURRENT, WAKE_POINT, DESTINATION, UPCOMING }

data class RouteStop(val station: Station, val status: StopStatus)

/** Berapa stasiun yang sudah lewat tetap ditampilkan, sebagai penanda posisi. */
private const val PASSED_SHOWN = 2

/**
 * Menyusun potongan rute untuk ditampilkan: beberapa stasiun yang sudah lewat, posisi sekarang,
 * titik alarm, dan tujuan.
 *
 * Semuanya diturunkan dari data yang sudah ada — urutan stasiun di line, stasiun terdekat, dan
 * ambang alert. Tidak ada jadwal atau posisi kereta yang terlibat, jadi tidak ada yang dikarang.
 *
 * Arah perjalanan menentukan mana yang "sudah lewat": line yang sama dipakai dua arah, dan
 * urutannya di database hanya satu arah.
 */
internal fun routeStops(trip: ActiveTrip, route: List<Station>): List<RouteStop> {
    val nearest = trip.proximity?.nearestStation ?: return emptyList()
    val destinationIndex = route.indexOfFirst { it.id == trip.destination.id }
    val currentIndex = route.indexOfFirst { it.id == nearest.id }
    if (destinationIndex < 0 || currentIndex < 0) return emptyList()

    val forward = destinationIndex >= currentIndex
    val wakeIndex = (trip.session.threshold as? AlertThreshold.StationCount)
        ?.let { if (forward) destinationIndex - it.stations else destinationIndex + it.stations }
        ?.takeIf { it in route.indices && it != destinationIndex }

    val start = if (forward) {
        (currentIndex - PASSED_SHOWN).coerceAtLeast(0)
    } else {
        (currentIndex + PASSED_SHOWN).coerceAtMost(route.lastIndex)
    }
    val window = if (forward) start..destinationIndex else start downTo destinationIndex

    return window.map { index ->
        RouteStop(
            station = route[index],
            status = when {
                index == destinationIndex -> StopStatus.DESTINATION
                index == currentIndex -> StopStatus.CURRENT
                index == wakeIndex -> StopStatus.WAKE_POINT
                if (forward) index < currentIndex else index > currentIndex -> StopStatus.PASSED
                else -> StopStatus.UPCOMING
            },
        )
    }
}
