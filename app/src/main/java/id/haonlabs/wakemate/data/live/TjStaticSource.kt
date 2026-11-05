package id.haonlabs.wakemate.data.live

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.haonlabs.wakemate.domain.live.Frequency
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
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rute, halte, dan trip dari dua aset dengan skema sama, digabung jadi satu jaringan:
 * tj_static.json (Transjakarta, tools/generate_tj_static.py) dan rail_static.json (KRL, MRT, LRT,
 * tools/generate_rail_static.py). Dibaca sekali lalu disimpan di memori: datanya hanya-baca dan
 * ~1,5 MB, jadi tidak perlu Room. Id rute/halte rel berawalan "krl:", "mrt:", "lrtj:", "lrtjbd:",
 * jadi tidak bertabrakan dengan id GTFS Transjakarta.
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
        val files = ASSETS.map { name ->
            json.decodeFromString<StaticFile>(context.assets.open(name).bufferedReader().use { it.readText() })
        }
        TjNetwork(
            routes = files.flatMap { it.routes }
                .map { TjRoute(it.id, it.short, it.long, it.kind, it.color, it.text) }
                .sortedWith(routeOrder),
            halte = files.flatMap { it.halte.entries }.associate { (id, row) ->
                id to Halte(id, row[0].jsonPrimitive.content, row[1].jsonPrimitive.double, row[2].jsonPrimitive.double)
            },
            trips = files.flatMap { it.trips }.map { TjTrip(
                id = it.id,
                routeId = it.route,
                headsign = it.head,
                name = it.name,
                halteIds = it.stops,
                shape = it.shape,
                arrivals = it.arr,
                days = it.days,
                freqs = it.freq.map { f -> Frequency(f[0], f[1], f[2]) },
            ) },
            fixedScheduleValidUntil = files.firstNotNullOfOrNull { it.meta?.validUntil }?.let(LocalDate::parse),
        )
    }

    @Serializable
    private data class StaticFile(
        val routes: List<RouteRow>,
        /** id -> [nama, lat, lng]; dibuat ringkas karena jumlahnya ribuan. */
        val halte: Map<String, JsonArray>,
        val trips: List<TripRow>,
        /** Hanya ada di rail_static.json. */
        val meta: Meta? = null,
    )

    @Serializable
    private data class Meta(val validUntil: String? = null)

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
        val arr: List<Int> = emptyList(),
        val days: Int = 0,
        /** [mulai, selesai, headway] dalam detik; di JSON "freq" adalah list list angka. */
        val freq: List<List<Int>> = emptyList(),
    )

    private companion object {
        val ASSETS = listOf("tj_static.json", "rail_static.json")
        val json = Json { ignoreUnknownKeys = true }

        /** Rel (hanya belasan rute) di atas supaya mudah ditemukan, lalu koridor BRT; dalam satu jenis urut angka alami (2 < 10). */
        val KIND_ORDER = listOf("KRL", "MRT", "LRT Jakarta", "LRT Jabodebek", "BRT", "Angkutan Umum Integrasi", "Mikrotrans", "Transjabodetabek")

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
