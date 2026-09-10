package id.haonlabs.wakemate.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.domain.model.TransitMode
import id.haonlabs.wakemate.domain.proximity.GeoPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingRequest(val intervalMs: Long, val priority: Int)

/**
 * Seberapa sering dan seakurat apa lokasi diminta (PRD §7.2). Hemat baterainya dicari dari
 * *interval* yang jarang saat masih jauh, bukan dari sensor yang lebih kasar — kecuali kalau
 * jarak antar pemberhentian memang cukup lebar untuk memaafkan galatnya.
 */
object TrackingProfile {

    /**
     * PRIORITY_BALANCED_POWER_ACCURACY sama sekali tidak menyalakan GPS: posisinya ditebak dari
     * jaringan, dengan galat ratusan meter. Itu masih aman untuk KRL/MRT yang antar stasiunnya
     * 1–5 km, tapi tidak untuk halte Transjakarta yang cuma ~400 m terpisah — di sana galat sebesar
     * itu akan melampaui toleransi off-route dan memicu peringatan "salah line" palsu.
     */
    fun far(mode: TransitMode) = when (mode) {
        TransitMode.TRANSJAKARTA -> TrackingRequest(FAR_INTERVAL_MS, Priority.PRIORITY_HIGH_ACCURACY)
        else -> TrackingRequest(FAR_INTERVAL_MS, Priority.PRIORITY_BALANCED_POWER_ACCURACY)
    }

    fun near(): TrackingRequest = TrackingRequest(NEAR_INTERVAL_MS, Priority.PRIORITY_HIGH_ACCURACY)

    private const val FAR_INTERVAL_MS = 45_000L
    private const val NEAR_INTERVAL_MS = 8_000L
}

/** Wrapper tipis di atas FusedLocationProviderClient, dipakai `TripTrackingService`. */
@Singleton
class LocationTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    // Mulai dari mode akurat supaya fix pertama datang cepat; sesi turun ke mode hemat begitu
    // ketahuan masih jauh dari tujuan.
    private val request = MutableStateFlow(TrackingProfile.near())

    fun setRequest(value: TrackingRequest) {
        request.value = value
    }

    /**
     * Ganti profil otomatis me-restart request lewat flatMapLatest, jadi pemanggil cukup collect
     * satu Flow saja sepanjang sesi.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locations: Flow<GeoPoint> = request.flatMapLatest { requestUpdates(it) }

    @SuppressLint("MissingPermission") // Dipanggil hanya dari service yang izinnya sudah dicek.
    private fun requestUpdates(tracking: TrackingRequest): Flow<GeoPoint> = callbackFlow {
        val request = LocationRequest.Builder(tracking.priority, tracking.intervalMs)
            .setMinUpdateIntervalMillis(tracking.intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
