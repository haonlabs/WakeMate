package id.haonlabs.wakemate.data.location

import android.content.Context
import android.location.Location
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.BuildConfig
import id.haonlabs.wakemate.domain.trip.ActiveTrip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mencatat fix GPS mentah ke berkas CSV selama sesi, hanya di build debug.
 *
 * Ada karena seluruh tuning ProximityCalculator saat ini bertumpu pada galat GPS yang diasumsikan,
 * bukan diukur. Tanpa berkas ini, satu perjalanan sungguhan cuma menghasilkan kesan "alertnya pas"
 * atau "telat" — tidak ada yang bisa dimasukkan ke test. Dengan berkas ini, perjalanan yang sama
 * bisa diputar ulang lewat `TrackReplay` dan jadi regression test permanen.
 *
 * `accuracy` yang dilaporkan perangkat sengaja ikut dicatat: itu perkiraan sigma dari perangkat
 * sendiri, dan `GeoPoint` di domain membuangnya.
 *
 * Berkas ditulis ke direktori khusus aplikasi di penyimpanan eksternal supaya bisa ditarik dengan
 * `adb pull` tanpa root, dan ikut terhapus saat aplikasi di-uninstall.
 */
@Singleton
class LocationLog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var writer: java.io.Writer? = null

    @Synchronized
    fun start(trip: ActiveTrip) {
        if (!BuildConfig.DEBUG) return
        stop()
        runCatching {
            val directory = File(context.getExternalFilesDir(null), DIRECTORY).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(System.currentTimeMillis())
            val file = File(directory, "$stamp-${trip.session.lineId}.csv")
            writer = file.bufferedWriter().also {
                it.appendLine("# line=${trip.session.lineId} tujuan=${trip.destination.id} " +
                    "moda=${trip.session.mode} ambang=${trip.session.threshold}")
                it.appendLine("timestampMs,lat,lng,accuracyMeters,speedMps,provider,event")
                it.flush()
            }
            Log.i(TAG, "Mencatat fix ke ${file.absolutePath}")
        }.onFailure { Log.w(TAG, "Gagal membuka berkas catatan", it) }
    }

    @Synchronized
    fun record(location: Location) {
        val out = writer ?: return
        // Ditulis dan di-flush per fix: sesi yang berakhir buruk justru yang paling ingin dibaca,
        // dan buffer yang belum sempat tertulis akan hilang bersama prosesnya.
        runCatching {
            out.appendLine(
                "${location.time},${location.latitude},${location.longitude}," +
                    "${if (location.hasAccuracy()) location.accuracy else ""}," +
                    "${if (location.hasSpeed()) location.speed else ""},${location.provider ?: ""},"
            )
            out.flush()
        }.onFailure { stop() }
    }

    /** Menandai kejadian penting (alert menyala, sesi ditutup) di tengah deret fix. */
    @Synchronized
    fun event(label: String) {
        val out = writer ?: return
        runCatching {
            out.appendLine("${System.currentTimeMillis()},,,,,,$label")
            out.flush()
        }.onFailure { stop() }
    }

    @Synchronized
    fun stop() {
        runCatching { writer?.close() }
        writer = null
    }

    private companion object {
        const val TAG = "LocationLog"
        const val DIRECTORY = "gps-logs"
    }
}
