package id.haonlabs.wakemate.domain

import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.proximity.GeoPoint
import id.haonlabs.wakemate.domain.proximity.ProximityCalculator
import id.haonlabs.wakemate.domain.proximity.ProximityState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Memutar ulang geometri jalur asli dari OpenStreetMap (`test/resources/tracks/`) menembus
 * [ProximityCalculator].
 *
 * Gunanya justru karena kedua sisi tidak sepakat: kalkulator memodelkan rute sebagai garis lurus
 * antar stasiun, sedangkan yang diputar di sini lengkung rel yang sebenarnya. Jadi test yang
 * memakainya mengukur galat pendekatan itu, bukan mengulang asumsinya sendiri.
 */
@Serializable
data class TrackFixture(
    val lineId: String,
    val mode: TransitMode,
    val stations: List<FixtureStation>,
    val track: List<List<Double>>,
)

@Serializable
data class FixtureStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val sequence: Int,
)

fun loadTrack(lineId: String): TrackFixture {
    val stream = checkNotNull(TrackFixture::class.java.getResourceAsStream("/tracks/$lineId.json")) {
        "Fixture /tracks/$lineId.json tidak ada — jalankan tools/generate_track_fixtures.py"
    }
    return Json { ignoreUnknownKeys = true }
        .decodeFromString<TrackFixture>(stream.bufferedReader().use { it.readText() })
}

fun TrackFixture.stationList(): List<Station> = stations.map {
    Station(it.id, it.name, mode, it.lat, it.lng, lineId, it.sequence)
}

/** Satu titik hasil simulasi berikut posisi sebenarnya, supaya test bisa menilai ketepatannya. */
data class RideSample(
    val state: ProximityState,
    /** Meter yang benar-benar sudah ditempuh di sepanjang jalur asli. */
    val travelledMeters: Double,
    /** Jarak sisa sebenarnya ke tujuan, diukur sepanjang jalur asli. */
    val actualRemainingMeters: Double,
)

class RideSimulator(private val fixture: TrackFixture) {

    private val stations = fixture.stationList()
    private val originLat = fixture.track[0][0]
    private val originLng = fixture.track[0][1]
    private val cosLat = cos(originLat * PI / 180.0)
    private val xs = DoubleArray(fixture.track.size) { toX(fixture.track[it][1]) }
    private val ys = DoubleArray(fixture.track.size) { toY(fixture.track[it][0]) }
    private val cumulative = DoubleArray(fixture.track.size).also { cum ->
        for (i in 1 until fixture.track.size) {
            cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        }
    }

    /**
     * @param sampleSeconds jeda antar fix, meniru interval adaptif service (8 s dekat, 45 s jauh).
     * @param noiseSigmaMeters simpangan baku galat GPS yang disuntikkan; 0 berarti koordinat sempurna.
     */
    fun ride(
        destinationIndex: Int,
        fromIndex: Int = 0,
        speedMps: Double,
        sampleSeconds: Long,
        noiseSigmaMeters: Double = 0.0,
        seed: Long = 42,
    ): List<RideSample> {
        val calculator = ProximityCalculator(stations, stations[destinationIndex].id, fixture.mode)
        val random = Random(seed)
        val start = cumulative[nearestTrackIndex(stations[fromIndex])]
        val end = cumulative[nearestTrackIndex(stations[destinationIndex])]
        val forward = end >= start
        val step = speedMps * sampleSeconds * if (forward) 1.0 else -1.0

        val samples = mutableListOf<RideSample>()
        var travelled = start
        var timestamp = 0L
        while (if (forward) travelled <= end else travelled >= end) {
            val (lat, lng) = pointAt(travelled, random, noiseSigmaMeters)
            samples += RideSample(
                state = calculator.onLocation(GeoPoint(lat, lng), timestamp),
                travelledMeters = travelled - start,
                actualRemainingMeters = kotlin.math.abs(end - travelled),
            )
            travelled += step
            timestamp += sampleSeconds * 1_000
        }
        // Panjang langkah jarang habis dibagi panjang rute, jadi tambahkan satu fix tepat di peron
        // tujuan — perjalanan sungguhan memang berakhir di sana, bukan 150 m sebelumnya.
        val (lat, lng) = pointAt(end, random, noiseSigmaMeters)
        samples += RideSample(
            state = calculator.onLocation(GeoPoint(lat, lng), timestamp),
            travelledMeters = end - start,
            actualRemainingMeters = 0.0,
        )
        return samples
    }

    /** Jarak tegak lurus tiap titik jalur asli ke model garis-lurus kalkulator. */
    fun chordErrorAt(sample: RideSample): Double = sample.state.offRouteMeters

    private fun pointAt(distance: Double, random: Random, sigma: Double): Pair<Double, Double> {
        val i = cumulative.indexOfLast { it <= distance }.coerceIn(0, cumulative.size - 2)
        val segment = cumulative[i + 1] - cumulative[i]
        val t = if (segment == 0.0) 0.0 else ((distance - cumulative[i]) / segment).coerceIn(0.0, 1.0)
        val x = xs[i] + t * (xs[i + 1] - xs[i]) + random.nextGaussian() * sigma
        val y = ys[i] + t * (ys[i + 1] - ys[i]) + random.nextGaussian() * sigma
        return toLat(y) to toLng(x)
    }

    private fun nearestTrackIndex(station: Station): Int {
        val sx = toX(station.lng)
        val sy = toY(station.lat)
        return xs.indices.minBy { hypot(xs[it] - sx, ys[it] - sy) }
    }

    private fun toX(lng: Double) = (lng - originLng) * METERS_PER_DEGREE * cosLat
    private fun toY(lat: Double) = (lat - originLat) * METERS_PER_DEGREE
    private fun toLng(x: Double) = originLng + x / (METERS_PER_DEGREE * cosLat)
    private fun toLat(y: Double) = originLat + y / METERS_PER_DEGREE

    private companion object {
        const val METERS_PER_DEGREE = 111_194.93
    }
}
