# Kebijakan Privasi WakeMate

**Berlaku sejak:** 10 September 2026
**Terakhir diperbarui:** 12 Oktober 2026
**Aplikasi:** WakeMate (`id.haonlabs.wakemate`)
**Pengembang:** Haon Malvinger
**Kontak:** developedbyadifens@gmail.com

---

## Ringkasan singkat

WakeMate membangunkanmu sebelum tiba di stasiun atau halte tujuan, dan menampilkan posisi bus
Transjakarta secara langsung.

- **Lokasimu tetap di perangkatmu.** Koordinat posisimu diproses di dalam ponsel dan **tidak
  pernah dikirim** ke server mana pun, termasuk ke kami.
- **Fitur Live TJ memakai internet** untuk *menerima* posisi bus dari Transjakarta dan gambar
  peta dari OpenFreeMap. Yang diminta ke server itu hanya data rute dan area peta, bukan data
  tentang dirimu.
- **Alarm GPS (KRL, MRT, Transjakarta) tetap bekerja tanpa internet.**
- Tidak ada akun, tidak ada iklan, tidak ada analitik, dan tidak ada pelaporan crash. Kami tidak
  memiliki server dan tidak menerima data apa pun darimu.

---

## 1. Data yang diakses

### 1.1 Lokasi perangkat

**Kapan diakses.**
- Selama sesi **Alarm GPS** berjalan, yaitu sejak kamu menekan "Mulai perjalanan" sampai sesi
  selesai atau dibatalkan.
- Satu kali saat kamu membuka halaman rute di **Live TJ**, untuk mengurutkan bus dari yang paling
  dekat denganmu. Ini hanya terjadi kalau kamu sudah memberi izin lokasi sebelumnya; fitur Live TJ
  tidak memintanya sendiri dan tetap berfungsi tanpanya.

Di luar dua keadaan itu, WakeMate tidak meminta lokasi sama sekali.

**Untuk apa.** Menghitung posisimu terhadap urutan stasiun di jalur yang kamu pilih (Alarm GPS),
dan menghitung jarakmu ke tiap bus (Live TJ).

**Ke mana perginya.** Tidak ke mana-mana. Koordinat diproses di memori perangkat lalu digantikan
pembacaan berikutnya. Koordinat lokasimu **tidak disimpan secara permanen** dan **tidak pernah
dikirim keluar perangkat**. Alarm di fitur Live TJ bahkan tidak memakai lokasimu sama sekali:
alarm itu dihitung dari posisi bus yang dilaporkan Transjakarta.

**Saat layar mati.** Pemantauan berlanjut melalui *foreground service* dengan notifikasi yang
selalu terlihat selama sesi aktif. Kamu selalu bisa tahu kapan WakeMate sedang memantau, dan bisa
menghentikannya kapan saja dari notifikasi itu.

### 1.2 Data yang disimpan di perangkatmu

Tersimpan secara lokal, tidak pernah dikirim keluar:

| Data | Isi | Kegunaan |
|---|---|---|
| Riwayat perjalanan | Stasiun tujuan, jalur, ambang alarm, waktu mulai dan selesai, status sesi | Memulihkan sesi yang terputus, dan riwayat pribadimu |
| Alarm Live TJ yang sedang aktif | Rute, halte, nomor bus yang kamu pilih, ambang alarm, waktu mulai | Melanjutkan alarm kalau sistem menghentikan aplikasi di tengah jalan; dihapus begitu alarm selesai atau dibatalkan |
| Pengaturan | Preferensi penggunaan speaker, versi data stasiun | Mengingat pilihanmu |
| Data rute, stasiun, dan halte | Daftar rute, stasiun, halte, dan koordinatnya | Disertakan bersama aplikasi, bukan data tentang dirimu |

Riwayat perjalanan memuat **stasiun tujuanmu beserta waktunya**, yang merupakan informasi tentang
kebiasaan perjalananmu. Data tersebut hanya berada di perangkatmu dan dapat kamu hapus kapan saja
(lihat bagian 6).

### 1.3 Yang tidak diakses sama sekali

Nama, alamat email, nomor telepon, kontak, kalender, kamera, mikrofon, foto, daftar aplikasi
terpasang, pengenal iklan, dan riwayat penjelajahan. WakeMate tidak meminta satu pun di antaranya.

---

## 2. Koneksi internet

Internet hanya dipakai oleh fitur **Live TJ**, dan hanya ke dua tujuan berikut. Semua koneksi
terenkripsi (TLS).

| Tujuan | Untuk apa | Yang diterima server itu dari perangkatmu |
|---|---|---|
| Server data armada Transjakarta (`mqtt.tj.co.id`) | Menerima posisi dan perkiraan tiba bus pada rute yang sedang kamu buka | Alamat IP-mu, kode rute yang sedang kamu lihat, dan pengenal koneksi acak yang dibuat ulang setiap kali tersambung |
| OpenFreeMap (`tiles.openfreemap.org`) | Mengunduh gambar peta | Alamat IP-mu dan area peta yang sedang ditampilkan |

Beberapa hal yang perlu kamu ketahui:

- **Koneksi ini satu arah.** WakeMate hanya *menerima* data dari server Transjakarta, tidak
  mengirim apa pun selain permintaan berlangganan rute.
- **Lokasimu tidak ikut terkirim.** Area peta yang diunduh adalah area rute yang kamu buka, bukan
  posisimu, dan WakeMate tidak menampilkan titik lokasimu di peta.
- **Alamat IP** pasti terlihat oleh server mana pun yang dihubungi perangkat, sama seperti saat
  membuka situs web. WakeMate tidak menambahkan informasi lain tentang dirimu.
- Server data armada adalah milik **PT Transportasi Jakarta** dan bukan layanan resmi untuk
  pengembang pihak ketiga. WakeMate tidak berafiliasi dengan Transjakarta. Ketersediaan data live
  dapat berubah sewaktu-waktu di luar kendali kami.
- Koneksi hanya dibuka saat kamu membuka halaman rute di Live TJ atau saat alarm Live TJ sedang
  berjalan, dan ditutup sendiri setelahnya.

---

## 3. Izin yang diminta dan alasannya

| Izin | Alasan |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Menghitung posisimu terhadap stasiun tujuan (Alarm GPS) dan mengurutkan bus terdekat (Live TJ) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Menjaga sesi Alarm GPS tetap hidup sepanjang perjalanan, termasuk saat layar terkunci. WakeMate **tidak** meminta izin lokasi latar belakang: lokasi hanya dibaca selama sesi yang kamu mulai sendiri, dengan notifikasi yang selalu terlihat |
| `FOREGROUND_SERVICE_DATA_SYNC` | Menjaga alarm Live TJ tetap menerima posisi bus saat layar mati |
| `INTERNET` | Menerima posisi bus live dan mengunduh peta (lihat bagian 2) |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Dipakai pustaka peta MapLibre untuk mengetahui apakah perangkat sedang tersambung ke internet |
| `POST_NOTIFICATIONS` | Menampilkan status sesi dan membunyikan pengingat |
| `VIBRATE` | Menggetarkan ponsel saat alarm berbunyi |

Menolak izin lokasi berarti Alarm GPS tidak dapat berfungsi, karena tidak ada cara lain untuk
mengetahui kamu sudah sampai mana. Fitur Live TJ tetap dapat dipakai tanpa izin lokasi.

---

## 4. Pihak ketiga

- **Google Play Services (Location)** dari Google dipakai untuk memperoleh pembacaan lokasi.
  Layanan ini merupakan bagian dari sistem Android di perangkatmu. Cara Google memproses lokasi
  sebagai penyedia layanan tersebut tunduk pada Kebijakan Privasi Google:
  https://policies.google.com/privacy
- **PT Transportasi Jakarta** mengoperasikan server data armada yang dihubungi fitur Live TJ
  (lihat bagian 2).
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
adalah yang dijelaskan di bagian 2, dan itu dikirim langsung oleh perangkatmu ke penyedia data
rute dan peta, bukan melalui kami.

---

## 6. Penyimpanan dan penghapusan

Data hanya bertahan selama aplikasi terpasang di perangkatmu.

- **Menghapus riwayat dan pengaturan:** Setelan Android → Aplikasi → WakeMate → Penyimpanan →
  Hapus data
- **Menghapus seluruhnya:** copot pemasangan aplikasi

Karena kami tidak memiliki server, tidak ada data milikmu yang tersimpan di pihak kami, dan tidak
ada permintaan penghapusan yang perlu kamu ajukan kepada kami. Untuk data yang mungkin dicatat
oleh Transjakarta atau OpenFreeMap saat perangkatmu terhubung (misalnya log alamat IP), berlaku
kebijakan masing-masing penyedia tersebut.

---

## 7. Anak-anak

WakeMate tidak ditujukan untuk anak di bawah 13 tahun dan tidak mengumpulkan data dari mereka
secara sengaja. Aplikasi ini juga tidak mengumpulkan data yang dapat mengidentifikasi seseorang
dari pengguna mana pun.

---

## 8. Keamanan

Data disimpan di penyimpanan privat aplikasi, yang dilindungi sistem Android dan tidak dapat
dibaca aplikasi lain. Koneksi ke server data armada dan server peta dienkripsi dengan TLS.

---

## 9. Sumber data

- **Stasiun KRL, MRT, dan koridor BRT untuk Alarm GPS:** **OpenStreetMap**, tersedia di bawah
  lisensi Open Database License (ODbL). © Kontributor OpenStreetMap.
- **Rute, halte, dan jadwal untuk Live TJ:** data GTFS yang dipublikasikan Transjakarta di
  gtfs.transjakarta.co.id.
- **Peta:** OpenFreeMap, dengan data © Kontributor OpenStreetMap dan OpenMapTiles.
- **Posisi bus live:** server data armada Transjakarta (lihat bagian 2).

Data rute dan stasiun disertakan bersama aplikasi dan bukan merupakan data tentang dirimu.

---

## 10. Perubahan kebijakan

Bila kebijakan ini berubah, tanggal "Terakhir diperbarui" di atas akan disesuaikan. Perubahan yang
memengaruhi cara data diperlakukan akan diumumkan di dalam aplikasi sebelum berlaku.

**Perubahan 12 Oktober 2026:** penambahan fitur Live TJ. Aplikasi kini memakai koneksi internet
untuk menerima posisi bus dan mengunduh peta (bagian 2), dan izin terkait ditambahkan (bagian 3).
Pemrosesan lokasimu tetap sepenuhnya di dalam perangkat.

---

## 11. Kontak

Pertanyaan mengenai kebijakan ini dapat dikirim ke: **developedbyadifens@gmail.com**
