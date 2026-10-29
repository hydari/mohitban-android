"""Full-resolution crops for reviewing the map style (Mianeh / Kaghazkonan and Tabriz / Sahand)."""
import math
import sys
from osgeo import gdal

src, prefix = sys.argv[1], sys.argv[2]
def merc(lat, lon):
    return lon * math.pi / 180 * 6378137, math.log(math.tan(math.pi / 4 + lat * math.pi / 360)) * 6378137
for name, lat, lon in (("mianeh", 37.42, 47.85), ("sahand", 37.74, 46.43), ("tabriz", 38.07, 46.29)):
    x, y = merc(lat, lon)
    half_w, half_h = 900 * 19.109257, 650 * 19.109257
    gdal.Translate(f"{prefix}-{name}.jpg", src, format="JPEG", creationOptions=["QUALITY=85"],
                   projWin=[x - half_w, y + half_h, x + half_w, y - half_h])
    print("crop", name)
