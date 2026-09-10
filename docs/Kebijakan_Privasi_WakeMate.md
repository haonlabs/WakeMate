# Kebijakan Privasi WakeMate

**Berlaku sejak:** 10 September 2026
**Terakhir diperbarui:** 10 September 2026
**Aplikasi:** WakeMate (`id.haonlabs.wakemate`)
**Pengembang:** [ISI NAMA PENGEMBANG / HAON LABS]
**Kontak:** [ISI ALAMAT EMAIL KONTAK]

---

## Ringkasan singkat

WakeMate membangunkanmu sebelum tiba di stasiun atau halte tujuan. Untuk itu aplikasi perlu
mengetahui posisimu selama perjalanan berlangsung.

Seluruh pemrosesan lokasi terjadi **di dalam perangkatmu**. WakeMate tidak mengirim data apa pun
ke server mana pun — dan bukan sekadar berjanji demikian: aplikasi ini **tidak meminta izin
`INTERNET`**, sehingga secara teknis tidak memiliki kemampuan mengirim data keluar dari
perangkatmu. Hal ini dapat diperiksa sendiri oleh siapa pun pada berkas manifest aplikasi.

Tidak ada akun, tidak ada iklan, tidak ada pelacakan, dan tidak ada analitik pihak ketiga.

---

## 1. Data yang diakses

### 1.1 Lokasi perangkat

**Kapan diakses.** Hanya ketika kamu menekan "Mulai perjalanan" dan sesi pemantauan sedang
berjalan. Di luar sesi aktif, WakeMate tidak meminta lokasi sama sekali.

**Untuk apa.** Satu-satunya kegunaannya adalah menghitung posisimu terhadap urutan stasiun pada
jalur yang kamu pilih, sehingga aplikasi tahu kapan harus membunyikan alarm.

**Ke mana perginya.** Tidak ke mana-mana. Koordinat diproses di memori perangkat, dipakai untuk
menghitung sisa jarak, lalu digantikan pembacaan berikutnya. Koordinat lokasimu **tidak disimpan
secara permanen** dan **tidak pernah dikirim keluar perangkat**.

**Saat layar mati.** Pemantauan berlanjut melalui *foreground service* dengan notifikasi permanen
yang selalu terlihat selama sesi aktif. Kamu selalu bisa tahu kapan WakeMate sedang memantau, dan
bisa menghentikannya kapan saja.

### 1.2 Data yang disimpan di perangkatmu

Tersimpan secara lokal, tidak pernah dikirim keluar:

| Data | Isi | Kegunaan |
|---|---|---|
| Riwayat perjalanan | Stasiun tujuan, jalur, ambang alarm, waktu mulai dan selesai, status sesi | Memulihkan sesi yang terputus, dan riwayat pribadimu |
| Pengaturan | Preferensi penggunaan speaker, versi data stasiun | Mengingat pilihanmu |
| Data stasiun | Daftar stasiun dan halte beserta koordinatnya | Disertakan bersama aplikasi, bukan data tentang dirimu |

Perlu dicatat: riwayat perjalanan memuat **stasiun tujuanmu beserta waktunya**. Ini adalah
informasi tentang kebiasaan perjalananmu. Data tersebut hanya berada di perangkatmu dan dapat
kamu hapus kapan saja (lihat bagian 5).

### 1.3 Yang tidak diakses sama sekali

Nama, alamat email, nomor telepon, kontak, kalender, kamera, mikrofon, foto, daftar aplikasi
terpasang, pengenal iklan, dan riwayat penjelajahan. WakeMate tidak meminta satu pun di antaranya.

---

## 2. Izin yang diminta dan alasannya

| Izin | Alasan |
|---|---|
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Menghitung posisimu terhadap stasiun tujuan |
| `ACCESS_BACKGROUND_LOCATION` | Melanjutkan pemantauan saat layar terkunci dan ponsel di dalam saku |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Menjaga sesi tetap hidup sepanjang perjalanan |
| `POST_NOTIFICATIONS` | Menampilkan status sesi dan membunyikan pengingat |
| `VIBRATE` | Menggetarkan ponsel saat alarm berbunyi |

Menolak izin lokasi berarti WakeMate tidak dapat berfungsi, karena tidak ada cara lain untuk
mengetahui kamu sudah sampai mana.

---

## 3. Pihak ketiga

WakeMate memakai **Google Play Services (Location)** dari Google untuk memperoleh pembacaan
lokasi. Layanan ini merupakan bagian dari sistem Android di perangkatmu. Cara Google memproses
lokasi sebagai penyedia layanan tersebut tunduk pada Kebijakan Privasi Google:
https://policies.google.com/privacy

Selain itu, tidak ada pustaka pihak ketiga yang mengumpulkan data: tidak ada analitik, tidak ada
pelaporan crash, tidak ada jaringan iklan, tidak ada SDK atribusi.

---

## 4. Berbagi dan penjualan data

WakeMate **tidak membagikan, menjual, menyewakan, atau memperdagangkan** data apa pun, kepada
siapa pun, untuk tujuan apa pun. Tidak ada data yang meninggalkan perangkatmu, sehingga tidak ada
yang bisa dibagikan.

---

## 5. Penyimpanan dan penghapusan

Data hanya bertahan selama aplikasi terpasang di perangkatmu.

- **Menghapus riwayat dan pengaturan:** Setelan Android → Aplikasi → WakeMate → Penyimpanan →
  Hapus data
- **Menghapus seluruhnya:** copot pemasangan aplikasi

Karena tidak ada server, tidak ada data milikmu yang tersimpan di tempat lain, dan tidak ada
permintaan penghapusan yang perlu kamu ajukan kepada kami.

---

## 6. Anak-anak

WakeMate tidak ditujukan untuk anak di bawah 13 tahun dan tidak mengumpulkan data dari mereka
secara sengaja. Aplikasi ini juga tidak mengumpulkan data yang dapat mengidentifikasi seseorang
dari pengguna mana pun.

---

## 7. Keamanan

Data disimpan di penyimpanan privat aplikasi, yang dilindungi sistem Android dan tidak dapat
dibaca aplikasi lain. Karena tidak ada pengiriman data melalui jaringan, tidak ada risiko
penyadapan di perjalanan.

---

## 8. Sumber data stasiun

Koordinat stasiun dan halte berasal dari **OpenStreetMap**, tersedia di bawah lisensi
Open Database License (ODbL). © Kontributor OpenStreetMap. Data ini disertakan bersama aplikasi
dan bukan merupakan data tentang dirimu.

---

## 9. Perubahan kebijakan

Bila kebijakan ini berubah, tanggal "Terakhir diperbarui" di atas akan disesuaikan. Perubahan yang
memengaruhi cara data diperlakukan akan diumumkan di dalam aplikasi sebelum berlaku.

---

## 10. Kontak

Pertanyaan mengenai kebijakan ini dapat dikirim ke: **[ISI ALAMAT EMAIL KONTAK]**
