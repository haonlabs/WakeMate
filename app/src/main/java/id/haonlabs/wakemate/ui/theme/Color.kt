package id.haonlabs.wakemate.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palet "Signal Pop" dari file Figma "WakeMate — Redesign UI/UX" (halaman v2, koleksi variabel
 * `v2 Terang` dan `v2 Gelap`). Nama token di sini sama dengan nama variabel di Figma.
 *
 * Gaya poster: krem + tinta, blok warna besar, border tebal dan bayangan keras. Mint = informasi
 * live dan aksi utama, amber = khusus hal soal alarm, di kedua tema. Semua teks di atas blok
 * berwarna memakai [OnColor] (tinta) — rasio kontras ≥ 6 untuk semua pasangan, sudah dicek.
 */

// ---- Tidak bergantung tema
val Live = Color(0xFF2BD9A0)
val Alarm = Color(0xFFFFB020)
val OnColor = Color(0xFF14110F)
val SignalRed = Color(0xFFFF5A4D)
val SignalYellow = Color(0xFFFFD21F)
val SignalBlue = Color(0xFF7C93FF)

// ---- Terang (krem)
val Cream = Color(0xFFFFF3DC)
val CreamSunken = Color(0xFFFFE3B0)
val Paper = Color(0xFFFFFFFF)
val Ink = Color(0xFF14110F)
val InkMuted = Color(0xFF5E554A)

// ---- Gelap (tinta)
val InkBg = Color(0xFF14110F)
val InkSurface = Color(0xFF221E1B)
val InkSunken = Color(0xFF2E2925)
val CreamText = Color(0xFFFFF3DC)
val CreamMuted = Color(0xFFBDB2A2)
