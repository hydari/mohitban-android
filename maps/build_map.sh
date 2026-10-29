#!/usr/bin/env bash
# Builds an offline topographic map (MBTiles) of one province for گشت‌یار from open data:
#   relief, elevation colours and contours  <- Copernicus DEM GLO-30 (ESA / EU, free licence)
#   water, rivers, forest, roads, villages  <- OpenStreetMap (ODbL)
# Usage: build_map.sh <slug> <west> <south> <east> <north> "<title>"
set -euo pipefail
trap 'echo "FAILED at line $LINENO: $BASH_COMMAND"' ERR

SLUG="$1"; W="$2"; S="$3"; E="$4"; N="$5"; TITLE="$6"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$PWD/out"; WORK="$PWD/work"
mkdir -p "$OUT" "$WORK/dem"
cd "$WORK"
RES=19.1092570713     # metres per pixel of web-map zoom 13 (≈15 m on the ground at 38°N)

echo "::group::Copernicus DEM"
mkdir -p dem30 dem90
for lat in $(seq "${S%.*}" "${N%.*}"); do
  for lon in $(seq "${W%.*}" "${E%.*}"); do
    n30=$(printf "Copernicus_DSM_COG_10_N%02d_00_E%03d_00_DEM" "$lat" "$lon")
    n90=$(printf "Copernicus_DSM_COG_30_N%02d_00_E%03d_00_DEM" "$lat" "$lon")
    # 90 m everywhere as a seamless base; 30 m where it is published (border tiles are 90 m only)
    [ -s "dem90/$n90.tif" ] || curl -fsSL --retry 3 -o "dem90/$n90.tif" "https://copernicus-dem-90m.s3.amazonaws.com/$n90/$n90.tif" || { rm -f "dem90/$n90.tif"; echo "no 90 m: $n90"; }
    [ -s "dem30/$n30.tif" ] || curl -fsSL --retry 3 -o "dem30/$n30.tif" "https://copernicus-dem-30m.s3.amazonaws.com/$n30/$n30.tif" || { rm -f "dem30/$n30.tif"; echo "90 m only: $n30"; }
  done
done
ls dem30 dem90 | wc -l
gdalbuildvrt -q -vrtnodata -32767 dem90.vrt dem90/*.tif
# gaps between published 30 m tiles must be "no data", not 0 m, so the 90 m base shows through
gdalbuildvrt -q -vrtnodata -32767 dem30.vrt dem30/*.tif
# later inputs are painted over earlier ones: 90 m base first, then 30 m detail
gdalwarp -q -overwrite -t_srs EPSG:3857 -te_srs EPSG:4326 -te "$W" "$S" "$E" "$N" -tr $RES $RES -tap -r bilinear \
  -srcnodata -32767 -dstnodata -32767 -wo SOURCE_EXTRA=4 \
  -co TILED=YES -co COMPRESS=DEFLATE -co PREDICTOR=3 -co BIGTIFF=YES -multi -wo NUM_THREADS=ALL_CPUS dem90.vrt dem30.vrt dem.tif
gdal_fillnodata.py -q -md 20 dem.tif dem_filled.tif -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=YES && mv dem_filled.tif dem.tif
gdal_edit.py -unsetnodata dem.tif
gdalinfo dem.tif | grep -E "Size is|Origin|Pixel Size"
echo "::endgroup::"

echo "::group::relief"
LAT_MID=$(python3 -c "print(($S+$N)/2)")
SCALE=$(python3 -c "import math;print(math.cos(math.radians($LAT_MID)))")   # web-mercator metres -> ground metres
gdaldem hillshade -q -multidirectional -z 1.6 -s "$SCALE" -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=YES dem.tif hill.tif
gdaldem color-relief -q dem.tif "$HERE/elevation.txt" tint.tif -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=YES
# contours from a slightly smoothed DEM so the lines stay clean
gdalwarp -q -overwrite -tr 38.2185141426 38.2185141426 -r average -co TILED=YES -co COMPRESS=DEFLATE dem.tif dem40.tif
gdal_contour -q -a ele -i 50 dem40.tif contours.gpkg
echo "::endgroup::"

echo "::group::OpenStreetMap"
[ -s iran.osm.pbf ] || curl -fsSL --retry 3 -o iran.osm.pbf https://download.geofabrik.de/asia/iran-latest.osm.pbf
osmium extract --overwrite -b "$W,$S,$E,$N" iran.osm.pbf -o area.osm.pbf
osmium tags-filter --overwrite area.osm.pbf nwr/waterway nwr/natural nwr/landuse w/highway nwr/place -o features.osm.pbf
export OSM_CONFIG_FILE="$HERE/osmconf.ini"
for layer in lines multipolygons points; do
  ogr2ogr -overwrite -t_srs EPSG:3857 -f GPKG "osm_$layer.gpkg" features.osm.pbf "$layer"
done
echo "::endgroup::"

echo "::group::rasterize"
read -r XMIN YMIN XMAX YMAX < <(python3 - <<'PY'
from osgeo import gdal
d = gdal.Open("dem.tif"); g = d.GetGeoTransform()
print(g[0], g[3] + g[5] * d.RasterYSize, g[0] + g[1] * d.RasterXSize, g[3])
PY
)
GRID=(-te "$XMIN" "$YMIN" "$XMAX" "$YMAX" -tr $RES $RES -ot Byte -init 0 -co TILED=YES -co COMPRESS=DEFLATE -co BIGTIFF=YES)
mask() { # out.tif src.gpkg layer burn where
  if [ -f "$1" ]; then gdal_rasterize -q -b 1 -burn "$4" -l "$3" -where "$5" "$2" "$1"
  else gdal_rasterize -q "${GRID[@]}" -burn "$4" -l "$3" -where "$5" "$2" "$1"; fi
}
mask contour.tif contours.gpkg contour 1 "1=1"
mask contour.tif contours.gpkg contour 2 "CAST(ele AS INTEGER) % 250 = 0"
mask landcover.tif osm_multipolygons.gpkg multipolygons 1 "landuse IN ('forest') OR \"natural\" IN ('wood')"
mask landcover.tif osm_multipolygons.gpkg multipolygons 2 "landuse IN ('orchard','vineyard')"
mask landcover.tif osm_multipolygons.gpkg multipolygons 3 "landuse IN ('residential','industrial','commercial')"
mask landcover.tif osm_multipolygons.gpkg multipolygons 4 "\"natural\" IN ('scrub','heath','grassland') OR landuse IN ('meadow','grass')"
mask water.tif osm_multipolygons.gpkg multipolygons 1 "\"natural\" IN ('water','wetland') OR landuse IN ('reservoir','basin') OR waterway IN ('riverbank','dock')"
mask river.tif osm_lines.gpkg lines 1 "waterway IN ('stream','ditch','drain','canal')"
mask river.tif osm_lines.gpkg lines 2 "waterway IN ('river')"
mask road.tif osm_lines.gpkg lines 1 "highway IN ('track','path','footway','bridleway')"
mask road.tif osm_lines.gpkg lines 2 "highway IN ('unclassified','residential','service','living_street')"
mask road.tif osm_lines.gpkg lines 3 "highway IN ('secondary','tertiary','secondary_link','tertiary_link')"
mask road.tif osm_lines.gpkg lines 4 "highway IN ('motorway','trunk','primary','motorway_link','trunk_link','primary_link')"
echo "::endgroup::"

echo "::group::compose + tiles"
python3 "$HERE/compose.py" . composite.tif
rm -f "$OUT/$SLUG.mbtiles"
gdal_translate -q -of MBTILES -co TILE_FORMAT=JPEG -co QUALITY=78 -co ZOOM_LEVEL_STRATEGY=UPPER \
  -co NAME="$TITLE" -co DESCRIPTION="نقشه توپوگرافی آفلاین گشت‌یار" composite.tif "$OUT/$SLUG.mbtiles"
gdaladdo -q -r average "$OUT/$SLUG.mbtiles" 2 4 8 16 32 64 128 256
python3 "$HERE/places.py" osm_points.gpkg osm_multipolygons.gpkg "$OUT/$SLUG.mbtiles" "$TITLE"
ls -la "$OUT"
echo "::endgroup::"

echo "::group::previews"
mkdir -p "$OUT/preview"
gdal_translate -q -of JPEG -co QUALITY=85 -outsize 1800 0 composite.tif "$OUT/preview/$SLUG-overview.jpg"
python3 "$HERE/crop.py" composite.tif "$OUT/preview/$SLUG"
ls -la "$OUT/preview"
echo "::endgroup::"
