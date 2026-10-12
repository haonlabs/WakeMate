# Teks Listing Play Store — WakeMate

Batas Play: short description **80 karakter**, full description **4.000 karakter**.

**Diperbarui 12 Oktober 2026** untuk fitur Live TJ. Klaim lama "tanpa internet" dan "tidak meminta
izin INTERNET" sudah tidak berlaku dan dihapus dari semua teks di bawah.

Karena teks ini menyebut Transjakarta dan memakai data armadanya, listing perlu menyatakan dengan
jelas bahwa WakeMate **bukan aplikasi resmi Transjakarta**. Kebijakan Play melarang listing yang
bisa disangka berasal dari pihak lain.

---

## Short description

**Pilihan A (disarankan): tracker dan alarm sekaligus**

```
Bus Transjakarta live dan alarm sebelum halte tujuan. KRL dan MRT juga bisa.
```

**Pilihan B: menonjolkan tidur tenang**

```
Tidur tenang di bus, KRL, dan MRT. Dibangunkan sebelum halte/stasiun tujuan.
```

**Pilihan C: menonjolkan sisi privasi**

```
Bus TJ live dan alarm turun. Tanpa akun, tanpa iklan, lokasimu tetap di ponsel.
```

---

## Full description

```
Kamu ingin tidur di perjalanan, tapi takut kebablasan. Kamu juga ingin tahu bus berikutnya masih seberapa jauh, tanpa menebak-nebak di halte.

WakeMate menunjukkan posisi bus Transjakarta secara langsung, lalu membangunkanmu sebelum sampai di halte atau stasiun tujuan.


BUS TRANSJAKARTA SECARA LANGSUNG

• Posisi bus di peta, diperbarui terus-menerus
• Perkiraan tiba di setiap halte, sesuai arah perjalanan
• Bus terdekat darimu ditampilkan paling depan
• 240 rute: BRT, Angkutan Umum Integrasi, Mikrotrans, Transjabodetabek, Rusun, Royaltrans, dan Bus Wisata


BANGUNKAN AKU DARI BUS YANG KUNAIKI

Pilih bus yang sedang kamu naiki, ketuk halte tempat kamu turun, lalu tentukan kapan dibangunkan: beberapa halte atau beberapa menit sebelumnya. WakeMate memantau bus itu dan membangunkanmu tepat waktu, bahkan saat layar terkunci.


INGATKAN SAAT BUS HAMPIR TIBA

Menunggu di halte? Pasang pengingat, lalu lakukan hal lain. WakeMate memberi tahu saat bus ke arahmu tinggal beberapa menit lagi.


ALARM GPS UNTUK KRL DAN MRT

Untuk KRL Commuter Line, MRT Jakarta, dan koridor BRT, WakeMate juga bisa memantau posisimu lewat GPS. Pilih stasiun tujuan, tentukan mau dibangunkan berapa stasiun sebelumnya, lalu simpan ponsel di saku. Fitur ini tetap bekerja tanpa internet.

• Tahu arah perjalananmu, dan memperingatkan kalau kamu naik jalur yang salah
• Tetap menghitung saat sinyal GPS hilang di terowongan
• Menghemat baterai dengan mempertajam pelacakan hanya saat mendekati tujuan
• Menunjukkan jarak sebenarnya dari tiap pilihan ambang, karena "2 stasiun" di Lin Bogor berarti belasan kilometer


ALARM YANG SOPAN DI TEMPAT UMUM

Alarm yang meraung di gerbong penuh membuat orang berhenti memakainya. WakeMate dirancang sebaliknya, bertahap:

• Dimulai dari getaran
• Bunyi diarahkan ke headset atau TWS kalau tersambung, bukan ke speaker
• Speaker hanya dipakai sebagai upaya terakhir, dan bisa kamu matikan sepenuhnya

Alarm tetap berbunyi meski ponsel dalam mode senyap, karena memakai jalur alarm sistem, sama seperti alarm bangun tidur. Sesi juga berhenti sendiri kalau ditinggalkan, supaya baterai tidak terkuras.


PRIVASI

• Lokasimu diproses di dalam ponsel dan tidak pernah dikirim ke mana pun
• Internet hanya dipakai untuk menerima posisi bus dan gambar peta
• Alarm dari bus yang kamu naiki tidak memakai lokasimu sama sekali
• Tanpa akun, tanpa iklan, tanpa analitik, tanpa pelacakan
• Notifikasi selalu terlihat selama pemantauan aktif, jadi kamu tahu persis kapan WakeMate bekerja


CAKUPAN SAAT INI

Transjakarta (live): 240 rute
KRL Commuter Line (GPS): Lin Bogor, Lin Bogor (Nambo), Lin Lingkar Cikarang, Lin Rangkasbitung, Lin Tangerang, Lin Tanjung Priuk, dan Lin Bandara Soekarno-Hatta
MRT Jakarta (GPS): Lin Utara-Selatan, Lebak Bulus sampai Bundaran HI


YANG PERLU DIKETAHUI

• Data bus langsung berasal dari Transjakarta. Kalau datanya sedang tidak tersedia, rute dan halte tetap bisa dilihat, dan alarm memberi tahu kalau berhenti.
• Posisi kereta KRL dan MRT secara langsung belum tersedia.

WakeMate adalah aplikasi independen dan tidak berafiliasi dengan PT Transportasi Jakarta, KAI Commuter, maupun MRT Jakarta.

Data rute dan halte Transjakarta dari GTFS yang dipublikasikan Transjakarta. Data stasiun dan peta © kontributor OpenStreetMap (ODbL). Peta oleh OpenFreeMap.
```

---

## Release name

Hanya terlihat olehmu di Play Console, tidak pernah ditampilkan ke pengguna. Batas 50 karakter.
Google mengisinya otomatis dari `versionName (versionCode)`. Biarkan sesuai default supaya mudah
dicocokkan dengan berkas `.aab` yang diunggah.

Setiap unggahan baru wajib memakai `versionCode` yang lebih tinggi. Saat ini masih `1` / `1.0` di
`app/build.gradle.kts`, jadi naikkan dulu, misalnya ke:

```
1.1 (2)
```

---

## Release notes (What's new)

**Terlihat oleh pengguna** di halaman aplikasi dan tab Update. Batas 500 karakter per bahasa.

**Kalau versi 1.0 sudah pernah tayang ke pengguna (id-ID)**

```
Yang baru di 1.1

• Bus Transjakarta secara langsung: posisi bus di peta dan perkiraan tiba di tiap halte, untuk 240 rute
• Bangunkan aku dari bus yang kunaiki: pilih bus dan halte turun, alarm berbunyi sebelum sampai
• Pengingat saat bus ke arahmu hampir tiba di halte

Alarm GPS untuk KRL dan MRT tetap ada di tab Alarm GPS, dan tetap bekerja tanpa internet.
```

**Kalau ini rilis pertama yang tayang (id-ID)**

```
Rilis pertama WakeMate.

Lihat posisi bus Transjakarta secara langsung, lalu tidur tenang. Pilih bus yang kamu naiki dan halte tujuan, WakeMate membangunkanmu sebelum sampai: getar dulu, lewat headset kalau tersambung, speaker hanya sebagai upaya terakhir.

Untuk KRL dan MRT tersedia alarm GPS yang bekerja tanpa internet. Tanpa akun, tanpa iklan.
```

**Pola untuk rilis berikutnya**

Tulis dari sudut pandang pengguna, bukan changelog teknis. Sebut yang mereka rasakan, bukan nama
kelas yang diubah.
