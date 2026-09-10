#!/usr/bin/env python3
"""Generate app/src/test/resources/tracks/*.json — geometri jalur asli dari OpenStreetMap.

Berbeda dari stations_seed.json yang hanya berisi titik stasiun, file ini berisi lengkung rel /
koridor yang sebenarnya, dirangkai dari way anggota relasi rute. Dipakai unit test sebagai
"kebenaran lapangan" untuk menguji ProximityCalculator, yang di dalamnya justru memodelkan rute
sebagai garis lurus antar stasiun — jadi testnya mengukur galat pendekatan itu, bukan mengulanginya.

Usage: python3 tools/generate_track_fixtures.py
"""
import json, math, pathlib, sys, time, urllib.parse, urllib.request

OVERPASS = "https://overpass-api.de/api/interpreter"
OUT_DIR = pathlib.Path("app/src/test/resources/tracks")

# Dipilih untuk tiga karakter yang berbeda: Bogor paling panjang dan berkelok (galat garis lurus
# paling besar), MRT punya terowongan, Koridor 1 punya halte paling rapat.
LINES = {
    16877210: ("krl-bogor", "KRL"),
    9677669: ("mrt-north-south", "MRT"),
    16738414: ("tj-koridor-1", "TRANSJAKARTA"),
}
RESAMPLE_METERS = 25.0
METERS_PER_DEGREE = 111_194.93


def overpass(query):
    body = urllib.parse.urlencode({"data": query}).encode()
    headers = {"User-Agent": "WakeMate-track-fixtures/1.0"}
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(OVERPASS, body, headers), timeout=300) as r:
                return json.load(r)
        except Exception as exc:
            if attempt == 2:
                raise
            print(f"  overpass retry {attempt + 1}: {exc}", file=sys.stderr)
            time.sleep(20)


def key(point):
    return (round(point[0], 7), round(point[1], 7))


def stitch(ways):
    """Way dalam relasi rute berbagi node di ujungnya, tapi arahnya bisa terbalik satu-satu."""
    ways = [w for w in ways if len(w) >= 2]
    line, used = list(ways[0]), {0}
    while True:
        for i, way in enumerate(ways):
            if i in used:
                continue
            for candidate in (way, way[::-1]):
                if key(line[-1]) == key(candidate[0]):
                    line.extend(candidate[1:])
                    break
                if key(line[0]) == key(candidate[-1]):
                    line[:0] = candidate[:-1]
                    break
            else:
                continue
            used.add(i)
            break
        else:
            return line, len(used), len(ways)


def resample(line, spacing):
    """Jarak antar titik dibuat seragam supaya kecepatan simulasi bisa dikendalikan test."""
    cos_lat = math.cos(math.radians(line[0][0]))
    out, carry = [line[0]], 0.0
    for a, b in zip(line, line[1:]):
        dx = (b[1] - a[1]) * METERS_PER_DEGREE * cos_lat
        dy = (b[0] - a[0]) * METERS_PER_DEGREE
        segment = math.hypot(dx, dy)
        if segment == 0:
            continue
        travelled = spacing - carry
        while travelled <= segment:
            t = travelled / segment
            out.append((a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t))
            travelled += spacing
        carry = (carry + segment) % spacing
    out.append(line[-1])
    return out


def main():
    ids = ",".join(str(i) for i in LINES)
    data = overpass(f"[out:json][timeout:300];rel(id:{ids})->.r;.r out geom;node(r.r);out body;")
    relations = {e["id"]: e for e in data["elements"] if e["type"] == "relation"}
    names = {
        e["id"]: (e.get("tags", {}).get("name") or e.get("tags", {}).get("alt_name"))
        for e in data["elements"] if e["type"] == "node" and e.get("tags", {}).get("name")
    }

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for rel_id, (line_id, mode) in LINES.items():
        relation = relations[rel_id]
        members = relation["members"]

        stops, seen = [], None
        for role in ("stop", "platform"):
            picked = [m for m in members if m["type"] == "node"
                      and m.get("role", "").startswith(role) and "lat" in m and m["ref"] in names]
            if picked:
                break
        for m in picked:
            name = names[m["ref"]].removeprefix("Stasiun ").removeprefix("Halte ").strip()
            if name == seen:
                continue
            seen = name
            stops.append({"name": name, "lat": round(m["lat"], 6), "lng": round(m["lon"], 6)})

        ways = [[(p["lat"], p["lon"]) for p in m["geometry"]]
                for m in members if m["type"] == "way" and m.get("geometry")]
        line, joined, total = stitch(ways)
        track = resample(line, RESAMPLE_METERS)

        used, stations = {}, []
        for i, s in enumerate(stops):
            base = f"{line_id}:{s['name'].lower().replace(' ', '-')}"
            used[base] = used.get(base, 0) + 1
            sid = base if used[base] == 1 else f"{base}-{used[base]}"
            stations.append({"id": sid, "name": s["name"], "lat": s["lat"], "lng": s["lng"], "sequence": i})

        payload = {
            "lineId": line_id,
            "mode": mode,
            "stations": stations,
            "track": [[round(p[0], 6), round(p[1], 6)] for p in track],
        }
        path = OUT_DIR / f"{line_id}.json"
        path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
        print(f"  {line_id}: {len(stations)} stasiun, {len(track)} titik jalur "
              f"({joined}/{total} way tersambung), {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
