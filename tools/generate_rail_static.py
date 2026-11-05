#!/usr/bin/env python3
"""Generate app/src/main/assets/rail_static.json: KRL, MRT, and LRT in the same schema as tj_static.json.

No operator publishes live positions for these modes, so this file only carries the *static* map and
whatever timetable is openly available:

- KRL: Open-KRL GTFS (CC-BY 4.0, see tools/data/README.md). Trips carry absolute `arr` times and a
  `days` bitmask, no `freq`. The source GTFS has no shapes, so `shape` is a straight line through the
  stations. Saturday has no data at all (`days` has bit 5 clear for every trip).
- MRT / LRT Jakarta / LRT Jabodebek: stations and track geometry from OpenStreetMap (ODbL). No open
  timetable exists, so `arr` is empty and the app shows no ETA for them. Nothing here is invented.

Schema (same as tj_static.json, ids prefixed per mode so they never collide with TJ ids):
  routes[]: id, short, long, kind ("KRL" | "MRT" | "LRT Jakarta" | "LRT Jabodebek"), color, text
  halte{id: [name, lat, lng]}
  trips[]:  id, route, head, name, stops[], shape (encoded polyline, precision 5),
            arr[] (seconds from midnight, may exceed 86400 after midnight), days (bit 0 = Monday), freq[]
  meta:     validUntil (YYYY-MM-DD, KRL calendar end), attribution, sources, krlGtfsSha256

Usage: python3 tools/generate_rail_static.py [--krl-gtfs tools/data/krl_gtfs_v2.zip] [--skip-osm] [-o OUT]
"""
import argparse, csv, hashlib, io, json, math, os, re, sys, zipfile
from datetime import datetime, timezone

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_tj_static import encode_polyline, simplify  # noqa: E402
from generate_stations_seed import fetch_relations  # noqa: E402

SIMPLIFY_METERS = 8.0
ATTRIBUTION = [
    "KRL Commuter Line GTFS data compiled and verified by Open-KRL (CC-BY 4.0), https://github.com/open-krl/pipeline",
    "Station and track geometry © OpenStreetMap contributors (ODbL)",
]

# OSM route relations. One direction per line; the opposite direction is the same stops reversed.
# (route id, short, long, kind, fallback colour, OSM relation id)
OSM_LINES = [
    ("mrt:north-south", "MRT", "MRT Jakarta Lin Utara-Selatan", "MRT", "1F5FA8", 9677669),
    ("lrtj:selatan", "LRTJ", "LRT Jakarta Lin Selatan", "LRT Jakarta", "E87722", 10693119),
    ("lrtjbd:cibubur", "LRT-C", "LRT Jabodebek Lin Cibubur", "LRT Jabodebek", "1F8AC0", 16036440),
    ("lrtjbd:bekasi", "LRT-B", "LRT Jabodebek Lin Bekasi", "LRT Jabodebek", "2E9E4F", 16079478),
]
# Some acronyms get mangled by str.title() when no OSM name matches a station.
ACRONYMS = {"Ui": "UI", "Itc": "ITC", "Bni": "BNI", "Bsd": "BSD", "Pdi": "PDI", "Gor": "GOR", "Ps": "Ps."}


def hms_to_sec(value):
    """GTFS time "HH:MM:SS" (hour may exceed 23 for after-midnight trips) -> seconds from midnight."""
    h, m, s = value.split(":")
    return int(h) * 3600 + int(m) * 60 + int(s)


def title_case(raw):
    words = [ACRONYMS.get(w.capitalize(), w.capitalize()) for w in raw.split()]
    return " ".join(words)


# Naming-rights suffixes OSM carries on MRT/LRT stations ("Blok M BCA"); riders know the plain name.
SPONSORS = re.compile(r"\s+(Bank Syariah Indonesia|Bank Jakarta|Indomaret|TUKU|VISA|BCA|Mastercard|Mandiri|Astra|BNI|"
                      r"Summarecon Mall|Headquarters)$")


def plain_name(name):
    previous = None
    while previous != name:  # "Dukuh Atas Bank Syariah Indonesia" could carry more than one suffix
        previous, name = name, SPONSORS.sub("", name)
    return name


def text_color(hex_color):
    """Black or white, whichever contrasts more with the badge colour (WCAG relative luminance)."""
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (0, 2, 4))
    lum = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    return "FFFFFF" if 1.05 / (lum + 0.05) >= (lum + 0.05) / 0.05 else "000000"


def flat_distance(a, b):
    """Metres between two (lat, lng) points; flat-earth is fine at Jabodetabek scale."""
    k = 111_320.0
    return math.hypot((a[0] - b[0]) * k, (a[1] - b[1]) * k * math.cos(math.radians(a[0])))


def path_length(points):
    return sum(flat_distance(points[i], points[i + 1]) for i in range(len(points) - 1))


def chain_ways(ways):
    """Join OSM way coordinate lists (each a list of (lat, lng)) into one path, flipping as needed."""
    ways = [w for w in ways if len(w) >= 2]
    if not ways:
        return []
    out = list(ways[0])
    if len(ways) > 1:
        nxt = ways[1]
        d_end = min(flat_distance(out[-1], nxt[0]), flat_distance(out[-1], nxt[-1]))
        d_start = min(flat_distance(out[0], nxt[0]), flat_distance(out[0], nxt[-1]))
        if d_start < d_end:
            out.reverse()
    for w in ways[1:]:
        if flat_distance(out[-1], w[0]) <= flat_distance(out[-1], w[-1]):
            out += w
        else:
            out += w[::-1]
    return out


def days_mask(row, today):
    """GTFS calendar row -> bitmask (bit 0 = Monday). Services already ended get 0 (as in tj_static)."""
    if row["end_date"] < today:
        return 0
    cols = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
    return sum(1 << i for i, c in enumerate(cols) if row[c] == "1")


def read(zf, name):
    with zf.open(name) as f:
        return list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig")))


def seed_station_names(seed_path):
    """OSM station names (proper case) from the alarm seed, used to prettify the upper-case GTFS names."""
    if not os.path.exists(seed_path):
        return []
    seed = json.load(open(seed_path, encoding="utf-8"))
    return [(s["lat"], s["lng"], s["name"]) for m in seed["modes"] if m["mode"] == "KRL"
            for line in m["lines"] for s in line["stations"]]


def build_krl(zip_path, seed_path, today):
    raw = open(zip_path, "rb").read()
    zf = zipfile.ZipFile(io.BytesIO(raw))
    osm_names = seed_station_names(seed_path)

    stops = {}
    for s in read(zf, "stops.txt"):
        lat, lng = float(s["stop_lat"]), float(s["stop_lon"])
        nearest = min(osm_names, key=lambda o: flat_distance((lat, lng), (o[0], o[1])), default=None)
        pretty = nearest[2] if nearest and flat_distance((lat, lng), (nearest[0], nearest[1])) < 350 else title_case(s["stop_name"])
        stops[s["stop_id"]] = (pretty, round(lat, 6), round(lng, 6), s["stop_name"])
    # GTFS headsigns are upper-case with the spaces dropped ("JAKARTAKOTA", "CIKARANG VIA MRI").
    squash = lambda text: re.sub(r"[^A-Z]", "", text.upper())
    by_squash = {squash(raw_name): pretty for pretty, _, _, raw_name in stops.values()}
    by_squash.update({squash(pretty): pretty for pretty, _, _, _ in stops.values()})
    by_squash["TANJUNGPRIUK"] = "Tanjung Priuk"  # GTFS spells the station "PRIOK", the headsign "PRIUK"
    by_code = {sid: v[0] for sid, v in stops.items()}

    def head_label(raw_head):
        left, _, via = raw_head.partition(" VIA ")
        label = by_squash.get(squash(left), title_case(left))
        return f"{label} via {by_code.get(via.strip(), title_case(via))}" if via else label

    services = {c["service_id"]: c for c in read(zf, "calendar.txt")}
    masks = {sid: days_mask(c, today) for sid, c in services.items()}
    valid_until = max(c["end_date"] for c in services.values())

    times = {}
    for st in read(zf, "stop_times.txt"):
        times.setdefault(st["trip_id"], []).append((int(st["stop_sequence"]), st["stop_id"], hms_to_sec(st["arrival_time"])))

    routes = {r["route_id"]: r for r in read(zf, "routes.txt")}
    trips, used = [], set()
    for t in read(zf, "trips.txt"):
        rows = sorted(times.get(t["trip_id"], []))
        seq, arr = [], []
        for _, sid, sec in rows:
            if sid not in stops or (seq and seq[-1] == sid):
                continue
            seq.append(sid)
            arr.append(sec)
        if len(seq) < 2:
            continue
        pts = [(stops[s][1], stops[s][2]) for s in seq]
        head_raw = t["trip_headsign"]
        trips.append({
            "id": f"krl:{t['trip_id']}",
            "route": f"krl:{t['route_id']}",
            "head": head_label(head_raw),
            "name": f"KA {t['trip_id']}",
            "stops": [f"krl:{s}" for s in seq],
            "shape": encode_polyline(pts),
            "arr": arr,
            "days": masks.get(t["service_id"], 0),
            "freq": [],
        })
        used.update(seq)

    route_rows = [{
        "id": f"krl:{rid}", "short": r["route_short_name"], "long": r["route_long_name"],
        "kind": "KRL", "color": r["route_color"] or "E30A16", "text": r["route_text_color"] or "FFFFFF",
    } for rid, r in routes.items() if any(t["route"] == f"krl:{rid}" for t in trips)]
    halte = {f"krl:{sid}": [stops[sid][0], stops[sid][1], stops[sid][2]] for sid in sorted(used)}
    valid = f"{valid_until[:4]}-{valid_until[4:6]}-{valid_until[6:]}"
    return route_rows, halte, trips, valid, hashlib.sha256(raw).hexdigest()


def stops_with_ids(rel, names):
    """Ordered (node id, name, lat, lng) of a PTv2 route relation; mirrors generate_stations_seed.stops_of."""
    members = rel.get("members", [])
    picked = []
    for role in ("stop", "platform"):
        picked = [m for m in members if m["type"] == "node" and m.get("role", "").startswith(role)
                  and "lat" in m and m["ref"] in names]
        if picked:
            break
    out, seen = [], None
    for m in picked:
        name = names[m["ref"]]
        if name == seen:
            continue
        seen = name
        out.append((m["ref"], plain_name(name), round(m["lat"], 6), round(m["lon"], 6)))
    return out


def track_points(rel):
    ways = []
    for m in rel.get("members", []):
        if m["type"] != "way" or not m.get("geometry"):
            continue
        if m.get("role", "").startswith(("platform", "stop")):
            continue
        ways.append([(p["lat"], p["lon"]) for p in m["geometry"]])
    return chain_ways(ways)


def build_osm(today_unused=None):
    rels, names = fetch_relations([line[5] for line in OSM_LINES])
    routes, halte, trips = [], {}, []
    for rid, short, long_name, kind, fallback, rel_id in OSM_LINES:
        rel = rels.get(rel_id)
        if not rel:
            print(f"  WARNING: relation {rel_id} ({rid}) not returned by Overpass, skipped", file=sys.stderr)
            continue
        st = stops_with_ids(rel, names)
        if len(st) < 2:
            print(f"  WARNING: {rid}: only {len(st)} stops, skipped", file=sys.stderr)
            continue
        straight = [(s[2], s[3]) for s in st]
        track = track_points(rel)
        # A track chain much longer or shorter than the station-to-station path means the ways were
        # not contiguous; fall back to the straight line rather than draw a spaghetti route.
        ratio = path_length(track) / path_length(straight) if track else 0
        if not 0.9 <= ratio <= 1.8:
            print(f"  note: {rid}: track/stations length ratio {ratio:.2f}, using straight line", file=sys.stderr)
            track = straight
        colour = (rel.get("tags", {}).get("colour") or "").lstrip("#").upper()
        colour = colour if re.fullmatch(r"[0-9A-F]{6}", colour) else fallback
        routes.append({"id": rid, "short": short, "long": long_name, "kind": kind, "color": colour, "text": text_color(colour)})
        prefix = rid.split(":")[0]
        for s in st:
            halte[f"{prefix}:{s[0]}"] = [s[1], s[2], s[3]]
        ids = [f"{prefix}:{s[0]}" for s in st]
        simplified = simplify(track, SIMPLIFY_METERS)
        for direction, (order, pts) in enumerate((
            (list(range(len(st))), simplified),
            (list(range(len(st) - 1, -1, -1)), simplified[::-1]),
        )):
            first, last = st[order[0]][1], st[order[-1]][1]
            trips.append({
                "id": f"{rid}:{direction}", "route": rid, "head": last, "name": f"{first} → {last}",
                "stops": [ids[i] for i in order], "shape": encode_polyline(pts),
                "arr": [], "days": 127, "freq": [],
            })
        print(f"  {rid}: {len(st)} stations, track {len(simplified)} pts (ratio {ratio:.2f})")
    return routes, halte, trips


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--krl-gtfs", default=os.path.join(here, "data", "krl_gtfs_v2.zip"))
    ap.add_argument("--seed", default=os.path.join(here, "..", "app/src/main/assets/stations_seed.json"))
    ap.add_argument("--skip-osm", action="store_true", help="KRL only (offline); MRT/LRT left out")
    ap.add_argument("-o", "--out", default=os.path.join(here, "..", "app/src/main/assets/rail_static.json"))
    args = ap.parse_args()

    today = datetime.now(timezone.utc).strftime("%Y%m%d")
    print("KRL (Open-KRL GTFS)...")
    routes, halte, trips, valid_until, sha = build_krl(args.krl_gtfs, args.seed, today)
    print(f"  {len(routes)} routes, {len(halte)} stations, {len(trips)} trips, valid until {valid_until}")
    if not args.skip_osm:
        print("MRT / LRT (OpenStreetMap)...")
        r2, h2, t2 = build_osm()
        routes += r2
        halte.update(h2)
        trips += t2

    out = {
        "version": today,
        "gtfsLastModified": None,
        "source": "Open-KRL GTFS (KRL), OpenStreetMap via Overpass API (MRT, LRT)",
        "routes": routes,
        "halte": halte,
        "trips": trips,
        "meta": {"validUntil": valid_until, "attribution": ATTRIBUTION, "krlGtfsSha256": sha},
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    size = os.path.getsize(args.out)
    print(f"Wrote {args.out}: {len(routes)} routes, {len(trips)} trips, {len(halte)} stops, {size / 1024:.0f} KB")


if __name__ == "__main__":
    main()
