package id.haonlabs.wakemate.data.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.domain.live.Halte
import id.haonlabs.wakemate.domain.live.TjNetwork
import id.haonlabs.wakemate.domain.live.TjRoute
import id.haonlabs.wakemate.domain.live.TjTrip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rute, halte, dan trip dari assets/tj_static.json (lihat tools/generate_tj_static.py). Dibaca
 * sekali lalu disimpan di memori: datanya hanya-baca dan cuma ~1 MB, jadi tidak perlu Room.
 */
@Singleton
class TjStaticSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cached: TjNetwork? = null

    suspend fun network(): TjNetwork = cached ?: mutex.withLock {
        cached ?: load().also { cached = it }
    }

    private suspend fun load(): TjNetwork = withContext(Dispatchers.IO) {
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val file = json.decodeFromString<StaticFile>(text)
        TjNetwork(
            routes = file.routes
                .map { TjRoute(it.id, it.short, it.long, it.kind, it.color, it.text) }
                .sortedWith(routeOrder),
            halte = file.halte.mapValues { (id, row) ->
                Halte(id, row[0].jsonPrimitive.content, row[1].jsonPrimitive.double, row[2].jsonPrimitive.double)
            },
            trips = file.trips.map { TjTrip(it.id, it.route, it.head, it.name, it.stops, it.shape) },
        )
    }

    @Serializable
    private data class StaticFile(
        val routes: List<RouteRow>,
        /** id -> [nama, lat, lng]; dibuat ringkas karena jumlahnya ribuan. */
        val halte: Map<String, JsonArray>,
        val trips: List<TripRow>,
    )

    @Serializable
    private data class RouteRow(
        val id: String,
        val short: String,
        val long: String,
        val kind: String,
        val color: String,
        val text: String,
    )

    @Serializable
    private data class TripRow(
        val id: String,
        val route: String,
        val head: String,
        val name: String,
        val stops: List<String>,
        val shape: String,
    )

    private companion object {
        const val ASSET = "tj_static.json"
        val json = Json { ignoreUnknownKeys = true }

        /** Koridor BRT paling sering dicari, jadi di atas; dalam satu jenis urut angka alami (2 < 10). */
        val KIND_ORDER = listOf("BRT", "Angkutan Umum Integrasi", "Mikrotrans", "Transjabodetabek")

        val routeOrder = compareBy<TjRoute>(
            { KIND_ORDER.indexOf(it.kind).let { i -> if (i < 0) KIND_ORDER.size else i } },
            { naturalKey(it.shortName).first },
            { naturalKey(it.shortName).second },
            { naturalKey(it.shortName).third },
        )

        /** "JAK.31" -> ("JAK.", 31, ""), "1F" -> ("", 1, "F"). */
        fun naturalKey(code: String): Triple<String, Int, String> {
            val start = code.indexOfFirst(Char::isDigit)
            if (start < 0) return Triple(code, Int.MAX_VALUE, "")
            val end = (start until code.length).firstOrNull { !code[it].isDigit() } ?: code.length
            return Triple(code.substring(0, start), code.substring(start, end).toInt(), code.substring(end))
        }
    }
}
