package id.haonlabs.wakemate.data.live

import id.haonlabs.wakemate.domain.live.HalteEta
import id.haonlabs.wakemate.domain.live.LiveBus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Payload topic `/mobile_armada/{operator}/{rute}/{trip}/{bus}`. Feed ini tidak terdokumentasi,
 * jadi hanya field yang dipakai yang dibaca, dan semuanya diberi default: field yang suatu hari
 * hilang cukup membuat satu bus terlewat, bukan seluruh feed gagal di-parse.
 */
@Serializable
private data class ArmadaPayload(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val bearing: Double = 0.0,
    @SerialName("route_code") val routeCode: String = "",
    @SerialName("trip_id") val tripId: String = "",
    @SerialName("trip_headsign") val headsign: String = "",
    @SerialName("bus_type") val busType: String = "",
    @SerialName("bus_body_no") val busNo: String = "",
    val timestamp: Long = 0,
    val stops: List<ArmadaStop> = emptyList(),
)

@Serializable
private data class ArmadaStop(
    @SerialName("parent_stop_id") val parentStopId: String = "",
    @SerialName("stop_id") val stopId: String = "",
    // Halte terdekat kadang tanpa eta sama sekali, kadang string kosong.
    val eta: JsonElement? = null,
)

private val json = Json { ignoreUnknownKeys = true }

/** null kalau payload rusak atau tidak punya identitas/posisi bus. */
fun parseArmada(payload: String, receivedAt: Long): LiveBus? {
    val p = runCatching { json.decodeFromString<ArmadaPayload>(payload) }.getOrNull() ?: return null
    if (p.busNo.isBlank() || p.routeCode.isBlank()) return null
    val lat = p.latitude ?: return null
    val lng = p.longitude ?: return null
    val remaining = p.stops
        .map { HalteEta(it.parentStopId.ifBlank { it.stopId }, (it.eta as? JsonPrimitive)?.intOrNull) }
        .filter { it.halteId.isNotBlank() }
        // Satu halte induk bisa punya beberapa gate berurutan; yang dihitung tetap satu halte.
        .fold(mutableListOf<HalteEta>()) { acc, stop ->
            if (acc.lastOrNull()?.halteId != stop.halteId) acc += stop
            acc
        }
    return LiveBus(
        busNo = p.busNo,
        routeId = p.routeCode,
        tripId = p.tripId,
        headsign = p.headsign,
        busType = p.busType,
        lat = lat,
        lng = lng,
        bearing = p.bearing,
        updatedAt = if (p.timestamp > 0) p.timestamp else receivedAt,
        remaining = remaining,
    )
}
