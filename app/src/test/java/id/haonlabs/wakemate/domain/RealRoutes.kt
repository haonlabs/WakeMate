package id.haonlabs.wakemate.domain

import id.haonlabs.wakemate.domain.model.Station
import id.haonlabs.wakemate.domain.model.TransitMode

/**
 * Koordinat asli dari stations_seed.json (OpenStreetMap), bukan data sintetis: geometri line yang
 * berbelok dan halte Transjakarta yang berdempetan justru kasus yang paling ingin diuji.
 */
private fun st(mode: TransitMode, lineId: String, name: String, lat: Double, lng: Double, seq: Int) =
    Station("$lineId:$seq", name, mode, lat, lng, lineId, seq)

private fun route(mode: TransitMode, lineId: String, vararg rows: Triple<String, Double, Double>) =
    rows.mapIndexed { i, (name, lat, lng) -> st(mode, lineId, name, lat, lng, i) }

/** MRT Lin Utara-Selatan, Lebak Bulus -> Bundaran HI (13 stasiun). */
val MRT_NORTH_SOUTH = route(
    TransitMode.MRT, "mrt-north-south",
    Triple("Lebak Bulus Bank Syariah Indonesia", -6.289296, 106.774932),
    Triple("Fatmawati Indomaret", -6.292471, 106.792463),
    Triple("Cipete Raya TUKU", -6.278372, 106.797342),
    Triple("Haji Nawi", -6.266696, 106.797344),
    Triple("Blok A VISA", -6.255787, 106.797172),
    Triple("Blok M BCA", -6.244423, 106.798239),
    Triple("ASEAN Headquarters", -6.238771, 106.798470),
    Triple("Senayan Mastercard", -6.226784, 106.802524),
    Triple("Istora Mandiri", -6.222442, 106.808665),
    Triple("Bendungan Hilir", -6.215063, 106.817995),
    Triple("Setiabudi Astra", -6.209095, 106.821762),
    Triple("Dukuh Atas BNI", -6.200802, 106.822828),
    Triple("Bundaran HI Bank Jakarta", -6.191849, 106.823062),
)

/** Transjakarta Koridor 1, Blok M -> Kali Besar (20 halte, banyak yang < 600 m terpisah). */
val TJ_KORIDOR_1 = route(
    TransitMode.TRANSJAKARTA, "tj-koridor-1",
    Triple("Blok M", -6.243351, 106.802010),
    Triple("ASEAN", -6.239935, 106.799104),
    Triple("Masjid Agung", -6.236420, 106.798413),
    Triple("Bundaran Senayan", -6.227831, 106.800946),
    Triple("Senayan Bank Jakarta", -6.224243, 106.805675),
    Triple("Polda Metro Jaya", -6.221044, 106.810258),
    Triple("Bendungan Hilir", -6.217053, 106.815326),
    Triple("Karet", -6.212406, 106.820010),
    Triple("Dukuh Atas", -6.205220, 106.822252),
    Triple("Tosari", -6.196514, 106.823073),
    Triple("Bundaran HI ASTRA", -6.193683, 106.822988),
    Triple("M.H. Thamrin", -6.188074, 106.822981),
    Triple("Kebon Sirih", -6.182595, 106.822782),
    Triple("Monumen Nasional", -6.176178, 106.822788),
    Triple("Harmoni Temporer", -6.162477, 106.819803),
    Triple("Sawah Besar Temporer", -6.157283, 106.818607),
    Triple("Mangga Besar Temporer", -6.153231, 106.817422),
    Triple("Taman Sari", -6.149323, 106.816627),
    Triple("Glodok", -6.143415, 106.815114),
    Triple("Kali Besar", -6.135331, 106.811409),
)
