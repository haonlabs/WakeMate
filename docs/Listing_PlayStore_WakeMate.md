# Teks Listing Play Store — WakeMate

Batas Play: short description **80 karakter**, full description **4.000 karakter**.

**Ditulis ulang 5 November 2026** untuk redesign "Signal Pop", Peta Rute multimoda, tab Sekitar, dan
alarm "Bangunin". Fitur bus live Transjakarta sudah dihapus (feed-nya ditolak broker), jadi semua
klaim "posisi bus langsung", "Saya di bus ini", dan "pengingat bus hampir tiba" dibuang.

Dua hal yang wajib tetap jujur di listing:

1. **Tidak ada posisi kendaraan nyata.** Kereta di peta KRL adalah simulasi dari jadwal, dan label
   itu harus ikut tertulis di teks listing. Play menolak listing yang menyesatkan.
2. **Bukan aplikasi resmi.** WakeMate memakai nama Transjakarta, KRL, MRT, dan LRT dan data
   pihak ketiga, jadi pernyataan independen dan atribusi CC-BY (Open-KRL) harus ada.

Screenshot: `design/playstore/screenshots-v3/01..08` (1080×1920). Ikon `design/playstore/icon-512.png`,
feature graphic `design/playstore/feature-graphic-v3.png`.

---

## Short description

**Pilihan A (disarankan): tiga fungsi sekaligus**

```
Rute KRL, MRT, LRT, TJ di sekitarmu, plus alarm turun yang bangunin kamu.
```

**Pilihan B: menonjolkan tidur tenang**

```
Tidur tenang di KRL, MRT, LRT, dan bus. Dibangunin sebelum stasiun tujuan.
```

**Pilihan C: menonjolkan privasi**

```
Alarm turun KRL, MRT, LRT, TJ. Tanpa akun, tanpa iklan, lokasi tetap di ponsel.
```

---

## Full description

```
Ketiduran di KRL lalu kebablasan itu menyebalkan. Bingung bus atau kereta apa yang lewat di dekatmu juga sama menyebalkannya.

WakeMate menjawab keduanya: lihat transportasi di sekitarmu, buka petanya, lalu tidur tenang karena kami bangunin sebelum sampai tujuan.


SEKITAR: APA SAJA YANG LEWAT DI DEKATMU

• Halte dan stasiun terdekat, lengkap dengan jarak dan waktu jalan kaki
• Rute apa saja yang lewat, dan kendaraan berikutnya menurut jadwal
• Atur jangkauan 500 m sampai 5 km, saring per moda: bus, KRL, MRT, LRT
• Ketuk satu titik untuk melihat semua rute dan tujuannya


PETA RUTE: KRL, MRT, LRT, DAN TRANSJAKARTA

• 251 rute dalam satu aplikasi: 6 lin KRL Commuter Line, MRT Jakarta, 3 rute LRT (LRT Jakarta, LRT Jabodebek Cibubur dan Bekasi), serta Transjakarta (BRT, Angkutan Umum Integrasi, Mikrotrans, Transjabodetabek, Royaltrans, Rusun, Bus Wisata)
• Jalur dan semua halte atau stasiun tergambar di peta, per arah perjalanan
• Perkiraan kereta atau bus berikutnya di tiap halte, menurut jadwal
• Di peta KRL ada kereta simulasi yang bergerak menurut jadwal. Ini bukan posisi nyata kereta, dan ditandai jelas di peta
• Simpan rute favorit, dan rute yang terakhir dibuka mudah dicari lagi


BANGUNIN: ALARM TURUN BERBASIS GPS

Pilih stasiun atau halte tujuan, tentukan mau dibangunkan berapa stasiun sebelumnya, lalu simpan ponsel di saku. Dari peta rute, tinggal ketuk "Bangunkan saya di sini" di stasiun tujuanmu.

• Untuk KRL, MRT, LRT, dan koridor BRT
• Jalan tanpa internet, cukup GPS ponsel
• Tahu arah perjalananmu, dan memperingatkan kalau kamu naik jalur yang salah
• Tetap menghitung saat sinyal GPS hilang di terowongan
• Hemat baterai: pelacakan dipertajam hanya saat mendekati tujuan
• Menunjukkan jarak sebenarnya tiap pilihan, karena "2 stasiun" di Lin Bogor bisa belasan kilometer


ALARM YANG SOPAN DI TEMPAT UMUM

Alarm yang meraung di gerbong penuh membuat orang berhenti memakainya. WakeMate dirancang bertahap:

• Dimulai dari getaran
• Bunyi diarahkan ke headset atau TWS kalau tersambung, bukan ke speaker
• Speaker hanya dipakai sebagai upaya terakhir, dan bisa kamu matikan sepenuhnya

Alarm tetap berbunyi meski ponsel dalam mode senyap karena memakai jalur alarm sistem, sama seperti alarm bangun tidur. Sesi berhenti sendiri kalau ditinggalkan, supaya baterai tidak terkuras.


PRIVASI

• Lokasimu diproses di dalam ponsel dan tidak dikirim WakeMate ke mana pun
• Lokasi hanya dipakai saat aplikasi terbuka atau saat alarm perjalanan aktif
• Internet hanya untuk gambar peta (OpenFreeMap). Di tab Sekitar, area peta yang diunduh ada di sekitar posisimu
• Tanpa akun, tanpa iklan, tanpa analitik, tanpa pelacakan
• Notifikasi selalu terlihat selama alarm aktif, jadi kamu tahu persis kapan WakeMate bekerja


YANG PERLU DIKETAHUI

• Semua waktu kedatangan adalah perkiraan menurut jadwal, bukan posisi kendaraan secara langsung. Jadwal bisa berubah tanpa pemberitahuan.
• Jadwal KRL berlaku untuk hari kerja dan Minggu, sampai 31 Desember 2026. Jadwal hari Sabtu belum tersedia, dan aplikasi akan bilang begitu, bukan menebak.
• MRT dan LRT belum punya jadwal di aplikasi. Rutenya tetap bisa dilihat, dan alarm tetap bekerja.

WakeMate adalah aplikasi independen dan tidak berafiliasi dengan PT Transportasi Jakarta, KAI Commuter, MRT Jakarta, maupun LRT Jakarta dan LRT Jabodebek.

Data KRL: KRL Commuter Line GTFS data compiled and verified by Open-KRL (CC-BY 4.0). Data rute dan halte Transjakarta dari GTFS yang dipublikasikan Transjakarta. Data stasiun, jalur, dan peta © kontributor OpenStreetMap (ODbL). Peta oleh OpenFreeMap.
```

---

## Release name

Hanya terlihat olehmu di Play Console. Batas 50 karakter. Biarkan default `versionName (versionCode)`
supaya cocok dengan berkas `.aab`. Rilis ini: `1.2 (4)`. `1.1 (3)` sudah pernah diajukan ke Play (ditolak soal feature graphic,
banding diajukan 12 Okt), jadi `versionCode` wajib naik.

---

## Release notes (What's new)

**Terlihat oleh pengguna.** Batas 500 karakter per bahasa.

**Untuk pengguna lama (id-ID)**

```
Tampilan baru dan fitur baru:

• Tab Sekitar: halte dan stasiun terdekat beserta rute yang lewat
• Peta Rute: KRL, MRT, LRT, dan Transjakarta dalam satu peta, dengan perkiraan tiba menurut jadwal
• Alarm GPS berganti nama jadi Bangunin, sekarang termasuk LRT
• Ketuk "Bangunkan saya di sini" langsung dari peta rute
• Logo baru

Posisi bus langsung dihapus karena datanya tidak lagi tersedia.
```

**Kalau ini rilis pertama yang tayang (id-ID)**

```
Rilis pertama WakeMate.

Lihat KRL, MRT, LRT, dan bus Transjakarta di sekitarmu, buka peta rutenya, lalu tidur tenang. Pilih stasiun tujuan, WakeMate membangunkanmu sebelum sampai: getar dulu, lewat headset kalau tersambung, speaker hanya sebagai upaya terakhir.

Alarm GPS jalan tanpa internet. Tanpa akun, tanpa iklan.
```

**Pola untuk rilis berikutnya**

Tulis dari sudut pandang pengguna, bukan changelog teknis. Sebut yang mereka rasakan, bukan nama
kelas yang diubah.

---

## Pengingat perawatan

- Jadwal KRL **kedaluwarsa 31 Des 2026**. Setelah itu aplikasi menyembunyikan ETA dan kereta
  simulasi dan menampilkan pesan. Perbarui lewat `tools/generate_rail_static.py` dengan rilis
  Open-KRL yang baru, lalu rilis versi baru sebelum tanggal itu.
- Daftar tampilan (screenshot) memakai build debug di emulator. Tombol "Audio" khusus debug sudah
  ditutup di `07-bangunin.png`; build rilis memang tidak menampilkannya.
