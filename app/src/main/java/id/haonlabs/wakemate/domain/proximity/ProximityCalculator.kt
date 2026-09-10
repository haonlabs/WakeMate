package id.haonlabs.wakemate.domain.proximity

import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

data class GeoPoint(val lat: Double, val lng: Double)

enum class TravelDirection { TOWARD_DESTINATION, AWAY_FROM_DESTINATION, UNKNOWN }

data class ProximityState(
    val nearestStation: Station,
    /** Sisa stasiun termasuk tujuan itu sendiri: 1 berarti tujuan adalah pemberhentian berikutnya. */
    val stationsRemaining: Int,
    val distanceToDestinationMeters: Double,
    val direction: TravelDirection,
    /** Jarak tegak lurus user ke rute line yang dipilih. */
    val offRouteMeters: Double,
    val isOffRoute: Boolean,
    /** True kalau posisi ini hasil estimasi (mis. di terowongan MRT), bukan fix GPS. */
    val isDeadReckoned: Boolean,
    val hasArrived: Boolean,
)

/**
 * Menghitung posisi user relatif terhadap urutan stasiun sebuah line (FR-5).
 *
 * Posisi diproyeksikan tegak lurus ke polyline rute, bukan diukur garis lurus ke tujuan, supaya
 * line yang berbelok atau koridor Transjakarta yang haltenya berdempetan tetap terhitung benar.
 * Proyeksi itu sekaligus memberi tiga hal sekaligus: progres sepanjang rute, sisa stasiun, dan
 * deteksi salah naik line.
 *
 * Bukan thread-safe — satu instance dimiliki satu sesi perjalanan (TripTrackingService).
 */
class ProximityCalculator(
    private val route: List<Station>,
    destinationStationId: String,
    private val mode: TransitMode,
) {
    init {
        require(route.size >= 2) { "Route needs at least 2 stations, got ${route.size}" }
    }

    private val destIndex = route.indexOfFirst { it.id == destinationStationId }.also {
        require(it >= 0) { "Destination $destinationStationId is not on this route" }
    }

    // ponytail: rute = garis lurus antar stasiun, bukan geometri rel sebenarnya. Jarak jadi sedikit
    // lebih pendek dari kenyataan di tikungan; cukup untuk ambang alert, ganti dengan polyline OSM
    // kalau nanti butuh ETA menit-akurat.
    private val flat = FlatRoute(route)
    private val xs = flat.xs
    private val ys = flat.ys
    private val segmentLength = flat.segmentLength
    private val cumulative = flat.cumulative
    private val destProgress = cumulative[destIndex]

    /** Meter sepanjang rute dari stasiun pertama, dari fix GPS terakhir. */
    private var progress = Double.NaN
    private var lastFixAtMs = 0L
    /** Bertanda: positif berarti bergerak searah urutan stasiun. Hanya untuk estimasi tanpa GPS. */
    private var speedMps = 0.0
    /** +1 searah urutan stasiun, -1 berlawanan, 0 selama belum ada perpindahan yang meyakinkan. */
    private var travelSign = 0.0
    /** Titik acuan arah; sengaja tidak digeser tiap fix, lihat [updateMotion]. */
    private var directionAnchor = Double.NaN

    fun onLocation(point: GeoPoint, timestampMs: Long): ProximityState {
        val px = flat.toX(point.lng)
        val py = flat.toY(point.lat)

        var offRoute = Double.MAX_VALUE
        var projected = 0.0
        for (i in 0 until route.size - 1) {
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val lengthSq = dx * dx + dy * dy
            val t = if (lengthSq == 0.0) 0.0 else
                (((px - xs[i]) * dx + (py - ys[i]) * dy) / lengthSq).coerceIn(0.0, 1.0)
            val distance = hypot(px - (xs[i] + t * dx), py - (ys[i] + t * dy))
            if (distance < offRoute) {
                offRoute = distance
                // Panjang segmen dipakai ulang, bukan sqrt(lengthSq): kalau keduanya beda 1 ULP,
                // titik yang persis di stasiun bisa jatuh sepersekian mikron sebelum stasiun itu
                // dan membuat stasiun yang sudah dilewati tetap terhitung di depan.
                projected = cumulative[i] + t * segmentLength[i]
            }
        }

        updateMotion(projected, timestampMs)
        progress = projected
        lastFixAtMs = timestampMs
        return stateAt(projected, offRoute, deadReckoned = false)
    }

    /**
     * FR-11: saat GPS hilang (terowongan MRT), teruskan estimasi dari kecepatan terakhir supaya
     * hitungan sisa stasiun tidak beku. Mengembalikan null kalau belum ada fix, belum cukup lama
     * dianggap hilang, atau sudah terlalu lama untuk dipercaya.
     */
    fun onNoFix(nowMs: Long): ProximityState? {
        if (progress.isNaN() || abs(speedMps) < MIN_MOVING_MPS) return null
        val staleSeconds = (nowMs - lastFixAtMs) / 1000.0
        if (staleSeconds < STALE_AFTER_SECONDS || staleSeconds > MAX_DEAD_RECKON_SECONDS) return null
        val predicted = (progress + speedMps * staleSeconds).coerceIn(0.0, cumulative.last())
        return stateAt(predicted, offRouteMeters = 0.0, deadReckoned = true)
    }

    private fun updateMotion(newProgress: Double, timestampMs: Long) {
        if (progress.isNaN()) return
        val elapsed = (timestampMs - lastFixAtMs) / 1000.0
        if (elapsed <= 0.0) return
        val moved = newProgress - progress
        val observed = moved / elapsed
        // Lompatan GPS (mis. keluar terowongan) akan mengarang kecepatan; abaikan saja.
        if (abs(observed) > MAX_PLAUSIBLE_SPEED_MPS) return
        speedMps = if (speedMps == 0.0) observed else SMOOTHING * observed + (1 - SMOOTHING) * speedMps

        // Arah dibaca dari perpindahan mentah, bukan dari kecepatan yang sudah dihaluskan: EMA
        // butuh beberapa update untuk membalik tanda, dan peringatan "kamu menjauh" yang telat
        // satu-dua update persis kehilangan gunanya.
        //
        // Yang dibandingkan adalah jarak dari titik acuan yang menetap, bukan dari fix sebelumnya.
        // Transjakarta hanya menempuh ~47 m antar fix sementara galat GPS menggeser ~85 m, jadi
        // selisih antar dua fix berurutan lebih banyak berisi noise daripada perjalanan — replay
        // jalur asli menunjukkan 16% update salah dilaporkan "menjauh" dengan cara itu. Acuan baru
        // dipasang hanya setelah benar-benar berpindah sejauh ambang, sehingga perjalanan nyata
        // selalu mendominasi galat.
        if (directionAnchor.isNaN()) directionAnchor = progress
        val fromAnchor = newProgress - directionAnchor
        if (abs(fromAnchor) > DIRECTION_BASELINE_METERS) {
            travelSign = sign(fromAnchor)
            directionAnchor = newProgress
        }
    }

    private fun stateAt(at: Double, offRouteMeters: Double, deadReckoned: Boolean): ProximityState {
        val toDestination = destProgress - at
        // Hitung stasiun yang terletak antara posisi sekarang dan tujuan, arah mana pun.
        val remaining = route.indices.count { i ->
            val c = cumulative[i]
            if (toDestination >= 0) c > at && c <= destProgress else c < at && c >= destProgress
        }
        val nearest = route.indices.minBy { abs(cumulative[it] - at) }
        val distance = abs(toDestination)
        return ProximityState(
            nearestStation = route[nearest],
            stationsRemaining = remaining,
            distanceToDestinationMeters = distance,
            direction = directionAt(at),
            offRouteMeters = offRouteMeters,
            isOffRoute = offRouteMeters > mode.offRouteToleranceMeters,
            isDeadReckoned = deadReckoned,
            hasArrived = distance <= mode.arrivalRadiusMeters,
        )
    }

    private fun directionAt(at: Double): TravelDirection {
        val toDestination = destProgress - at
        if (travelSign == 0.0 || abs(toDestination) < 1.0) return TravelDirection.UNKNOWN
        return if (travelSign == sign(toDestination)) TravelDirection.TOWARD_DESTINATION
        else TravelDirection.AWAY_FROM_DESTINATION
    }

    private companion object {
        const val MIN_MOVING_MPS = 1.0
        /**
         * Cukup jauh agar perjalanan nyata mengalahkan galat GPS, cukup dekat agar putar balik
         * ketahuan dalam hitungan detik (~12 detik di KRL, ~40 detik di Transjakarta).
         */
        const val DIRECTION_BASELINE_METERS = 200.0
        const val MAX_PLAUSIBLE_SPEED_MPS = 45.0 // ~160 km/jam, di atas semua moda di sini
        const val SMOOTHING = 0.4
        const val STALE_AFTER_SECONDS = 20.0
        const val MAX_DEAD_RECKON_SECONDS = 15 * 60.0
    }
}
