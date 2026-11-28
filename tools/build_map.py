#!/usr/bin/env python3
"""O'zbekiston oflayn xaritasi: aholi punktlari va trassalar (OpenStreetMap, Overpass).

Natija: app/src/main/assets/uz_map.bin
  "UZM1"
  int32 n_places; n_places * (int32 lat*1e6, int32 lon*1e6, uint8 kind, uint16 radius_m)
     kind: 1=shahar, 2=aholi punkti
  int32 n_road;   n_road * (int32 lat*1e6, int32 lon*1e6)   — trassa nuqtalari (har ~120 m)
Hammasi big-endian.
"""
import json, math, struct, sys, time, urllib.parse, urllib.request

SERVERS = ["https://overpass-api.de/api/interpreter",
           "https://overpass.kumi.systems/api/interpreter",
           "https://maps.mail.ru/osm/tools/overpass/api/interpreter"]

def overpass(q):
    last = None
    for attempt in range(4):
        for s in SERVERS:
            try:
                data = urllib.parse.urlencode({"data": q}).encode()
                req = urllib.request.Request(s, data=data, headers={"User-Agent": "AvtoLauncher-mapbuild/1"})
                with urllib.request.urlopen(req, timeout=900) as r:
                    return json.loads(r.read().decode())
            except Exception as e:
                last = e
                print("xato", s, e, file=sys.stderr)
                time.sleep(20)
    raise last

AREA = 'area["ISO3166-1"="UZ"][admin_level=2]->.uz;'

def radius(tags):
    p = tags.get("place")
    pop = 0
    try:
        pop = int(str(tags.get("population", "0")).replace(" ", "").split(";")[0])
    except Exception:
        pop = 0
    if p == "city":
        return 1, int(min(15000, max(3500, 600 + 7 * math.sqrt(pop)))) if pop else 4000
    if p == "town":
        return 1, int(min(5000, max(1500, 600 + 7 * math.sqrt(pop)))) if pop else 2000
    if p == "village":
        return 2, int(min(1500, max(600, 300 + 6 * math.sqrt(pop)))) if pop else 700
    return 2, 400  # hamlet

def main(out):
    places = overpass('[out:json][timeout:900];' + AREA +
                      'node(area.uz)[place~"^(city|town|village|hamlet)$"];out;')["elements"]
    print("aholi punktlari:", len(places))
    roads = overpass('[out:json][timeout:900];' + AREA +
                     'way(area.uz)[highway~"^(motorway|trunk)$"];out geom;')["elements"]
    print("trassa bo'laklari:", len(roads))

    prec = []
    for e in places:
        kind, r = radius(e.get("tags", {}))
        prec.append((int(round(e["lat"] * 1e6)), int(round(e["lon"] * 1e6)), kind, min(r, 65535)))

    pts = set()
    STEP = 120.0
    for w in roads:
        g = w.get("geometry") or []
        for a, b in zip(g, g[1:]):
            la1, lo1, la2, lo2 = a["lat"], a["lon"], b["lat"], b["lon"]
            dy = (la2 - la1) * 111320.0
            dx = (lo2 - lo1) * 111320.0 * math.cos(math.radians(la1))
            n = max(1, int(math.hypot(dx, dy) / STEP))
            for i in range(n + 1):
                t = i / n
                la = la1 + (la2 - la1) * t
                lo = lo1 + (lo2 - lo1) * t
                # ~11 m aniqlikda takrorlarni olib tashlaymiz
                pts.add((int(round(la * 1e4)) * 100, int(round(lo * 1e4)) * 100))
    print("trassa nuqtalari:", len(pts))

    with open(out, "wb") as f:
        f.write(b"UZM1")
        f.write(struct.pack(">i", len(prec)))
        for la, lo, k, r in prec:
            f.write(struct.pack(">iiBH", la, lo, k, r))
        f.write(struct.pack(">i", len(pts)))
        for la, lo in sorted(pts):
            f.write(struct.pack(">ii", la, lo))
    if len(prec) < 1000 or len(pts) < 1000:
        raise SystemExit("ma'lumot juda kam — xato bo'lishi mumkin")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/uz_map.bin")
