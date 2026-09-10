# Technical Design Document
## WakeMate — Android App

| | |
|---|---|
| **Versi** | 1.0 |
| **Terkait** | PRD_Transit_Wake_App.md |
| **Scope** | v1.0 MVP |

---

## 1. Ringkasan Arsitektur

Menggunakan **Clean Architecture** ringan dengan 4 layer (lihat diagram di atas), single module untuk MVP:

```
app/
 ├── presentation/     // Compose UI, ViewModel, Navigation
 ├── domain/           // Use cases, model domain, interface repository
 ├── data/             // Implementasi repository, mapper
 ├── data/local/        // Room: Entity, DAO, Database
 ├── data/location/      // Location tracking, proximity calculation
 ├── service/          // Foreground Service (TripTrackingService)
 └── di/                // Hilt modules
```

Prinsip: `presentation` hanya tahu `domain` (lewat interface), `domain` tidak tahu Android framework sama sekali (murni Kotlin + Coroutines), `data` mengimplementasikan interface dari `domain`.

## 2. Komponen Inti

### 2.1 `TripTrackingService` (Foreground Service)
- Tipe: `foregroundServiceType="location"`
- Dimulai saat user tap "Mulai Perjalanan", berhenti saat sesi selesai/dibatalkan
- Menampilkan **persistent notification** (wajib untuk foreground service) berisi status: "Menuju [Stasiun] — 3 stasiun lagi"
- Mengelola siklus hidup `LocationTracker` dan `ProximityCalculator`

### 2.2 `LocationTracker`
- Wrapper di atas `FusedLocationProviderClient`
- Expose `Flow<Location>` ke pemanggil (via callbackFlow)
- Adaptive interval: default `LocationRequest` 30–60 detik, berubah ke 5–10 detik saat `ProximityCalculator` melaporkan status "mendekat" (lihat §7.2 PRD)

### 2.3 `ProximityCalculator`
- Input: lokasi user saat ini + `List<Station>` terurut sesuai `sequenceInLine` pada line yang dipilih
- Output: `ProximityState` (stasiun terdekat saat ini, jumlah stasiun tersisa, jarak ke tujuan)
- Logika: cari station terdekat dari posisi user menggunakan Haversine formula, cocokkan dengan urutan station di line untuk menentukan arah pergerakan (agar tidak salah hitung kalau line-nya bolak-balik)
- Fallback: jika tidak ada update GPS valid dalam durasi tertentu (misal di terowongan MRT), gunakan estimasi waktu tempuh rata-rata antar stasiun (data statis) untuk tetap menghitung estimasi progres

### 2.4 `AlertManager`
- Menerima `ProximityState`, mengecek terhadap `alertThresholdValue` yang dipilih user
- Saat threshold tercapai → trigger notifikasi heads-up + getar via `NotificationCompat`
- Didesain sebagai interface agar gampang di-extend ke alarm keras di v1.1 tanpa mengubah pemanggilnya (`AlertManager` → nanti ada `AlarmAlertManager` sebagai implementasi baru)

## 3. Use Cases (Domain Layer)

| Use Case | Tanggung Jawab |
|---|---|
| `SearchStationsUseCase` | Cari stasiun/halte berdasarkan query + moda |
| `StartTripUseCase` | Validasi input, buat `TripSession` baru, mulai `TripTrackingService` |
| `ObserveTripProgressUseCase` | Expose `Flow<ProximityState>` ke UI untuk ditampilkan real-time |
| `CancelTripUseCase` | Hentikan sesi & service |
| `CompleteTripUseCase` | Tandai sesi selesai setelah alert dikonfirmasi/timeout |

## 4. Skema Room (final draft)

Mengacu ke §10 PRD, ditambahkan detail DAO:

```kotlin
@Dao
interface StationDao {
    @Query("SELECT * FROM station WHERE mode = :mode AND name LIKE '%' || :query || '%'")
    fun search(mode: TransitMode, query: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM station WHERE lineId = :lineId ORDER BY sequenceInLine ASC")
    suspend fun getStationsInLine(lineId: String): List<StationEntity>
}

@Dao
interface TripSessionDao {
    @Insert
    suspend fun insert(session: TripSessionEntity): Long

    @Update
    suspend fun update(session: TripSessionEntity)

    @Query("SELECT * FROM trip_session WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(): TripSessionEntity?
}
```

Catatan: `getActiveSession()` penting untuk **recovery** — kalau app di-kill lalu dibuka lagi oleh sistem/user saat sesi masih aktif, UI bisa langsung sinkron ke sesi yang berjalan tanpa kehilangan state.

## 5. Dependency Injection (Hilt) — Modul

```
AppModule          → Application-level singleton (Database, DataStore)
LocationModule     → FusedLocationProviderClient, LocationTracker
RepositoryModule   → Binding interface domain ke implementasi data
ServiceModule      → Dependency khusus untuk TripTrackingService (EntryPoint,
                      karena Service butuh manual injection dgn @AndroidEntryPoint)
```

## 6. Alur Sesi (Sequence, tekstual)

1. `HomeScreen` → user pilih moda & tujuan → `SearchStationsUseCase`
2. User tap mulai → `StartTripUseCase` → simpan `TripSession` (status `ACTIVE`) ke Room → start `TripTrackingService` via Intent
3. `TripTrackingService.onStartCommand()` → mulai collect `LocationTracker.locationFlow` di `serviceScope`
4. Setiap lokasi baru masuk → `ProximityCalculator.calculate()` → emit `ProximityState` → update notification & simpan ke `StateFlow` yang diobservasi `ObserveTripProgressUseCase`
5. Saat `ProximityState` memenuhi threshold → `AlertManager.trigger()` → kirim notifikasi heads-up + getar
6. User tap notifikasi → `CompleteTripUseCase` → update `TripSession.status = COMPLETED` → stop service
7. (Alternatif) User cancel manual dari UI → `CancelTripUseCase` → stop service, status `CANCELLED`

## 7. Data Seeding (Stasiun/Halte)

- Sesuai §8 PRD: file `stations_seed.json` (assets) berisi semua stasiun KRL, MRT, dan hasil parsing GTFS Transjakarta
- `SeedDatabaseUseCase` dijalankan sekali di first-launch (dicek via DataStore flag `is_seeded`)
- Struktur JSON idealnya sudah dipisah per moda agar mudah di-parse & di-update terpisah:
```json
{
  "mode": "TRANSJAKARTA",
  "lines": [
    { "id": "koridor-1", "name": "Koridor 1", "stations": [
      { "id": "blok-m", "name": "Blok M", "lat": -6.2438, "lng": 106.7993, "sequence": 1 }
    ]}
  ]
}
```

## 8. Permission Handling

Flow permintaan izin (Android 10+):
1. Saat pertama kali user tap "Mulai Perjalanan" → minta `ACCESS_FINE_LOCATION` (foreground)
2. Jelaskan lewat rationale dialog kenapa perlu **background location** juga (`ACCESS_BACKGROUND_LOCATION`) — karena app perlu tetap tracking walau layar mati
3. Untuk Android 13+ → minta `POST_NOTIFICATIONS` juga di flow yang sama
4. Jika user tolak background location → beri tahu keterbatasan (app hanya bisa tracking saat layar menyala) alih-alih diam-diam gagal

## 9. Testing Strategy

| Layer | Jenis Test | Tools |
|---|---|---|
| Domain (use cases, `ProximityCalculator`) | Unit test murni Kotlin | JUnit, Turbine (untuk `Flow`) |
| Data (Repository, Room) | Instrumented test | Room in-memory DB, JUnit |
| Presentation (ViewModel) | Unit test | JUnit, Turbine, Coroutines Test |
| UI (Compose) | UI test | Compose UI Test |
| `ProximityCalculator` khususnya | Test dengan data real koordinat GPS sample dari perjalanan asli (rekam log lokasi dari 1-2 kali naik KRL/TJ beneran) | Manual data collection |

`ProximityCalculator` adalah komponen paling kritis (menentukan keandalan alert) — sebaiknya jadi prioritas testing pertama, idealnya divalidasi dengan data GPS asli dari perjalanan sungguhan, bukan cuma data sintetis.

## 10. Urutan Development yang Disarankan

1. Setup project + Hilt + Room + Navigation Compose (skeleton)
2. Data seeding: parsing GTFS Transjakarta + kurasi manual KRL/MRT → `stations_seed.json`
3. `ProximityCalculator` + unit test (bisa dikembangkan & diuji tanpa UI/Service dulu)
4. `LocationTracker` + `TripTrackingService` (foreground service dasar, belum ada alert)
5. Integrasi `AlertManager` (notifikasi + getar)
6. UI: pemilihan moda/tujuan, layar sesi aktif
7. Permission flow lengkap + edge case handling (§13 PRD)
8. Testing end-to-end dengan perjalanan asli (naik KRL/TJ sungguhan sambil app jalan)

---

*Langkah berikutnya: mulai dari poin 1–3 di §10 (skeleton project + data seeding + ProximityCalculator) sebagai fondasi yang bisa langsung diuji tanpa perlu UI selesai dulu.*
