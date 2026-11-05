package id.haonlabs.wakemate.domain.live

import java.time.ZonedDateTime

/**
 * Perkiraan tiba dari jadwal GTFS resmi, dipakai saat data live tidak tersedia — bukan pengganti
 * feed live, karena bus yang sedang di jalan tidak selalu mengikuti jadwal persis.
 *
 * Cara kerja, dua jenis trip:
 *  - Berfrekuensi (Transjakarta): menurut frequencies.txt, trip berangkat dari halte pertama di
 *    `mulai + n × headway` selama masih sebelum `selesai`. Tiba di halte mana pun punya selisih
 *    tetap dari halte pertama menurut stop_times, jadi waktunya cukup dijumlahkan:
 *    `instansi + (tiba halte − tiba halte awal)`.
 *  - Jadwal tetap (KRL, tanpa `freqs`): `arrivals` adalah jam tiba mutlak. Jam bisa melebihi
 *    24:00 untuk KA yang berangkat malam dan tiba lewat tengah malam; KA itu milik hari
 *    layanan kemarin.
 */

/**
 * Jadwal tiba berikutnya di [halteId] dari [trips] yang beroperasi pada hari [now], menit dari
 * [now] (dibulatkan ke atas, minimal 1). Naik (terdekat dulu), unik, maksimal [limit].
 */
fun scheduleEtas(
    trips: Collection<TjTrip>,
    halteId: String,
    now: ZonedDateTime,
    limit: Int = 3,
): List<Int> {
    val nowSec = now.toLocalTime().toSecondOfDay()
    val day = now.dayOfWeek.value
    val results = sortedSetOf<Int>()
    for (trip in trips) {
        // Data jadwal tidak sejajar dengan daftar halte — trip dari GTFS lawas. Abaikan.
        if (trip.arrivals.size != trip.halteIds.size) continue
        val index = trip.halteIds.indexOf(halteId)
        if (index < 0) continue
        val arrivalAt = trip.arrivals[index]
        if (trip.freqs.isEmpty()) {
            val yesterday = if (day == 1) 7 else day - 1
            if (trip.runsOn(day) && arrivalAt >= nowSec) results += minuteFromNow(arrivalAt - nowSec)
            if (trip.runsOn(yesterday) && arrivalAt - SECONDS_PER_DAY >= nowSec) results += minuteFromNow(arrivalAt - SECONDS_PER_DAY - nowSec)
            continue
        }
        if (!trip.runsOn(day)) continue
        val fromStart = trip.arrivals[0]
        for (freq in trip.freqs) {
            val headway = freq.headway
            if (headway <= 0) continue
            // Instansi yang sudah pasti kelewatan di halte ini tidak perlu dihitung satu per satu.
            val skipped = (nowSec - freq.start - (arrivalAt - fromStart)) / headway
            var departure = freq.start + maxOf(0, skipped) * headway
            while (departure < freq.end) {
                val arrival = departure + arrivalAt - fromStart
                if (arrival >= nowSec) results += minuteFromNow(arrival - nowSec)
                departure += headway
            }
        }
    }
    return results.take(limit)
}

private const val SECONDS_PER_DAY = 86_400

private fun minuteFromNow(seconds: Int): Int = ((seconds + 59) / 60).coerceAtLeast(1)
