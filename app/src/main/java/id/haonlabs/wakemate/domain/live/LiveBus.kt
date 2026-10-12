package id.haonlabs.wakemate.domain.live

import kotlinx.serialization.Serializable

/** Satu halte di depan bus beserta ETA dari feed (menit). ETA bisa kosong untuk halte terdekat. */
data class HalteEta(val halteId: String, val etaMin: Int?)

/** Posisi satu bus menurut feed live MQTT Transjakarta. */
data class LiveBus(
    val busNo: String,
    val routeId: String,
    val tripId: String,
    val headsign: String,
    val busType: String,
    val lat: Double,
    val lng: Double,
    val bearing: Double,
    /** Waktu posisi menurut feed (epoch ms). */
    val updatedAt: Long,
    /** Semua halte yang belum dilewati, urut. Halte pertama = pemberhentian berikutnya. */
    val remaining: List<HalteEta>,
)

/** Seberapa dekat sebuah bus ke satu halte. [stopsAway] 1 berarti halte itu pemberhentian berikutnya. */
data class Approach(val bus: LiveBus, val stopsAway: Int, val etaMin: Int?)

/** Ambang alarm live: dihitung dalam halte atau menit sebelum sampai. */
@Serializable
sealed interface LiveLead {
    @Serializable
    data class Stops(val count: Int) : LiveLead

    @Serializable
    data class Minutes(val minutes: Int) : LiveLead
}

fun approachOf(bus: LiveBus, halteId: String): Approach? {
    val index = bus.remaining.indexOfFirst { it.halteId == halteId }
    if (index < 0) return null
    return Approach(bus, stopsAway = index + 1, etaMin = bus.remaining[index].etaMin)
}

/**
 * Bus mana yang paling dulu tiba di [halteId]. Diurutkan per ETA; bus tanpa ETA kalah dari bus
 * yang punya, lalu dibandingkan dari jumlah halte.
 */
fun nextArrival(buses: Collection<LiveBus>, halteId: String): Approach? =
    buses.mapNotNull { approachOf(it, halteId) }
        .minWithOrNull(compareBy<Approach, Int?>(nullsLast()) { it.etaMin }.thenBy { it.stopsAway })

/** "3 halte lagi · ±7 mnt" — dipakai bersama notifikasi dan layar supaya angkanya sama persis. */
fun Approach.summary(): String {
    val stops = if (stopsAway <= 1) "halte berikutnya" else "$stopsAway halte lagi"
    return etaMin?.let { "$stops · ±$it mnt" } ?: stops
}

fun LiveLead.label(): String = when (this) {
    is LiveLead.Stops -> if (count == 1) "1 halte sebelumnya" else "$count halte sebelumnya"
    is LiveLead.Minutes -> "$minutes menit sebelumnya"
}

fun Approach.hasReached(lead: LiveLead): Boolean = when (lead) {
    is LiveLead.Stops -> stopsAway <= lead.count
    // Tanpa ETA, jangan diam saja kalau bus sudah tepat di depan halte.
    is LiveLead.Minutes -> etaMin?.let { it <= lead.minutes } ?: (stopsAway <= 1)
}
