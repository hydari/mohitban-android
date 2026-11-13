#!/usr/bin/env python3
"""
Pins every county map in assets/geo/maps.json to the exact file the app downloads.

Usage:  python3 maps/add_sha256.py DIR
where DIR holds the files exactly as they are served from the maps site
(county-xxxxxxxxxx.mbtiles - the encrypted ones, not the decrypted maps).
Each entry gets a "sha256"; the app then refuses a download that differs.
"""
import hashlib, json, os, sys

REG = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "geo", "maps.json")

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()

def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    folder = sys.argv[1]
    with open(REG, encoding="utf-8") as f:
        reg = json.load(f)
    missing = []
    for m in reg["maps"]:
        p = os.path.join(folder, m["file"])
        if not os.path.isfile(p):
            missing.append(m["file"]); continue
        if os.path.getsize(p) != m["size"]:
            sys.exit(f"{m['file']}: size {os.path.getsize(p)} differs from the registry ({m['size']})")
        m["sha256"] = sha256(p)
    with open(REG, "w", encoding="utf-8") as f:
        json.dump(reg, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(reg['maps']) - len(missing)} maps pinned" + (f", {len(missing)} files not found: {missing}" if missing else ""))

if __name__ == "__main__":
    main()
