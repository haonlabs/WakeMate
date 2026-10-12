package id.haonlabs.wakemate.domain.live

/** Ke mana alarm "bangunkan sebelum halte X" akan jatuh, untuk dijelaskan sebelum dipasang. */
sealed interface WakePreview {
    /** Ambang sudah tercapai sekarang: alarm langsung berbunyi begitu dipasang. */
    data object Immediately : WakePreview

    /** Bunyi saat bus meninggalkan [halteId]; [minutesBefore] null kalau ETA feed tidak lengkap. */
    data class AtHalte(val halteId: String, val minutesBefore: Int?) : WakePreview

    /** Ambang menit: bunyi sekitar [minutes] menit sebelum tiba, di halte mana pun saat itu. */
    data class Minutes(val minutes: Int) : WakePreview
}

/**
 * Harus sejalan dengan [hasReached]: ambang `Stops(n)` tercapai saat tujuan tinggal n halte lagi,
 * yaitu tepat setelah bus meninggalkan halte ke-(d − n) dari daftar halte di depannya.
 */
fun wakePreview(bus: LiveBus, destinationId: String, lead: LiveLead): WakePreview? {
    val approach = approachOf(bus, destinationId) ?: return null
    if (approach.hasReached(lead)) return WakePreview.Immediately
    return when (lead) {
        is LiveLead.Minutes -> WakePreview.Minutes(lead.minutes)
        is LiveLead.Stops -> {
            val destIndex = approach.stopsAway - 1
            val wake = bus.remaining[destIndex - lead.count]
            val before = approach.etaMin?.let { dest -> wake.etaMin?.let { dest - it } }
            WakePreview.AtHalte(wake.halteId, before)
        }
    }
}
