# tools/data

`krl_gtfs_v2.zip` — GTFS KRL Commuter Line, **bukan buatan WakeMate**.

- Sumber: Open-KRL pipeline (https://github.com/open-krl/pipeline, commit `2055d81`), yang menyusun jadwal dari
  papan keberangkatan stasiun KCI (kci.id). Independen, tidak berafiliasi dengan KCI/KAI/Kemenhub.
- Lisensi data: **CC-BY 4.0**. Atribusi wajib: "KRL Commuter Line GTFS data compiled and verified by Open-KRL."
  Koordinat stasiun © kontributor OpenStreetMap (ODbL).
- Cakupan: snapshot hari kerja + Minggu (v2/1..7). **Sabtu tidak ada.** Kalender berlaku 2026-09-21 s.d. 2026-12-31.
- Dibangun offline dari snapshot mentah (tanpa menyentuh API KCI):

  ```
  git clone https://github.com/open-krl/pipeline && cd pipeline && bun install --frozen-lockfile
  bun src/cli/index.ts build 2 && bun src/cli/index.ts export 2   # -> data/build/krl_gtfs_v2.zip
  ```
- Checksum: lihat `krl_gtfs_v2.zip.sha256`. Perbarui berkas ini (dan jalankan ulang
  `tools/generate_rail_static.py`) saat Open-KRL merilis snapshot baru atau kalender mendekati habis.
