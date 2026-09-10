package id.haonlabs.wakemate.data.seed

import id.haonlabs.wakemate.domain.model.TransitMode
import kotlinx.serialization.Serializable

/** Bentuk `assets/stations_seed.json`, dihasilkan oleh `tools/generate_stations_seed.py`. */
@Serializable
data class SeedFile(
    val version: Int,
    val modes: List<SeedMode>,
)

@Serializable
data class SeedMode(
    val mode: TransitMode,
    val lines: List<SeedLine>,
)

@Serializable
data class SeedLine(
    val id: String,
    val name: String,
    val stations: List<SeedStation>,
)

@Serializable
data class SeedStation(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val sequence: Int,
)
