#!/usr/bin/env python3
"""Generate app/src/main/assets/stations_seed.json from OpenStreetMap (Overpass API).

OSM route relations already carry stops in travel order, so they are the source of
truth for `sequence`. Rail lines are listed explicitly because OSM holds several
variants per line (full/half racket, both directions) and we only want one per line.

Usage: python3 tools/generate_stations_seed.py [-o OUT]
"""
import argparse, json, re, sys, time, urllib.parse, urllib.request
from datetime import datetime, timezone

OVERPASS = "https://overpass-api.de/api/interpreter"
JABODETABEK = "(-6.75,106.4,-5.95,107.20)"

# relation id -> (mode, line id, display name). One direction per line; ProximityCalculator
# is direction-agnostic (it projects onto the polyline), so the reverse trip works too.
RAIL_LINES = {
    16877210: ("KRL", "krl-bogor", "Lin Bogor"),
    16877211: ("KRL", "krl-nambo", "Lin Bogor (Nambo)"),
    15094545: ("KRL", "krl-cikarang-manggarai", "Lin Lingkar Cikarang via Manggarai"),
    2922163:  ("KRL", "krl-cikarang-senen", "Lin Lingkar Cikarang via Pasar Senen"),
    2922215:  ("KRL", "krl-rangkasbitung", "Lin Rangkasbitung"),
    2922235:  ("KRL", "krl-tangerang", "Lin Tangerang"),
    17193008: ("KRL", "krl-tanjung-priuk", "Lin Tanjung Priuk"),
    17675694: ("KRL", "krl-bandara", "Lin Bandara Soekarno-Hatta"),
    9677669:  ("MRT", "mrt-north-south", "MRT Lin Utara-Selatan"),
}


def overpass(query):
    body = urllib.parse.urlencode({"data": query}).encode()
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(OVERPASS, body, {"User-Agent": "WakeMate-seed-generator/1.0"}), timeout=300) as r:
                return json.load(r)
        except Exception as exc:  # Overpass rate-limits aggressively; back off and retry
            if attempt == 2:
                raise
            print(f"  overpass retry {attempt + 1}: {exc}", file=sys.stderr)
            time.sleep(20)


def slug(text):
    return re.sub(r"-+", "-", re.sub(r"[^a-z0-9]+", "-", text.lower())).strip("-")


def discover_brt_relations():
    """14 BRT corridors have dedicated halte and numeric refs; pick one direction each."""
    data = overpass(f'[out:json][timeout:180];rel["type"="route"]["route"="bus"]'
                    f'["network"~"Transjakarta",i]{JABODETABEK};out tags;')
    by_ref = {}
    for el in data["elements"]:
        ref = (el["tags"].get("ref") or "").strip()
        if ref.isdigit():
            by_ref.setdefault(int(ref), []).append(el)
    out = {}
    for ref in sorted(by_ref):
        el = sorted(by_ref[ref], key=lambda e: e["id"])[0]
        out[el["id"]] = ("TRANSJAKARTA", f"tj-koridor-{ref}", f"Koridor {ref}")
    return out


def fetch_relations(rel_ids):
    ids = ",".join(str(i) for i in rel_ids)
    # `out geom` gives members in order with coordinates; the node(r) pass gives their names.
    data = overpass(f"[out:json][timeout:300];rel(id:{ids})->.r;.r out geom;node(r.r);out body;")
    rels, names = {}, {}
    for el in data["elements"]:
        if el["type"] == "relation":
            rels[el["id"]] = el
        elif el["type"] == "node":
            tags = el.get("tags", {})
            name = tags.get("name") or tags.get("alt_name")
            if name:
                names[el["id"]] = re.sub(r"^Stasiun\s+|^Halte\s+", "", name).strip()
    return rels, names


def stops_of(rel, names):
    members = rel.get("members", [])
    roles = ("stop", "platform")  # PTv2: stop nodes preferred, platform is the fallback
    for role in roles:
        picked = [m for m in members
                  if m["type"] == "node" and m.get("role", "").startswith(role)
                  and "lat" in m and m["ref"] in names]
        if picked:
            break
    stations, seen = [], None
    for m in picked:
        name = names[m["ref"]]
        if name == seen:  # same station tagged twice (e.g. stop + stop_entry_only)
            continue
        seen = name
        stations.append({"name": name, "lat": round(m["lat"], 6), "lng": round(m["lon"], 6)})
    return stations


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("-o", "--out", default="app/src/main/assets/stations_seed.json")
    args = ap.parse_args()

    print("Discovering Transjakarta BRT corridors...")
    catalog = dict(RAIL_LINES)
    catalog.update(discover_brt_relations())
    print(f"Fetching {len(catalog)} route relations...")
    rels, names = fetch_relations(catalog)

    modes, skipped = {}, []
    for rel_id, (mode, line_id, line_name) in sorted(catalog.items(), key=lambda kv: kv[1][1]):
        rel = rels.get(rel_id)
        stops = stops_of(rel, names) if rel else []
        if len(stops) < 2:
            skipped.append(f"{line_id} (rel {rel_id}): {len(stops)} stops")
            continue
        # Loop lines (Cikarang full racket) pass some stations twice, so ids get a suffix.
        stations, used = [], {}
        for i, s in enumerate(stops):
            base = f"{line_id}:{slug(s['name'])}"
            used[base] = used.get(base, 0) + 1
            sid = base if used[base] == 1 else f"{base}-{used[base]}"
            stations.append({"id": sid, "name": s["name"], "lat": s["lat"],
                             "lng": s["lng"], "sequence": i})
        modes.setdefault(mode, []).append({"id": line_id, "name": line_name, "stations": stations})
        print(f"  {line_id}: {len(stations)} stations")

    seed = {
        "version": 1,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "source": "OpenStreetMap via Overpass API (ODbL)",
        "modes": [{"mode": m, "lines": modes[m]} for m in ("KRL", "MRT", "TRANSJAKARTA") if m in modes],
    }
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(seed, f, ensure_ascii=False, indent=1)
        f.write("\n")
    total = sum(len(l["stations"]) for m in seed["modes"] for l in m["lines"])
    print(f"\nWrote {args.out}: {total} stations across "
          f"{sum(len(m['lines']) for m in seed['modes'])} lines")
    for s in skipped:
        print(f"  SKIPPED {s}", file=sys.stderr)


if __name__ == "__main__":
    main()
