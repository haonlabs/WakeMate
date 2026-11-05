# Kebijakan Privasi WakeMate

**Berlaku sejak:** 10 September 2026
**Terakhir diperbarui:** 5 November 2026
**Aplikasi:** WakeMate (`id.haonlabs.wakemate`)
**Pengembang:** Haon Malvinger
**Kontak:** developedbyadifens@gmail.com

---

## Ringkasan singkat

WakeMate menunjukkan KRL, MRT, LRT, dan Transjakarta di sekitarmu, menampilkan peta rutenya, dan
membangunkanmu sebelum tiba di stasiun atau halte tujuan (fitur Bangunin).

- **Lokasimu tetap di perangkatmu.** Koordinat posisimu diproses di dalam ponsel dan **tidak
  pernah dikirim** ke server mana pun, termasuk ke kami.
- **Internet hanya dipakai untuk gambar peta** dari OpenFreeMap. Karena peta di tab Sekitar
  menampilkan area di sekitarmu, penyedia peta bisa mengetahui perkiraan area itu dari gambar
  peta yang kamu minta (lihat bagian 2).
- **Rute, jadwal, dan stasiun sudah ada di dalam aplikasi.** WakeMate tidak menampilkan posisi
  kendaraan secara langsung.
- **Bangunin (KRL, MRT, LRT, koridor BRT) bekerja tanpa internet.**
- Tidak ada akun, tidak ada iklan, tidak ada analitik, dan tidak ada pelaporan crash. Kami tidak
  memiliki server dan tidak menerima data apa pun darimu.

---

## 1. Data yang diakses

### 1.1 Lokasi perangkat

**Kapan diakses.**
- Selama sesi **Bangunin** berjalan, yaitu sejak kamu menekan "Mulai perjalanan" sampai sesi
  selesai atau dibatalkan.
- Selama tab **Sekitar** terbuka di layar, untuk mencari halte dan stasiun terdekat. Lokasi
  dibaca ulang kira-kira tiap 30 detik, dan berhenti begitu layar itu tidak lagi terbuka di
  depanmu.
- Satu kali saat kamu membuka peta sebuah rute di **Peta Rute**, dan saat kamu menekan tombol
  Lokasiku, untuk memusatkan peta ke posisimu.

Sekitar dan Peta Rute tidak meminta izin sendiri: keduanya memakai izin lokasi yang sudah kamu
berikan, dan tetap berfungsi tanpanya (Sekitar menampilkan layar untuk memberi izin).
Di luar keadaan di atas, WakeMate tidak meminta lokasi sama sekali.

**Untuk apa.** Menghitung posisimu terhadap urutan stasiun di jalur yang kamu pilih (Bangunin),
dan menghitung halte atau stasiun terdekat dari posisimu (Sekitar dan Peta Rute).

**Ke mana perginya.** Tidak ke mana-mana. Koordinat diproses di memori perangkat lalu digantikan
pembacaan berikutnya. Koordinat lokasimu **tidak disimpan secara permanen** dan **tidak pernah
dikirim keluar perangkat oleh WakeMate**. Satu hal yang perlu kamu tahu: peta di tab Sekitar
terpusat di posisimu, sehingga gambar peta yang diminta dari OpenFreeMap mencakup area di sekitar
posisimu (lihat bagian 2).

**Saat layar mati.** Pemantauan berlanjut melalui *foreground service* dengan notifikasi yang
selalu terlihat selama sesi aktif. Kamu selalu bisa tahu kapan WakeMate sedang memantau, dan bisa
menghentikannya kapan saja dari notifikasi itu.

### 1.2 Data yang disimpan di perangkatmu

Tersimpan secara lokal, tidak pernah dikirim keluar:

| Data | Isi | Kegunaan |
|---|---|---|
| Riwayat perjalanan | Stasiun tujuan, jalur, ambang alarm, waktu mulai dan selesai, status sesi | Memulihkan sesi yang terputus, dan riwayat pribadimu |
| Rute favorit dan terakhir dibuka | Kode rute yang kamu favoritkan atau buka | Menampilkan rute yang kamu pakai di Peta Rute |
| Pengaturan | Preferensi penggunaan speaker, versi data stasiun | Mengingat pilihanmu |
| Data rute, jadwal, stasiun, dan halte | Daftar rute, jadwal, stasiun, halte, dan koordinatnya | Disertakan bersama aplikasi, bukan data tentang dirimu |

Riwayat perjalanan memuat **stasiun tujuanmu beserta waktunya**, yang merupakan informasi tentang
kebiasaan perjalananmu. Data tersebut hanya berada di perangkatmu dan dapat kamu hapus kapan saja
(lihat bagian 6).

### 1.3 Yang tidak diakses sama sekali

Nama, alamat email, nomor telepon, kontak, kalender, kamera, mikrofon, foto, daftar aplikasi
terpasang, pengenal iklan, dan riwayat penjelajahan. WakeMate tidak meminta satu pun di antaranya.

---

## 2. Koneksi internet

Internet hanya dipakai untuk satu tujuan, yaitu mengunduh gambar peta. Koneksinya terenkripsi
(TLS).

| Tujuan | Untuk apa | Yang diterima server itu dari perangkatmu |
|---|---|---|
| OpenFreeMap (`tiles.openfreemap.org`) | Mengunduh gambar peta | Alamat IP-mu dan area peta yang sedang ditampilkan |

Beberapa hal yang perlu kamu ketahui:

- **Koordinat lokasimu tidak dikirim.** Tetapi di tab Sekitar peta terpusat di posisimu, jadi area
  peta yang diminta berada di sekitar posisimu. Di Peta Rute, area peta adalah area rute yang kamu
  buka.
- **Alamat IP** pasti terlihat oleh server mana pun yang dihubungi perangkat, sama seperti saat
  membuka situs web. WakeMate tidak menambahkan informasi lain tentang dirimu.
- WakeMate tidak menghubungi server operator transportasi mana pun. Rute dan jadwal sudah ada di
  dalam aplikasi, sehingga tidak ada posisi kendaraan secara langsung.
- Koneksi hanya dibuka saat ada peta di layar.

---

## 3. Izin yang diminta dan alasannya

| Izin | Alasan |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Menghitung posisimu terhadap stasiun tujuan (Bangunin) dan mencari halte serta stasiun terdekat (Sekitar, Peta Rute) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Menjaga sesi Bangunin tetap hidup sepanjang perjalanan, termasuk saat layar terkunci. WakeMate **tidak** meminta izin lokasi latar belakang: lokasi hanya dibaca selama sesi yang kamu mulai sendiri, dengan notifikasi yang selalu terlihat |
| `INTERNET` | Mengunduh gambar peta (lihat bagian 2) |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Dipakai pustaka peta MapLibre untuk mengetahui apakah perangkat sedang tersambung ke internet |
| `POST_NOTIFICATIONS` | Menampilkan status sesi dan membunyikan pengingat |
| `VIBRATE` | Menggetarkan ponsel saat alarm berbunyi |

Menolak izin lokasi berarti Bangunin dan Sekitar tidak dapat berfungsi, karena tidak ada cara lain
untuk mengetahui kamu sudah sampai mana atau apa yang ada di dekatmu. Peta Rute tetap dapat
dipakai tanpa izin lokasi.

---

## 4. Pihak ketiga

- **Google Play Services (Location)** dari Google dipakai untuk memperoleh pembacaan lokasi.
  Layanan ini merupakan bagian dari sistem Android di perangkatmu. Cara Google memproses lokasi
  sebagai penyedia layanan tersebut tunduk pada Kebijakan Privasi Google:
  https://policies.google.com/privacy
- **OpenFreeMap** menyediakan gambar peta (lihat bagian 2): https://openfreemap.org
- **MapLibre** adalah pustaka sumber terbuka untuk menggambar peta. Pustaka ini berjalan di
  perangkatmu dan tidak mengirim data penggunaan ke siapa pun.

Tidak ada pustaka pihak ketiga yang mengumpulkan data: tidak ada analitik, tidak ada pelaporan
crash, tidak ada jaringan iklan, tidak ada SDK atribusi.

---

## 5. Berbagi dan penjualan data

WakeMate **tidak membagikan, menjual, menyewakan, atau memperdagangkan** data apa pun, kepada
siapa pun, untuk tujuan apa pun. Kami tidak memiliki server dan tidak menerima data darimu,
sehingga tidak ada yang bisa kami bagikan. Satu-satunya informasi yang keluar dari perangkatmu
adalah yang dijelaskan di bagian 2, dan itu dikirim langsung oleh perangkatmu ke penyedia peta,
bukan melalui kami.

---

## 6. Penyimpanan dan penghapusan

Data hanya bertahan selama aplikasi terpasang di perangkatmu.

- **Menghapus riwayat dan pengaturan:** Setelan Android → Aplikasi → WakeMate → Penyimpanan →
  Hapus data
- **Menghapus seluruhnya:** copot pemasangan aplikasi

Karena kami tidak memiliki server, tidak ada data milikmu yang tersimpan di pihak kami, dan tidak
ada permintaan penghapusan yang perlu kamu ajukan kepada kami. Untuk data yang mungkin dicatat
oleh OpenFreeMap saat perangkatmu terhubung (misalnya log alamat IP), berlaku kebijakan penyedia
tersebut.

---

## 7. Anak-anak

WakeMate tidak ditujukan untuk anak di bawah 13 tahun dan tidak mengumpulkan data dari mereka
secara sengaja. Aplikasi ini juga tidak mengumpulkan data yang dapat mengidentifikasi seseorang
dari pengguna mana pun.

---

## 8. Keamanan

Data disimpan di penyimpanan privat aplikasi, yang dilindungi sistem Android dan tidak dapat
dibaca aplikasi lain. Koneksi ke server peta dienkripsi dengan TLS.

---

## 9. Sumber data

- **Stasiun dan jalur KRL, MRT, LRT, dan koridor BRT:** **OpenStreetMap**, tersedia di bawah
  lisensi Open Database License (ODbL). © Kontributor OpenStreetMap.
- **Jadwal KRL:** KRL Commuter Line GTFS data compiled and verified by Open-KRL, lisensi
  CC-BY 4.0 (https://github.com/open-krl/pipeline).
- **Rute, halte, dan jadwal Transjakarta:** data GTFS yang dipublikasikan Transjakarta di
  gtfs.transjakarta.co.id.
- **Peta:** OpenFreeMap, dengan data © Kontributor OpenStreetMap dan OpenMapTiles.

Jadwal hanyalah perkiraan dan bukan posisi kendaraan secara langsung. WakeMate tidak berafiliasi
dengan PT Transportasi Jakarta, KAI Commuter, MRT Jakarta, LRT Jakarta, maupun LRT Jabodebek.

Data rute dan stasiun disertakan bersama aplikasi dan bukan merupakan data tentang dirimu.

---

## 10. Perubahan kebijakan

Bila kebijakan ini berubah, tanggal "Terakhir diperbarui" di atas akan disesuaikan. Perubahan yang
memengaruhi cara data diperlakukan akan diumumkan di dalam aplikasi sebelum berlaku.

**Perubahan 5 November 2026:** fitur Live TJ (posisi bus langsung) dihapus, sehingga aplikasi tidak
lagi menghubungi server Transjakarta dan izin `FOREGROUND_SERVICE_DATA_SYNC` dibuang. Ditambahkan
tab Sekitar, Peta Rute multimoda, dan LRT. Tab Sekitar membaca lokasi saat terbuka dan memusatkan
peta di posisimu, sehingga area peta yang diminta dari OpenFreeMap berada di sekitar posisimu
(bagian 1.1 dan 2). Koordinat lokasimu tetap tidak dikirim ke mana pun.

**Perubahan 12 Oktober 2026:** penambahan fitur Live TJ. Aplikasi kini memakai koneksi internet
untuk menerima posisi bus dan mengunduh peta (bagian 2), dan izin terkait ditambahkan (bagian 3).
Pemrosesan lokasimu tetap sepenuhnya di dalam perangkat.

---

## 11. Kontak

Pertanyaan mengenai kebijakan ini dapat dikirim ke: **developedbyadifens@gmail.com**
