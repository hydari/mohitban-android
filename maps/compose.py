"""Blends relief, land cover, water, contours and roads into one RGB map raster, in strips."""
import sys
import numpy as np
from osgeo import gdal

work, out = sys.argv[1], sys.argv[2]
gdal.UseExceptions()
open_ = lambda n: gdal.Open(f"{work}/{n}")
hill, tint = open_("hill.tif"), open_("tint.tif")
masks = {k: open_(f"{k}.tif") for k in ["contour", "landcover", "water", "river", "road"]}
W, H = hill.RasterXSize, hill.RasterYSize
drv = gdal.GetDriverByName("GTiff")
dst = drv.Create(out, W, H, 3, gdal.GDT_Byte,
                 ["TILED=YES", "COMPRESS=JPEG", "JPEG_QUALITY=90", "PHOTOMETRIC=YCBCR", "BIGTIFF=YES"])
dst.SetGeoTransform(hill.GetGeoTransform()); dst.SetProjection(hill.GetProjection())

def col(*rgb): return np.array(rgb, dtype=np.float32)

def mix(img, m, c, a):
    if not m.any(): return
    img[m] = img[m] * (1 - a) + c * a

STRIP = 1024
for y in range(0, H, STRIP):
    h = min(STRIP, H - y)
    rd = lambda ds, b=1: ds.GetRasterBand(b).ReadAsArray(0, y, W, h)
    shade = rd(hill).astype(np.float32) / 255.0
    img = np.dstack([rd(tint, b) for b in (1, 2, 3)]).astype(np.float32)
    lc, wa, rv, ro, ct = (rd(masks[k]) for k in ["landcover", "water", "river", "road", "contour"])

    mix(img, lc == 4, col(214, 226, 170), 0.35)   # scrub / grassland
    mix(img, lc == 2, col(176, 208, 128), 0.45)   # orchards / vineyards
    mix(img, lc == 1, col(118, 168, 104), 0.55)   # forest
    mix(img, lc == 3, col(214, 204, 198), 0.6)    # villages / towns
    # relief: soft multiply so colours stay readable in deep valleys
    img *= (0.42 + 0.66 * shade)[..., None]
    img = np.clip(img, 0, 255)
    mix(img, ct == 1, col(150, 112, 74), 0.32)    # 50 m contours
    mix(img, ct == 2, col(128, 90, 56), 0.62)     # 250 m index contours
    img[wa == 1] = col(156, 198, 232)             # lakes, reservoirs, wetlands
    mix(img, rv == 1, col(86, 148, 208), 0.8)     # streams
    img[rv == 2] = col(52, 118, 196)              # rivers
    mix(img, ro == 1, col(150, 96, 52), 0.75)     # tracks / paths
    img[ro == 2] = col(250, 250, 246)             # minor roads
    img[ro == 3] = col(252, 214, 120)             # secondary roads
    img[ro == 4] = col(236, 130, 60)              # main roads
    out8 = np.clip(img, 0, 255).astype(np.uint8)
    for b in range(3):
        dst.GetRasterBand(b + 1).WriteArray(out8[..., b], 0, y)
    print(f"rows {y}-{y + h} of {H}", flush=True)
dst.FlushCache()
dst = None
