package id.haonlabs.wakemate.domain

import id.haonlabs.wakemate.domain.model.AlertThreshold
import id.haonlabs.wakemate.domain.model.hasReached
import id.haonlabs.wakemate.domain.proximity.TravelDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Memutar ulang geometri jalur asli dari OpenStreetMap menembus ProximityCalculator, dengan galat
 * GPS buatan yang bisa diatur.
 *
 * Ini satu-satunya test yang menilai kalkulator terhadap bentuk jalur yang sebenarnya: model
 * internalnya memakai garis lurus antar stasiun, sedangkan yang diputar di sini lengkung rel dan
 * koridor aslinya. Jadi selisih keduanya terukur, bukan terasumsikan.
 *
 * Sigma adalah simpangan baku galat GPS dalam meter. 30 m mewakili kondisi langit terbuka yang
 * wajar, 60 m mewakili di antara gedung tinggi. Di atas itu alat ukurnya sendiri sudah tidak bisa
 * dipercaya, jadi tidak dijadikan syarat lulus.
 */
class TrackReplayTest {

    private data class Ride(
        val lineId: String,
        val speedMps: Double,
        val destinationIndex: Int,
    )

    private val rides = listOf(
        Ride("krl-bogor", speedMps = 16.7, destinationIndex = 23),
        Ride("mrt-north-south", speedMps = 9.7, destinationIndex = 12),
        Ride("tj-koridor-1", speedMps = 4.7, destinationIndex = 19),
    )

    private fun Ride.replay(sigma: Double, from: Int = 0, to: Int = destinationIndex) =
        RideSimulator(loadTrack(lineId)).ride(
            destinationIndex = to,
            fromIndex = from,
            speedMps = speedMps,
            sampleSeconds = SAMPLE_SECONDS,
            noiseSigmaMeters = sigma,
        )

    @Test
    fun `jalur asli tidak pernah dikira keluar rute`() {
        forEachRideAndSigma { ride, sigma, samples ->
            val worst = samples.maxOf { it.state.offRouteMeters }
            val tolerance = loadTrack(ride.lineId).mode.offRouteToleranceMeters
            assertTrue(
                "${ride.lineId} sigma=$sigma: simpangan maksimum ${worst.toInt()} m " +
                    "melewati toleransi ${tolerance.toInt()} m",
                samples.none { it.state.isOffRoute },
            )
            // Sisa ruang untuk galat GPS yang lebih buruk dari yang disimulasikan.
            assertTrue(
                "${ride.lineId} sigma=$sigma: sisa ruang cuma ${(tolerance - worst).toInt()} m",
                tolerance - worst > 200.0,
            )
        }
    }

    @Test
    fun `perjalanan satu arah tidak pernah dilaporkan menjauh`() {
        forEachRideAndSigma { ride, sigma, samples ->
            val wrong = samples.count { it.state.direction == TravelDirection.AWAY_FROM_DESTINATION }
            assertEquals("${ride.lineId} sigma=$sigma", 0, wrong)
        }
    }

    @Test
    fun `alert menyala saat benar-benar tersisa dua pemberhentian`() {
        val threshold = AlertThreshold.StationCount(2)
        forEachRideAndSigma { ride, sigma, samples ->
            val firedAt = samples.indexOfFirst { it.state.hasReached(threshold) }
            assertTrue("${ride.lineId} sigma=$sigma: alert tidak pernah menyala", firedAt >= 0)
            assertEquals(2, samples[firedAt].state.stationsRemaining)
            assertTrue(
                "${ride.lineId} sigma=$sigma: alert menyala setelah tiba, sudah telat",
                samples[firedAt].actualRemainingMeters > 0,
            )
            // Update sebelumnya belum boleh memenuhi ambang — kalau iya berarti terlalu dini.
            assertTrue(firedAt > 0 && !samples[firedAt - 1].state.hasReached(threshold))
        }
    }

    /**
     * Titik jalur terdekat dari sebuah peron tidak persis berimpit dengan koordinat stasiunnya,
     * jadi di akhir perjalanan hitungan boleh masih menunjukkan 1. Yang menentukan sesi ditutup
     * adalah `hasArrived`, bukan hitungan itu.
     */
    @Test
    fun `sisa stasiun habis dan tujuan dinyatakan tercapai`() {
        forEachRideAndSigma { ride, sigma, samples ->
            assertEquals(
                "${ride.lineId} sigma=$sigma",
                ride.destinationIndex, samples.first().state.stationsRemaining,
            )
            assertTrue("${ride.lineId} sigma=$sigma", samples.last().state.stationsRemaining <= 1)
            assertTrue("${ride.lineId} sigma=$sigma: tujuan tidak dinyatakan tercapai",
                samples.last().state.hasArrived)
            assertTrue("${ride.lineId} sigma=$sigma", samples.all { it.state.stationsRemaining >= 0 })
        }
    }

    @Test
    fun `jarak sisa yang dilaporkan mendekati jarak sebenarnya saat alert`() {
        val threshold = AlertThreshold.StationCount(2)
        forEachRideAndSigma { ride, sigma, samples ->
            val fired = samples.first { it.state.hasReached(threshold) }
            val error = abs(fired.state.distanceToDestinationMeters - fired.actualRemainingMeters)
            assertTrue(
                "${ride.lineId} sigma=$sigma: melenceng ${error.toInt()} m dari " +
                    "${fired.actualRemainingMeters.toInt()} m sebenarnya",
                error < fired.actualRemainingMeters * 0.15,
            )
        }
    }

    @Test
    fun `arah sebaliknya di line yang sama juga terhitung benar`() {
        rides.forEach { ride ->
            val samples = ride.replay(sigma = SIGMA_URBAN, from = ride.destinationIndex, to = 0)

            assertEquals(ride.lineId, ride.destinationIndex, samples.first().state.stationsRemaining)
            assertTrue(ride.lineId, samples.last().state.hasArrived)
            assertTrue(ride.lineId, samples.none { it.state.isOffRoute })
            assertEquals(
                ride.lineId, 0,
                samples.count { it.state.direction == TravelDirection.AWAY_FROM_DESTINATION },
            )
        }
    }

    /**
     * Mengunci galat pendekatan garis-lurus terhadap jalur asli. Kalau data seed atau fixture
     * diperbarui dan angka ini melonjak, toleransi off-route tiap moda perlu ditinjau ulang.
     */
    @Test
    fun `galat model garis lurus tetap di kisaran yang sudah diukur`() {
        val measured = mapOf(
            "krl-bogor" to 783.0,      // Lin Bogor paling berkelok, jadi paling meleset
            "mrt-north-south" to 256.0,
            "tj-koridor-1" to 251.0,
        )
        rides.forEach { ride ->
            val worst = ride.replay(sigma = 0.0).maxOf { it.state.offRouteMeters }
            val expected = measured.getValue(ride.lineId)
            assertTrue(
                "${ride.lineId}: galat ${worst.toInt()} m, sebelumnya ${expected.toInt()} m",
                worst <= expected * 1.1,
            )
        }
    }

    /**
     * ponytail: pada galat GPS ekstrem, hitungan sisa stasiun sesekali naik lagi sesaat karena
     * posisi berjitter di sekitar peron — terukur ~5% update di Koridor 1 pada sigma 100 m, dan
     * nyaris tidak pernah pada sigma wajar. Tidak diperhalus karena tidak memengaruhi alert
     * (`hasAlerted` mengunci setelah menyala) dan setiap penghalusan menambah state yang justru
     * bisa mengunci lonjakan noise. Tambahkan histeresis kalau uji lapangan menunjukkan kedipannya
     * mengganggu.
     */
    @Test
    fun `kedipan hitungan stasiun tetap jarang pada galat GPS wajar`() {
        forEachRideAndSigma { ride, sigma, samples ->
            val backwards = samples.map { it.state.stationsRemaining }
                .zipWithNext().count { (before, after) -> after > before }
            assertTrue(
                "${ride.lineId} sigma=$sigma: $backwards kedipan dari ${samples.size} update",
                backwards <= samples.size / 40,
            )
        }
    }

    private fun forEachRideAndSigma(
        check: (Ride, Double, List<RideSample>) -> Unit,
    ) = rides.forEach { ride ->
        listOf(SIGMA_PERFECT, SIGMA_OPEN_SKY, SIGMA_URBAN).forEach { sigma ->
            check(ride, sigma, ride.replay(sigma))
        }
    }

    private companion object {
        /** Meniru fase dekat service, saat alert benar-benar diputuskan. */
        const val SAMPLE_SECONDS = 10L
        const val SIGMA_PERFECT = 0.0
        const val SIGMA_OPEN_SKY = 30.0
        const val SIGMA_URBAN = 60.0
    }
}
