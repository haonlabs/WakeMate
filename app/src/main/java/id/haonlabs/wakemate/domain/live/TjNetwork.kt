package id.haonlabs.wakemate.domain.live

/** Data statis dari GTFS resmi Transjakarta (assets/tj_static.json, lihat tools/generate_tj_static.py). */
data class TjRoute(
    val id: String,
    val shortName: String,
    val longName: String,
    /** route_desc GTFS: BRT, Mikrotrans, Angkutan Umum Integrasi, Transjabodetabek, ... */
    val kind: String,
    /** RRGGBB tanpa '#'. */
    val color: String,
    val textColor: String,
)

data class Halte(val id: String, val name: String, val lat: Double, val lng: Double)

data class TjTrip(
    val id: String,
    val routeId: String,
    val headsign: String,
    val name: String,
    /** Id halte induk (parent station GTFS), urut sesuai perjalanan. Sama dengan id di feed live. */
    val halteIds: List<String>,
    /** Google encoded polyline, presisi 5. */
    val shape: String,
)

class TjNetwork(
    val routes: List<TjRoute>,
    val halte: Map<String, Halte>,
    trips: List<TjTrip>,
) {
    val routeById: Map<String, TjRoute> = routes.associateBy { it.id }
    val tripById: Map<String, TjTrip> = trips.associateBy { it.id }
    private val tripsByRoute: Map<String, List<TjTrip>> = trips.groupBy { it.routeId }

    /**
     * Semua varian trip searah [direction]. Halte BRT dipakai dua arah dengan id yang sama, jadi
     * arah bus harus dikenali dari trip-nya, bukan dari halte yang akan dilewatinya.
     */
    fun tripIdsToward(direction: TjTrip): Set<String> =
        tripsByRoute[direction.routeId].orEmpty()
            .filter { it.headsign == direction.headsign }
            .mapTo(HashSet()) { it.id }

    /**
     * Satu arah per headsign. Sebuah rute bisa punya beberapa varian trip ke arah yang sama;
     * varian dengan halte terbanyak yang ditampilkan karena paling lengkap.
     */
    fun directions(routeId: String): List<TjTrip> =
        tripsByRoute[routeId].orEmpty()
            .groupBy { it.headsign }
            .map { (_, variants) -> variants.maxBy { it.halteIds.size } }
}
