package id.haonlabs.wakemate.domain.live

import java.time.ZonedDateTime

/**
 * Posisi kereta **simulasi**: dihitung dari jadwal, BUKAN posisi nyata. Tidak ada sumber terbuka
 * untuk posisi live KRL; yang ada hanya jadwal, jadi kereta digerakkan seolah mengikuti jadwal
 * persis (polanya sama dengan trafik.live: interpolasi antar stasiun menurut jam tiba).
 *
 * UI wajib melabelinya "menurut jadwal" — jangan pernah menyebutnya live.
 */
data class SimTrain(
    val tripId: String,
    val headsign: String,
    val lat: Double,
    val lng: Double,
    /** Id stasiun kalau kereta sedang tepat di stasiun (awal/akhir atau jam tiba), selain itu null. */
    val atHalteId: String?,
)

/** Kereta sudah tampil berdiri di stasiun awal sekian detik sebelum jadwal berangkat. */
private const val STANDING_BEFORE_SECONDS = 120

/** Dan masih tampil di stasiun akhir sekian detik setelah tiba. */
private const val STANDING_AFTER_SECONDS = 60

private const val SECONDS_PER_DAY = 86_400

/**
 * Kereta jadwal-tetap (trip tanpa `freqs`) yang sedang berjalan pada [now]. Antar stasiun posisinya
 * garis lurus; jalur KRL di data memang garis lurus antar stasiun, jadi kereta tepat di atas garis.
 * Trip berfrekuensi (Transjakarta) diabaikan: posisinya tidak bisa diturunkan dari jadwal.
 */
fun simulatedTrains(trips: Collection<TjTrip>, halte: Map<String, Halte>, now: ZonedDateTime): List<SimTrain> {
    val nowSec = now.toLocalTime().toSecondOfDay()
    val day = now.dayOfWeek.value
    val yesterday = if (day == 1) 7 else day - 1
    val out = ArrayList<SimTrain>()
    for (trip in trips) {
        if (trip.freqs.isNotEmpty() || trip.arrivals.size != trip.halteIds.size || trip.arrivals.size < 2) continue
        // Hari layanan yang sedang berjalan: hari ini, atau kemarin untuk KA yang jamnya lewat 24:00.
        val train = (if (trip.runsOn(day)) positionAt(trip, halte, nowSec) else null)
            ?: (if (trip.runsOn(yesterday)) positionAt(trip, halte, nowSec + SECONDS_PER_DAY) else null)
        if (train != null) out += train
    }
    return out
}

private fun positionAt(trip: TjTrip, halte: Map<String, Halte>, seconds: Int): SimTrain? {
    val arr = trip.arrivals
    val ids = trip.halteIds
    if (seconds < arr.first() - STANDING_BEFORE_SECONDS || seconds > arr.last() + STANDING_AFTER_SECONDS) return null
    fun at(index: Int): SimTrain? = halte[ids[index]]?.let { SimTrain(trip.id, trip.headsign, it.lat, it.lng, it.id) }
    if (seconds <= arr.first()) return at(0)
    if (seconds >= arr.last()) return at(ids.lastIndex)
    val i = arr.indexOfLast { it <= seconds }
    val from = halte[ids[i]] ?: return null
    val to = halte[ids[i + 1]] ?: return null
    val span = arr[i + 1] - arr[i]
    val f = if (span <= 0) 0.0 else (seconds - arr[i]).toDouble() / span
    return SimTrain(
        tripId = trip.id,
        headsign = trip.headsign,
        lat = from.lat + (to.lat - from.lat) * f,
        lng = from.lng + (to.lng - from.lng) * f,
        atHalteId = from.id.takeIf { seconds == arr[i] },
    )
}
