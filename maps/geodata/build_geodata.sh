#!/usr/bin/env bash
# Offline "where am I" data for the app, from OpenStreetMap (ODbL):
#   admin.geojson.gz : provinces (admin_level 4) and counties (admin_level 5) of Iran, simplified
#   places.tsv.gz    : cities, towns, villages and hamlets (kind, lat, lon, name)
set -euo pipefail
trap 'echo "FAILED at line $LINENO: $BASH_COMMAND"' ERR
OUT="$PWD/app/src/main/assets/geo"; WORK="$PWD/work-geo"
mkdir -p "$OUT" "$WORK"; cd "$WORK"
[ -s iran.osm.pbf ] || curl -fsSL --retry 3 -o iran.osm.pbf https://download.geofabrik.de/asia/iran-latest.osm.pbf
ls -la iran.osm.pbf

echo "::group::admin boundaries"
osmium tags-filter --overwrite iran.osm.pbf r/boundary=administrative -o admin.osm.pbf
ogr2ogr -overwrite -f GPKG admin.gpkg admin.osm.pbf multipolygons -where "admin_level IN ('4','5','6')" -nln admin
python3 - <<'PY'
from osgeo import ogr
ds = ogr.Open("admin.gpkg"); lyr = ds.GetLayer(0)
from collections import Counter
c = Counter(f.GetField("admin_level") for f in lyr); print("levels:", dict(c))
lyr.ResetReading()
print("level 4:", sorted(f.GetField("name") or "?" for f in lyr if f.GetField("admin_level") == "4"))
PY
for lvl in 4 5; do
  tol=$([ "$lvl" = 4 ] && echo 0.001 || echo 0.0005)
  ogr2ogr -overwrite -f GeoJSON "admin$lvl.geojson" admin.gpkg -where "admin_level = '$lvl'" -simplify "$tol" \
    -select name,admin_level -lco COORDINATE_PRECISION=5 -lco RFC7946=YES
done
python3 - <<'PY'
import json
feats = []
for lvl in ("4", "5"):
    g = json.load(open(f"admin{lvl}.geojson", encoding="utf-8"))
    for f in g["features"]:
        if f.get("geometry") and f["properties"].get("name"):
            feats.append({"type": "Feature", "properties": {"n": f["properties"]["name"].replace("ي", "ی").replace("ك", "ک"), "l": int(lvl)}, "geometry": f["geometry"]})
json.dump({"type": "FeatureCollection", "features": feats}, open("admin.geojson", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("admin features:", len(feats))
PY
gzip -9 -c admin.geojson > "$OUT/admin.geojson.gz"
echo "::endgroup::"

echo "::group::places"
osmium tags-filter --overwrite iran.osm.pbf n/place=city,town,village,hamlet,suburb -o places.osm.pbf
ogr2ogr -overwrite -f CSV places.csv places.osm.pbf points -select name,place -lco GEOMETRY=AS_XY
python3 - "$OUT/places.tsv.gz" <<'PY'
import csv, gzip, sys
kinds = {"city": "c", "town": "t", "village": "v", "hamlet": "h", "suburb": "s"}
n = 0
with open("places.csv", encoding="utf-8") as f, gzip.open(sys.argv[1], "wt", encoding="utf-8", compresslevel=9) as out:
    for r in csv.DictReader(f):
        k = kinds.get(r.get("place") or ""); name = (r.get("name") or "").strip()
        if not k or not name: continue
        name = name.replace("ي", "ی").replace("ك", "ک").replace("\t", " ")
        out.write(f"{k}\t{float(r['Y']):.5f}\t{float(r['X']):.5f}\t{name}\n"); n += 1
print("places:", n)
PY
echo "::endgroup::"
ls -la "$OUT"
