package id.haonlabs.wakemate.domain.live

import kotlinx.serialization.Serializable

/**
 * Alarm yang digerakkan feed live, bukan GPS HP. Disimpan apa adanya (JSON) supaya bisa dipulihkan
 * kalau proses dibunuh sistem di tengah jalan.
 */
@Serializable
sealed interface LiveWatch {
    val routeId: String
    val halteId: String
    val halteName: String
    val lead: LiveLead
    val startedAt: Long

    /** User di dalam bus [busNo]; bangunkan sebelum bus sampai di [halteId]. */
    @Serializable
    data class Onboard(
        val busNo: String,
        val tripId: String,
        override val routeId: String,
        override val halteId: String,
        override val halteName: String,
        override val lead: LiveLead,
        override val startedAt: Long,
        /** Jarak (halte) ke tujuan saat alarm dipasang, untuk bar progres. Null pada data lama. */
        val initialStops: Int? = null,
    ) : LiveWatch

    /**
     * User menunggu di [halteId]; ingatkan saat bus rute [routeId] ke arah [tripIds] hampir tiba.
     * [tripIds] kosong berarti arah mana pun.
     */
    @Serializable
    data class Arrival(
        override val routeId: String,
        override val halteId: String,
        override val halteName: String,
        override val lead: LiveLead,
        override val startedAt: Long,
        val tripIds: Set<String> = emptySet(),
    ) : LiveWatch
}

sealed interface WatchVerdict {
    /** Belum waktunya. [approach] null kalau belum ada bus yang menuju halte itu. */
    data class Waiting(val approach: Approach?) : WatchVerdict

    /** Bunyikan. [approach] null berarti bus yang dinaiki sudah melewati tujuan atau ganti trip. */
    data class Alert(val approach: Approach?) : WatchVerdict
}

/**
 * Bus yang hilang dari feed selama ini dianggap tidak bisa dipantau lagi. Feed mengirim posisi
 * tiap bus kira-kira sekali setengah menit, jadi lima menit sudah jauh melewati jeda wajar.
 */
const val LIVE_LOST_MS = 5 * 60_000L

/**
 * Keputusan alarm dari satu potret bus di rute itu. Fungsi murni supaya bisa diuji tanpa
 * Android dan tanpa jaringan.
 */
fun evaluate(watch: LiveWatch, buses: Map<String, LiveBus>): WatchVerdict = when (watch) {
    is LiveWatch.Onboard -> {
        val bus = buses[watch.busNo]
        when {
            bus == null -> WatchVerdict.Waiting(null)
            // Bus sampai terminal lalu berbalik arah: tujuan sudah lewat, lebih baik bunyi
            // terlambat daripada tidak sama sekali.
            bus.tripId != watch.tripId -> WatchVerdict.Alert(null)
            else -> when (val approach = approachOf(bus, watch.halteId)) {
                null -> WatchVerdict.Alert(null)
                else -> if (approach.hasReached(watch.lead)) WatchVerdict.Alert(approach)
                else WatchVerdict.Waiting(approach)
            }
        }
    }

    is LiveWatch.Arrival -> {
        // Halte BRT dilewati dua arah; bus arah sebaliknya bukan bus yang ditunggu.
        val toward = if (watch.tripIds.isEmpty()) buses.values else buses.values.filter { it.tripId in watch.tripIds }
        val approach = nextArrival(toward, watch.halteId)
        if (approach != null && approach.hasReached(watch.lead)) WatchVerdict.Alert(approach)
        else WatchVerdict.Waiting(approach)
    }
}
