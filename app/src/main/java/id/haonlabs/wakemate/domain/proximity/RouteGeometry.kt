package id.haonlabs.wakemate.domain.proximity

import id.haonlabs.wakemate.domain.model.Station
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Rute yang sudah diproyeksikan ke bidang datar lokal, berikut jarak kumulatifnya.
 *
 * Dipakai bersama oleh [ProximityCalculator] dan [routeDistanceMeters] supaya keduanya memakai
 * model jarak yang persis sama — kalau layar setup menjanjikan "1,9 km" sementara kalkulator
 * mengukur dengan cara lain, janji itu meleset tanpa ada yang tahu.
 *
 * Di Jabodetabek galat proyeksi datar jauh di bawah noise GPS, dan jauh lebih murah daripada
 * haversine di setiap segmen untuk tiap update lokasi.
 */
internal class FlatRoute(val route: List<Station>) {

    private val originLat = route[0].lat
    private val originLng = route[0].lng
    private val cosLat = cos(originLat * PI / 180.0)

    val xs = DoubleArray(route.size) { toX(route[it].lng) }
    val ys = DoubleArray(route.size) { toY(route[it].lat) }

    val segmentLength = DoubleArray(maxOf(route.size - 1, 0)) {
        hypot(xs[it + 1] - xs[it], ys[it + 1] - ys[it])
    }

    val cumulative = DoubleArray(route.size).also { cum ->
        for (i in 1 until route.size) cum[i] = cum[i - 1] + segmentLength[i - 1]
    }

    fun toX(lng: Double) = (lng - originLng) * METERS_PER_DEGREE * cosLat

    fun toY(lat: Double) = (lat - originLat) * METERS_PER_DEGREE

    companion object {
        /** Meter per derajat lintang pada bola berjari-jari rata-rata bumi. */
        const val METERS_PER_DEGREE = 111_194.93
    }
}

/**
 * Jarak sepanjang rute antara dua stasiun, arah mana pun.
 *
 * ponytail: mengikuti model garis lurus antar stasiun, sama seperti kalkulator, jadi sedikit lebih
 * pendek dari panjang rel sebenarnya di tikungan (terukur 2,4-4,8%). Cukup untuk memberi tahu user
 * seberapa jauh ambang yang dipilihnya; ganti dengan polyline OSM kalau nanti butuh ETA akurat.
 */
fun routeDistanceMeters(route: List<Station>, fromIndex: Int, toIndex: Int): Double {
    if (route.size < 2) return 0.0
    if (fromIndex !in route.indices || toIndex !in route.indices) return 0.0
    val cumulative = FlatRoute(route).cumulative
    return kotlin.math.abs(cumulative[toIndex] - cumulative[fromIndex])
}
