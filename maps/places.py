"""Stores named places (towns, villages, peaks, springs) and metadata inside the MBTiles file."""
import sqlite3
import sys
from osgeo import ogr, osr

points, polys, mbtiles, title = sys.argv[1:5]
src = osr.SpatialReference(); src.ImportFromEPSG(3857)
dst = osr.SpatialReference(); dst.ImportFromEPSG(4326)
dst.SetAxisMappingStrategy(osr.OAMS_TRADITIONAL_GIS_ORDER)
tr = osr.CoordinateTransformation(src, dst)

def kind_of(f):
    place = f.GetField("place") if f.GetFieldIndex("place") >= 0 else None
    nat = f.GetField("natural") if f.GetFieldIndex("natural") >= 0 else None
    if place in ("city", "town"): return "town"
    if place in ("village", "suburb"): return "village"
    if place in ("hamlet", "isolated_dwelling", "locality"): return "hamlet"
    if nat == "peak": return "peak"
    if nat == "spring": return "spring"
    return None

rows = []
for path, layer in ((points, "points"), (polys, "multipolygons")):
    ds = ogr.Open(path)
    lyr = ds.GetLayerByName(layer)
    for f in lyr:
        k = kind_of(f)
        if not k: continue
        name = (f.GetField("name_fa") if f.GetFieldIndex("name_fa") >= 0 else None) or f.GetField("name")
        if not name and k != "peak": continue
        g = f.GetGeometryRef()
        if g is None: continue
        c = g.Centroid() if g.GetGeometryType() != ogr.wkbPoint else g
        c = c.Clone(); c.Transform(tr)
        ele = None
        if f.GetFieldIndex("ele") >= 0 and f.GetField("ele"):
            try: ele = float(str(f.GetField("ele")).split()[0].replace(",", "."))
            except ValueError: ele = None
        rows.append((name or "", k, c.GetY(), c.GetX(), ele))

db = sqlite3.connect(mbtiles)
db.execute("CREATE TABLE IF NOT EXISTS mohityar_places (name TEXT, kind TEXT, lat REAL, lon REAL, ele REAL)")
db.execute("DELETE FROM mohityar_places")
db.executemany("INSERT INTO mohityar_places VALUES (?,?,?,?,?)", rows)
meta = {
    "name": title,
    "attribution": "Copernicus DEM © DLR/Airbus (ESA, EU) | داده‌ها © مشارکت‌کنندگان OpenStreetMap (ODbL)",
    "mohityar": "1",
}
for k, v in meta.items():
    db.execute("DELETE FROM metadata WHERE name = ?", (k,))
    db.execute("INSERT INTO metadata (name, value) VALUES (?, ?)", (k, v))
db.commit()
print("places:", len(rows), {k: sum(1 for r in rows if r[1] == k) for k in {r[1] for r in rows}})
for r in db.execute("SELECT name, value FROM metadata"): print(r)
print("tiles per zoom:", list(db.execute("SELECT zoom_level, COUNT(*) FROM tiles GROUP BY zoom_level")))
db.close()
