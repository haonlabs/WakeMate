package id.haonlabs.wakemate.domain.live

import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot

/** Moda untuk filter "Sekitar Saya". BUS = semua jenis bus Transjakarta (BRT, integrasi, mikrotrans, ...). */
enum class NearbyMode(val label: String) { BUS("Bus"), KRL("KRL"), MRT("MRT"), LRT("LRT") }

/** Moda sebuah halte/stasiun dari awalan id-nya (lihat tools/generate_rail_static.py); selebihnya bus. */
fun Halte.nearbyMode(): NearbyMode = when {
    id.startsWith("krl:") -> NearbyMode.KRL
    id.startsWith("mrt:") -> NearbyMode.MRT
    id.startsWith("lrtj:") || id.startsWith("lrtjbd:") -> NearbyMode.LRT
    else -> NearbyMode.BUS
}

/** Kecepatan jalan kaki rata-rata untuk perkiraan waktu tempuh ke halte. */
const val WALK_METERS_PER_MINUTE = 80.0

data class NearbyStop(
    val halte: Halte,
    val mode: NearbyMode,
    val distanceMeters: Double,
    /** Rute yang melewati halte ini. */
    val routes: List<TjRoute>,
) {
    val walkMinutes: Int get() = ceil(distanceMeters / WALK_METERS_PER_MINUTE).toInt().coerceAtLeast(1)
}

/**
 * Halte/stasiun dalam [radiusMeters] dari ([lat], [lng]), terdekat dulu, maksimal [limit]. [mode] = null
 * berarti semua moda. Halte tanpa rute satu pun (data yatim) dibuang.
 */
fun nearbyStops(
    net: TjNetwork,
    lat: Double,
    lng: Double,
    radiusMeters: Double,
    mode: NearbyMode? = null,
    limit: Int = 30,
): List<NearbyStop> {
    // Kotak kasar dulu: ribuan halte TJ tidak perlu semuanya dihitung jaraknya.
    val dLat = radiusMeters / METERS_PER_DEGREE
    val dLng = dLat / cos(lat * PI / 180.0)
    return net.halte.values.asSequence()
        .filter { kotlin.math.abs(it.lat - lat) <= dLat && kotlin.math.abs(it.lng - lng) <= dLng }
        .map { it to distanceMeters(lat, lng, it.lat, it.lng) }
        .filter { (_, d) -> d <= radiusMeters }
        .filter { (h, _) -> mode == null || h.nearbyMode() == mode }
        .sortedBy { it.second }
        .map { (h, d) -> NearbyStop(h, h.nearbyMode(), d, net.routesAt(h.id)) }
        .filter { it.routes.isNotEmpty() }
        .take(limit)
        .toList()
}

/** Satu baris "rute yang melewati": rute + tujuan, dan kapan kedatangan berikutnya (null = tidak ada jadwal). */
data class RouteDeparture(val route: TjRoute, val headsign: String, val minutes: Int?)

/**
 * Kedatangan berikutnya di [halteId] untuk tiap pasangan rute+tujuan, tercepat dulu; yang tanpa jadwal
 * di belakang. Trip yang berakhir di halte ini tidak dihitung (kereta/bus itu tidak membawa penumpang
 * lanjut). Jadwal tetap yang sudah kadaluarsa ([TjNetwork.fixedScheduleValidUntil]) tidak ditampilkan.
 */
fun departuresAt(net: TjNetwork, halteId: String, now: ZonedDateTime): List<RouteDeparture> {
    val expired = net.fixedScheduleValidUntil?.let { now.toLocalDate().isAfter(it) } == true
    val trips = net.tripsByHalte[halteId].orEmpty().filter { it.halteIds.indexOf(halteId) < it.halteIds.lastIndex }
    val order = net.routes.withIndex().associate { (i, r) -> r.id to i }
    return trips.groupBy { it.routeId to it.headsign }
        .mapNotNull { (key, group) ->
            val route = net.routeById[key.first] ?: return@mapNotNull null
            val minutes = if (expired && route.kind == "KRL") null else scheduleEtas(group, halteId, now, 1).firstOrNull()
            RouteDeparture(route, key.second, minutes)
        }
        .sortedWith(compareBy<RouteDeparture, Int?>(nullsLast()) { it.minutes }.thenBy { order[it.route.id] ?: Int.MAX_VALUE }.thenBy { it.headsign })
}

private const val METERS_PER_DEGREE = 111_194.93

private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
    hypot((lat1 - lat2) * METERS_PER_DEGREE, (lng1 - lng2) * METERS_PER_DEGREE * cos((lat1 + lat2) / 2 * PI / 180.0))
