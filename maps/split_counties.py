#!/usr/bin/env python3
"""
Cuts a provincial MBTiles map into one small map per county (شهرستان), for download from inside
the app. A tile is kept when it lies within BUFFER_KM of the county boundary, so a ranger near
the county edge still sees the land around it. Also writes index.json (the list the app uses).

usage: split_counties.py PROVINCE.mbtiles admin.geojson[.gz] "استان آذربایجان شرقی" OUT_DIR
"""
import gzip, hashlib, json, math, os, sqlite3, sys
import numpy as np

BUFFER_KM = 3.0


def load_geojson(path):
    op = gzip.open if path.endswith('.gz') else open
    with op(path, 'rt', encoding='utf-8') as f:
        return json.load(f)


def rings_of(geom):
    if geom['type'] == 'Polygon':
        return [np.array(r, dtype=float) for r in geom['coordinates']]
    if geom['type'] == 'MultiPolygon':
        return [np.array(r, dtype=float) for p in geom['coordinates'] for r in p]
    return []


def inside(px, py, rings):
    """Even-odd point in polygon for many points at once."""
    res = np.zeros(px.shape, dtype=bool)
    for r in rings:
        x1, y1 = r[:-1, 0], r[:-1, 1]
        x2, y2 = r[1:, 0], r[1:, 1]
        for a, b, c, d in zip(x1, y1, x2, y2):
            cond = (b > py) != (d > py)
            xs = (c - a) * (py - b) / ((d - b) if d != b else 1e-12) + a
            res ^= cond & (px < xs)
    return res


def dist_km(px, py, rings, lat0):
    """Distance (km) from points to the nearest ring segment (local flat approximation)."""
    kx = 111.32 * math.cos(math.radians(lat0)); ky = 110.57
    best = np.full(px.shape, np.inf)
    for r in rings:
        ax, ay = r[:-1, 0] * kx, r[:-1, 1] * ky
        bx, by = r[1:, 0] * kx, r[1:, 1] * ky
        X, Y = (px * kx)[:, None], (py * ky)[:, None]
        dx, dy = bx - ax, by - ay
        L = dx * dx + dy * dy; L[L == 0] = 1e-12
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / L, 0, 1)
        d = np.hypot(X - (ax + t * dx), Y - (ay + t * dy)).min(axis=1)
        best = np.minimum(best, d)
    return best


def tile_center(z, x, y_xyz):
    n = 2 ** z
    lon = (x + 0.5) / n * 360 - 180
    lat = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y_xyz + 0.5) / n))))
    return lon, lat


def tile_half_diag_km(z, lat):
    size = 40075.0 * math.cos(math.radians(lat)) / 2 ** z
    return size * 0.7072


def norm(s):
    return s.replace('ي', 'ی').replace('ك', 'ک').replace('‌', '').replace(' ', '').strip()


def main():
    src, admin_path, province, out = sys.argv[1:5]
    os.makedirs(out, exist_ok=True)
    admin = load_geojson(admin_path)['features']
    prov = next(f for f in admin if f['properties']['l'] == 4 and norm(f['properties']['n']) == norm(province))
    prov_rings = rings_of(prov['geometry'])
    counties = []
    for f in admin:
        if f['properties']['l'] != 5:
            continue
        rings = rings_of(f['geometry'])
        if not rings:
            continue
        outer = max(rings, key=len)
        cx, cy = outer[:, 0].mean(), outer[:, 1].mean()
        if inside(np.array([cx]), np.array([cy]), prov_rings)[0]:
            counties.append((f['properties']['n'], rings))
    print(f'{len(counties)} counties in {province}', flush=True)

    s = sqlite3.connect(src)
    meta = dict(s.execute('SELECT name, value FROM metadata').fetchall())
    tiles = s.execute('SELECT zoom_level, tile_column, tile_row FROM tiles').fetchall()
    tz = np.array([t[0] for t in tiles]); tx = np.array([t[1] for t in tiles]); tr = np.array([t[2] for t in tiles])
    ty = (2 ** tz - 1) - tr  # TMS -> XYZ
    cen = np.array([tile_center(z, x, y) for z, x, y in zip(tz, tx, ty)])
    places = s.execute('SELECT name, kind, lat, lon, ele FROM mohityar_places').fetchall()

    index = []
    for name, rings in counties:
        clean = name.strip()
        for p in ('شهرستان ',):
            if clean.startswith(p):
                clean = clean[len(p):]
        allx = np.concatenate([r[:, 0] for r in rings]); ally = np.concatenate([r[:, 1] for r in rings])
        lat0 = float(ally.mean())
        pad = 0.12
        w, e, so, no = allx.min() - pad, allx.max() + pad, ally.min() - pad, ally.max() + pad
        cand = np.where((cen[:, 0] > w - 0.5) & (cen[:, 0] < e + 0.5) & (cen[:, 1] > so - 0.5) & (cen[:, 1] < no + 0.5))[0]
        keep = []
        for z in sorted(set(tz[cand].tolist())):
            idx = cand[tz[cand] == z]
            px, py = cen[idx, 0], cen[idx, 1]
            ins = inside(px, py, rings)
            reach = tile_half_diag_km(z, lat0) + BUFFER_KM
            near = np.zeros(idx.shape, dtype=bool)
            rest = ~ins
            if rest.any():
                near[rest] = dist_km(px[rest], py[rest], rings, lat0) <= reach
            keep.extend(idx[ins | near].tolist())
        key = hashlib.md5(clean.encode('utf-8')).hexdigest()[:10]
        fname = f'county-{key}.mbtiles'
        path = os.path.join(out, fname)
        if os.path.exists(path):
            os.remove(path)
        d = sqlite3.connect(path)
        d.execute('CREATE TABLE metadata (name TEXT, value TEXT)')
        d.execute('CREATE TABLE tiles (zoom_level INTEGER NOT NULL, tile_column INTEGER NOT NULL, tile_row INTEGER NOT NULL, tile_data BLOB NOT NULL, UNIQUE (zoom_level, tile_column, tile_row))')
        d.execute('CREATE TABLE mohityar_places (name TEXT, kind TEXT, lat REAL, lon REAL, ele REAL)')
        bw, bs, be, bn = allx.min() - 0.03, ally.min() - 0.03, allx.max() + 0.03, ally.max() + 0.03
        m = dict(meta)
        m['name'] = f'شهرستان {clean} — نقشه توپوگرافی'
        m['bounds'] = f'{bw:.5f},{bs:.5f},{be:.5f},{bn:.5f}'
        m['center'] = f'{(bw + be) / 2:.5f},{(bs + bn) / 2:.5f},11'
        m['mohityar_county'] = clean
        d.executemany('INSERT INTO metadata VALUES (?, ?)', list(m.items()))
        for i in range(0, len(keep), 500):
            chunk = keep[i:i + 500]
            for j in chunk:
                row = s.execute('SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?', (int(tz[j]), int(tx[j]), int(tr[j]))).fetchone()
                d.execute('INSERT INTO tiles VALUES (?, ?, ?, ?)', (int(tz[j]), int(tx[j]), int(tr[j]), row[0]))
        d.executemany('INSERT INTO mohityar_places VALUES (?, ?, ?, ?, ?)', [p for p in places if bs - 0.05 <= p[2] <= bn + 0.05 and bw - 0.05 <= p[3] <= be + 0.05])
        d.commit(); d.execute('VACUUM'); d.close()
        size = os.path.getsize(path)
        sha = hashlib.sha256(open(path, 'rb').read()).hexdigest()
        index.append({
            'name': clean, 'province': province.replace('استان ', '', 1).strip(), 'file': fname, 'size': size, 'sha256': sha,
            'bounds': [round(bw, 5), round(bs, 5), round(be, 5), round(bn, 5)], 'tiles': len(keep),
        })
        print(f'{clean}: {len(keep)} tiles, {size / 1e6:.1f} MB', flush=True)

    index.sort(key=lambda r: r['name'])
    old = []
    ip = os.path.join(out, 'index.json')
    with open(ip, 'w', encoding='utf-8') as f:
        json.dump({'version': 1, 'maps': old + index}, f, ensure_ascii=False, indent=1)
    print('total', sum(r['size'] for r in index) / 1e6, 'MB')


if __name__ == '__main__':
    main()
