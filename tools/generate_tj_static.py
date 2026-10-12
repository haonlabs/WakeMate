#!/usr/bin/env python3
"""Generate app/src/main/assets/tj_static.json from the official Transjakarta GTFS feed.

The live feed (MQTT `/mobile_armada/...`) identifies trips by GTFS `trip_id` and halte by
GTFS parent station id, so this file has to come from the same GTFS — OSM ids won't match.

Shapes are simplified (Douglas-Peucker) and stored as Google encoded polylines (precision 5),
which MapLibre's `LineString.fromPolyline` reads directly.

Usage: python3 tools/generate_tj_static.py [--gtfs file_gtfs.zip] [-o OUT]
"""
import argparse, csv, io, json, math, urllib.request, zipfile
from datetime import datetime, timezone

GTFS_URL = "https://gtfs.transjakarta.co.id/files/file_gtfs.zip"
SIMPLIFY_METERS = 8.0


def read(zf, name):
    with zf.open(name) as f:
        return list(csv.DictReader(io.TextIOWrapper(f, encoding="utf-8-sig")))


def to_xy(lat, lng, lat0):
    """Equirectangular projection in meters; plenty accurate at city scale."""
    k = 111_320.0
    return lng * k * math.cos(math.radians(lat0)), lat * k


def simplify(points, tolerance):
    if len(points) < 3:
        return points
    lat0 = points[0][0]
    xy = [to_xy(lat, lng, lat0) for lat, lng in points]
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        (ax, ay), (bx, by) = xy[a], xy[b]
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy) or 1e-9
        worst, worst_i = 0.0, None
        for i in range(a + 1, b):
            px, py = xy[i]
            d = abs(dy * px - dx * py + bx * ay - by * ax) / length
            if d > worst:
                worst, worst_i = d, i
        if worst_i is not None and worst > tolerance:
            keep[worst_i] = True
            stack += [(a, worst_i), (worst_i, b)]
    return [p for p, k in zip(points, keep) if k]


def encode_polyline(points):
    out, prev_lat, prev_lng = [], 0, 0
    for lat, lng in points:
        ilat, ilng = round(lat * 1e5), round(lng * 1e5)
        for delta in (ilat - prev_lat, ilng - prev_lng):
            v = ~(delta << 1) if delta < 0 else delta << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        prev_lat, prev_lng = ilat, ilng
    return "".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gtfs", help="local GTFS zip; downloaded from Transjakarta when omitted")
    ap.add_argument("-o", "--out", default="app/src/main/assets/tj_static.json")
    args = ap.parse_args()

    if args.gtfs:
        raw = open(args.gtfs, "rb").read()
        last_modified = None
    else:
        req = urllib.request.Request(GTFS_URL, headers={"User-Agent": "Mozilla/5.0 WakeMate-static/1.0"})
        with urllib.request.urlopen(req, timeout=120) as r:
            raw, last_modified = r.read(), r.headers.get("Last-Modified")
    zf = zipfile.ZipFile(io.BytesIO(raw))

    stops = {s["stop_id"]: s for s in read(zf, "stops.txt")}
    parent = {sid: (s["parent_station"] or sid) for sid, s in stops.items()}

    stop_times = {}
    for st in read(zf, "stop_times.txt"):
        stop_times.setdefault(st["trip_id"], []).append((int(st["stop_sequence"]), st["stop_id"]))

    shapes = {}
    for p in read(zf, "shapes.txt"):
        shapes.setdefault(p["shape_id"], []).append(
            (int(p["shape_pt_sequence"]), float(p["shape_pt_lat"]), float(p["shape_pt_lon"])))

    trips, used_halte, routes_with_trips = [], set(), set()
    for t in read(zf, "trips.txt"):
        seq = [parent.get(sid, sid) for _, sid in sorted(stop_times.get(t["trip_id"], []))]
        halte = [h for i, h in enumerate(seq) if i == 0 or h != seq[i - 1]]
        if len(halte) < 2:
            continue
        pts = [(lat, lng) for _, lat, lng in sorted(shapes.get(t["shape_id"], []))]
        trips.append({
            "id": t["trip_id"],
            "route": t["route_id"],
            "head": t["trip_headsign"],
            "name": t["trip_short_name"],
            "stops": halte,
            "shape": encode_polyline(simplify(pts, SIMPLIFY_METERS)) if len(pts) >= 2 else "",
        })
        used_halte.update(halte)
        routes_with_trips.add(t["route_id"])

    routes = [{
        "id": r["route_id"],
        "short": r["route_short_name"] or r["route_id"],
        "long": r["route_long_name"],
        "kind": r["route_desc"],
        "color": r["route_color"] or "4FDBC8",
        "text": r["route_text_color"] or "000000",
    } for r in read(zf, "routes.txt") if r["route_id"] in routes_with_trips]

    halte = {}
    for hid in sorted(used_halte):
        s = stops.get(hid)
        if s:
            halte[hid] = [s["stop_name"], round(float(s["stop_lat"]), 6), round(float(s["stop_lon"]), 6)]

    out = {
        "version": datetime.now(timezone.utc).strftime("%Y%m%d"),
        "gtfsLastModified": last_modified,
        "source": GTFS_URL,
        "routes": sorted(routes, key=lambda r: r["short"]),
        "halte": halte,
        "trips": trips,
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"Wrote {args.out}: {len(routes)} routes, {len(trips)} trips, {len(halte)} halte")


if __name__ == "__main__":
    main()
