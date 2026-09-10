# Product Requirement Document
## WakeMate — Pengingat Tujuan untuk Pengguna Transportasi Umum

| | |
|---|---|
| **Versi** | 1.0 |
| **Tanggal** | 10 September 2026 |
| **Status** | Draft |
| **Platform** | Android (native) |

---

## 1. Latar Belakang & Masalah

Pengguna transportasi umum di Jabodetabek (KRL, MRT, Transjakarta) sering menempuh perjalanan panjang dan monoton, terutama di jam-jam sepi atau rute jauh (misal Bogor–Jakarta Kota, Bekasi–Manggarai). Banyak dari mereka ingin memanfaatkan waktu perjalanan untuk tidur, tapi menahan diri karena takut **kebablasan** (missed stop), yang berakibat:

- Kehilangan waktu dan harus balik arah
- Terlambat sampai tujuan (kerja, sekolah, janji)
- Stres/kecemasan yang membuat pengguna tidak bisa benar-benar istirahat meski secara fisik sedang naik kendaraan

**Problem Statement:** Pengguna transportasi umum membutuhkan cara yang andal untuk dibangunkan/diberi tahu sebelum tiba di stasiun/halte tujuan, tanpa harus terus-menerus memantau posisi secara manual.

## 2. Tujuan Produk (Goals)

1. Memberi notifikasi/alert kepada pengguna saat kendaraan mendekati stasiun/halte tujuan, dengan jarak ambang yang bisa disesuaikan.
2. Mendukung tiga moda utama: **KRL Commuter Line**, **MRT Jakarta**, **Transjakarta**.
3. Alert harus cukup andal untuk dipercaya pengguna tidur — meminimalkan false negative (tidak bunyi saat seharusnya bunyi) lebih penting daripada meminimalkan false positive.
4. Hemat baterai — aplikasi berjalan di background dalam durasi lama (bisa 30–90 menit) tanpa menguras baterai secara signifikan.

### Success Metrics (indikatif, didiskusikan lebih lanjut)
- ≥ 95% alert terkirim dalam radius/waktu yang ditentukan (tidak telat, tidak gagal kirim)
- Retensi 7 hari (D7) sebagai indikator kepercayaan pengguna terhadap keandalan alert
- Rata-rata konsumsi baterai per sesi perjalanan (target: dapat dibandingkan dengan Google Maps navigation sebagai baseline)

## 3. Target Pengguna

**Persona utama:** Komuter harian Jabodetabek, perjalanan 30–90 menit, rute rutin (rumah↔kantor/kampus), sering mengantuk terutama di pagi hari (subuh) atau pulang malam setelah lelah kerja.

**Persona sekunder:** Wisatawan/pendatang baru yang tidak familiar dengan rute dan takut salah turun.

## 4. Ruang Lingkup (Scope)

### 4.1 MVP (v1.0)
- Pilih moda: KRL / MRT / Transjakarta
- Cari & pilih stasiun/halte tujuan (dari database lokal)
- Set jarak/ambang alert (default rekomendasi, bisa disesuaikan — lihat §7.3)
- Deteksi otomatis saat mendekati tujuan → notifikasi + getar
- Berjalan di background walau layar mati / app di-minimize
- Riwayat perjalanan sederhana (opsional, untuk debugging & histori pribadi)

### 4.2 Out of Scope (v1.0)
- Real-time jadwal/delay kereta (integrasi jadwal live)
- Rekomendasi rute (routing multi-moda seperti Google Maps)
- Pembayaran/tiket elektronik
- iOS
- Alert berbasis suara/alarm keras (dijadwalkan sebagai v1.1, lihat §12)

### 4.3 Kandidat v1.1+
- Alarm keras yang menembus mode silent (pakai `AlarmManager` + full-screen intent, mirip alarm bangun tidur)
- Deteksi otomatis moda & stasiun keberangkatan (tanpa input manual)
- Widget/quick tile
- Wear OS companion (getar di jam tangan agar tidak ganggu penumpang lain)

## 5. User Stories

| # | Sebagai... | Saya ingin... | Agar... |
|---|---|---|---|
| 1 | Komuter KRL | memilih stasiun tujuan sebelum naik kereta | aplikasi tahu kapan harus mengingatkan saya |
| 2 | Komuter yang mengantuk | mendapat notifikasi + getaran saat mendekati tujuan | saya bisa tidur tanpa takut kebablasan |
| 3 | Pengguna baru kota | melihat radius/jarak alert yang bisa disesuaikan | saya punya cukup waktu bersiap sebelum turun |
| 4 | Pengguna Transjakarta | aplikasi tetap akurat meski koridor punya banyak halte berdekatan | tidak salah deteksi halte |
| 5 | Semua pengguna | aplikasi tidak menguras baterai HP | saya tetap bisa pakai HP untuk hal lain setelah turun |

## 6. Functional Requirements

### 6.1 Pemilihan Moda & Tujuan
- FR-1: User memilih moda transportasi (KRL / MRT / Transjakarta) sebagai langkah pertama.
- FR-2: User mencari & memilih stasiun/halte tujuan dari daftar (search dengan autocomplete).
- FR-3: Sistem menampilkan rute/line yang melewati stasiun tersebut jika ambigu (misal 1 stasiun dilewati banyak line, contoh Manggarai).

### 6.2 Deteksi Posisi & Alert
- FR-4: Sistem melakukan tracking lokasi user secara periodik selama sesi perjalanan aktif.
- FR-5: Sistem menghitung jarak/estimasi stasiun tersisa menuju tujuan menggunakan kombinasi **geofencing GPS real-time** + **pencocokan rute** (line yang sedang dilalui), bukan garis lurus semata — penting karena KRL/MRT berbelok dan halte TransJakarta padat di sepanjang koridor yang sama.
- FR-6: User dapat memilih ambang alert: berdasarkan **jarak** (mis. 1 km) atau **jumlah stasiun/halte tersisa** (mis. 2 stasiun sebelum tujuan) — direkomendasikan default **2 stasiun/halte sebelum tujuan**, karena jarak dalam km kurang bermakna di KRL/MRT yang jarak antar stasiunnya tidak seragam (bisa 900 m atau 5 km).
- FR-7: Saat ambang tercapai, sistem mengirim **notifikasi lokal + getaran** (sesuai keputusan awal), dengan opsi upgrade ke alarm keras di v1.1.
- FR-8: Sesi perjalanan otomatis berakhir setelah alert terkirim & dikonfirmasi (tap notifikasi), atau otomatis timeout jika user tidak berinteraksi dalam durasi tertentu.

### 6.3 Sesi & Kontrol
- FR-9: User dapat membatalkan sesi perjalanan kapan saja.
- FR-10: Sistem menampilkan status sesi aktif via **persistent notification** (indikasi wajib untuk foreground service Android), menunjukkan sisa stasiun/estimasi waktu.
- FR-11: Jika GPS tidak akurat (di dalam terowongan MRT, misalnya), sistem fallback ke estimasi berbasis waktu tempuh rata-rata antar stasiun.

## 7. Non-Functional Requirements

### 7.1 Reliability
- Alert tidak boleh gagal terkirim karena app di-kill oleh sistem — gunakan **Foreground Service** dengan tipe `location`, bukan `WorkManager` biasa yang periodenya tidak presisi untuk kasus real-time seperti ini.
- Tangani battery optimization/doze mode secara eksplisit (minta user whitelist app dari battery optimization saat sesi pertama dimulai).

### 7.2 Battery Efficiency
- Gunakan **adaptive location interval**: update lebih jarang saat masih jauh dari tujuan (mis. tiap 30–60 detik), lebih sering saat mendekati ambang (mis. tiap 5–10 detik).
- Gunakan `FusedLocationProviderClient` (Google Play Services) dengan prioritas `PRIORITY_BALANCED_POWER_ACCURACY` di fase jauh, naik ke `PRIORITY_HIGH_ACCURACY` saat mendekati tujuan.

### 7.3 Akurasi Deteksi
- Radius geofence per stasiun sebaiknya tidak seragam — disesuaikan dengan jarak antar stasiun aktual (data statis), bukan angka pukul rata seperti "5 km" yang diusulkan awal (ini bisa meleset jauh, misal di MRT jarak antar stasiun kadang < 1 km).
- Untuk Transjakarta, kepadatan halte per koridor tinggi → radius deteksi harus lebih kecil (~200–400 m) dibanding KRL (~800m–1.5km) atau MRT (~500m–1km).

### 7.4 Privasi
- Data lokasi diproses **on-device**; tidak perlu dikirim ke server kecuali untuk fitur masa depan (analytics agregat, opsional & opt-in).
- Permintaan izin lokasi granular sesuai Android 10+ (foreground dulu, baru background jika diperlukan, dengan penjelasan kontekstual/"rationale").

## 8. Rekomendasi Sumber Data Stasiun/Halte

Karena kebutuhan intinya adalah **koordinat (lat/long) stasiun/halte** yang relatif statis (jarang berubah), bukan jadwal real-time, rekomendasi berikut memprioritaskan biaya rendah & kemudahan maintenance untuk MVP:

| Moda | Sumber Data Rekomendasi | Catatan |
|---|---|---|
| **Transjakarta** | GTFS resmi dari `gtfs.transjakarta.co.id` (terverifikasi di Mobility Database sebagai *Official Feed*) | Lengkap: 253 rute, koordinat halte, shapes. Bisa di-parse sekali lalu diseed ke Room, di-refresh berkala. |
| **KRL Commuter Line** | Tidak ada API/GTFS resmi publik dari KAI Commuter. Rekomendasi: kurasi manual koordinat stasiun (jumlahnya terbatas, ~80 stasiun Jabodetabek) dari OpenStreetMap, divalidasi manual. Proyek komunitas seperti `comuline/api` bisa jadi referensi tambahan untuk struktur data/jadwal. | Effort kurasi manual realistis karena jumlah stasiun terbatas & jarang berubah. |
| **MRT Jakarta** | Tidak ada API resmi publik. Kurasi manual — hanya 13 stasiun (Lebak Bulus–Bundaran HI), sangat ringan untuk didata manual dari OSM. | |

**Kesimpulan:** Untuk MVP, **tidak perlu Google Maps Platform berbayar**. Gunakan pendekatan **static seeded database**: file JSON (stasiun, koordinat, line, urutan stasiun per line) di-bundle sebagai asset aplikasi, diseed ke Room saat pertama install, dan bisa di-*refresh* dari remote config/API sederhana di kemudian hari tanpa perlu update aplikasi. Ini paling murah, cukup akurat untuk kebutuhan geofencing (bukan turn-by-turn navigation), dan tidak bergantung pada kuota API berbayar.

## 9. Tech Stack & Arsitektur

### 9.1 Stack (dikonfirmasi user)
- **Bahasa:** Kotlin
- **UI:** Jetpack Compose
- **DI:** Hilt (Dagger)
- **Local DB:** Room

### 9.2 Tambahan yang direkomendasikan
| Kebutuhan | Rekomendasi | Alasan |
|---|---|---|
| Lokasi | `FusedLocationProviderClient` (Google Play Services Location) | Lebih akurat & hemat baterai dibanding `LocationManager` bawaan Android |
| Geofencing | Kombinasi custom geofence logic (bukan `Geofencing API` bawaan Play Services) | Play Services Geofencing API punya limit 100 geofence per app & delay deteksi (bisa 2 menit) — kurang presisi untuk kasus real-time ini. Lebih baik hitung jarak manual dari FusedLocationProvider + Haversine formula terhadap rute yang sudah diketahui. |
| Background execution | `Foreground Service` (tipe `location`) | Wajib untuk tracking lokasi berkelanjutan di Android 10+, dan agar tidak dibunuh sistem |
| Async/reactive | Kotlin Coroutines + Flow | Standar modern untuk Compose + Room |
| Navigasi UI | Navigation Compose | Konsisten dengan Compose |
| Preferences ringan | DataStore (Preferences) | Pengganti SharedPreferences, untuk simpan setting radius/ambang default |
| Notifikasi | `NotificationCompat` + `NotificationChannel` | Standar Android untuk notifikasi + getar |
| Testing | JUnit + Turbine (untuk Flow) + Compose UI Test | |

### 9.3 Pertanyaan tech stack yang masih perlu didiskusikan
- **Minimum SDK target** — Android 8 (API 26) untuk jangkauan lebih luas, atau Android 10+ (API 29) untuk permission model lokasi yang lebih modern? (mempengaruhi kompleksitas handling izin)
- **Modularisasi:** single module dulu untuk MVP, atau langsung multi-module (`:core`, `:feature-trip`, `:data`)?
- **Crash/analytics reporting:** pakai Firebase Crashlytics, atau tidak dulu di MVP?
- **CI/CD:** ada preferensi (GitHub Actions, dsb.) atau belum perlu dibahas di tahap PRD ini?

## 10. Data Model (Room — draf awal)

```
Entity: Station
- id: String (PK)
- name: String
- mode: enum (KRL, MRT, TRANSJAKARTA)
- lat: Double
- lng: Double
- lineId: String (FK → Line)
- sequenceInLine: Int   // urutan stasiun dalam line, untuk hitung "N stasiun lagi"

Entity: Line
- id: String (PK)
- name: String          // contoh: "Bogor Line", "Koridor 1"
- mode: enum

Entity: TripSession
- id: Long (PK, autogen)
- destinationStationId: String (FK → Station)
- mode: enum
- alertThresholdType: enum (DISTANCE, STATION_COUNT)
- alertThresholdValue: Float
- startedAt: Long (timestamp)
- endedAt: Long? (nullable)
- status: enum (ACTIVE, COMPLETED, CANCELLED)
```

## 11. Alur Utama (High-Level Flow)

1. User buka app → pilih moda → pilih stasiun tujuan → (opsional) sesuaikan ambang alert
2. User tap "Mulai Perjalanan" → app minta izin lokasi (jika belum) & mulai foreground service
3. App tracking lokasi di background, menghitung posisi relatif terhadap urutan stasiun di line yang dipilih
4. Saat ambang tercapai → kirim notifikasi + getar
5. User tap notifikasi (atau timeout) → sesi selesai, foreground service berhenti

## 12. Desain Alert (sesuai keputusan: notifikasi + getar)

- Notifikasi menggunakan **prioritas tinggi (heads-up notification)** dengan pola getar custom yang cukup panjang untuk membangunkan (bukan getar notifikasi biasa yang singkat).
- Judul jelas: *"Hampir sampai [Nama Stasiun] — bersiap turun"*
- Catatan risiko: notifikasi + getar biasa **berisiko tidak cukup kuat** membangunkan orang yang tidur pulas, terutama jika HP di silent/di dalam tas. Disarankan fitur alarm keras (menembus silent, full-screen intent seperti alarm bangun tidur) dipertimbangkan serius untuk **v1.1**, karena ini langsung berkaitan dengan tujuan inti produk (agar user bisa benar-benar tidur nyenyak, bukan setengah waspada).

## 13. Risiko & Edge Case

| Risiko | Mitigasi |
|---|---|
| GPS tidak akurat/hilang sinyal di terowongan MRT (Bundaran HI–Senayan bawah tanah) | Fallback ke estimasi waktu tempuh rata-rata antar stasiun berdasarkan jadwal statis |
| User naik line/arah yang salah dari yang dipilih | Deteksi mismatch: jika posisi user menyimpang jauh dari rute line yang dipilih, tampilkan warning agar user mengecek ulang |
| App di-kill oleh sistem (battery saver agresif, khususnya HP Xiaomi/Oppo/Vivo yang terkenal agresif) | Edukasi user di onboarding untuk whitelist app; deteksi jika service terhenti tak terduga |
| Android versi lama tanpa dukungan penuh background location | Tentukan minSDK (lihat §9.3) & uji di versi rendah |
| Radius alert terlalu kecil di kondisi macet/kereta berhenti lama | Beri opsi ambang berbasis "jumlah stasiun tersisa" sebagai default, bukan murni jarak/waktu |
| Data statis stasiun jadi usang (ada stasiun baru, contoh Stasiun JIS yang akan beroperasi 2026) | Rencanakan mekanisme update data (remote JSON) tanpa perlu update APK, meski hanya seed lokal untuk MVP |

## 14. Roadmap Ringkas

**v1.0 (MVP)** — Scope §4.1, alert notifikasi + getar, 3 moda, database stasiun statis.
**v1.1** — Alarm keras (bangun paksa), deteksi otomatis stasiun keberangkatan.
**v1.2+** — Widget, Wear OS companion, kemungkinan integrasi jadwal real-time jika API resmi tersedia.

## 15. Open Questions untuk Didiskusikan

1. Min SDK target — lihat §9.3
2. Perlu akun/login, atau app sepenuhnya lokal tanpa akun di MVP?
3. Apakah perlu dukungan multi-bahasa (ID/EN) dari awal?
4. Target rilis: internal testing dulu, atau langsung ke Play Store closed testing?

---

*Dokumen ini adalah draf awal — bagian §9.3 dan §15 perlu keputusan dari kamu sebelum lanjut ke tahap technical design / breakdown task development.*
