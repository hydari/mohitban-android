#!/usr/bin/env python3
"""
Makes the package for the update host (muhitban.ir/app/application) from a built APK.

    MOHITBAN_UPDATE_KEY=<hex> python3 tools/pack_update.py APK VERSION_CODE VERSION_NAME OUT_DIR [NOTES]

OUT_DIR receives:
  version.bin  what the newest version is (encrypted JSON)
  app.bin      the APK itself (encrypted)
  index.php    the gate: hands both files only to the app (a request signed with the app's key)
  .htaccess    no folder listing and no direct download of the .bin files (Apache / LiteSpeed)

Both .bin files use the app's backup format (BackupCrypto.kt: "MHYBK1", PBKDF2-SHA1 key, AES-256-GCM in
64 KB chunks with the last chunk marked), keyed with MOHITBAN_UPDATE_KEY, so a file taken from the host
without the app is unreadable, and a changed or cut-off file is refused by the app.
"""
import hashlib
import hmac
import json
import os
import struct
import sys
import time

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"MHYBK1"
CHUNK = 64 * 1024
# the key is 256 random-looking bits, not a word a person chose: few iterations are enough and keep
# the check quick on old phones (BackupCrypto accepts 10 000 and up)
ITERATIONS = 10_000


def encrypt(data: bytes, password: str) -> bytes:
    salt = os.urandom(16)
    base = os.urandom(12)
    key = hashlib.pbkdf2_hmac("sha1", password.encode("utf-8"), salt, ITERATIONS, 32)
    aes = AESGCM(key)
    out = [MAGIC, struct.pack(">I", ITERATIONS), salt, base]
    chunks = [data[i:i + CHUNK] for i in range(0, len(data), CHUNK)] or [b""]
    for n, chunk in enumerate(chunks):
        nonce = bytearray(base)
        for i in range(8):
            nonce[11 - i] ^= (n >> (8 * i)) & 0xFF
        last = n == len(chunks) - 1
        ct = aes.encrypt(bytes(nonce), chunk, b"\x01" if last else b"\x00")
        out.append(struct.pack(">I", len(ct)))
        out.append(ct)
    return b"".join(out)


def gate_key(update_key: str) -> str:
    """The secret of index.php; the app derives the same one (AppUpdate.gateKey)."""
    return hmac.new(bytes.fromhex(update_key), b"gate", hashlib.sha256).hexdigest()


PHP = r"""<?php
// Mohityar update gate. Hands the encrypted files only to the app: every request carries the time and an
// HMAC made with the app's key. The files are encrypted as well, so even a copy taken directly is useless.
// Made automatically by tools/pack_update.py; upload it unchanged together with version.bin and app.bin.
$KEY = '__GATE_KEY__';
$FILES = array('version' => 'version.bin', 'app' => 'app.bin');
$WINDOW = 900; // seconds a signed request stays valid

function deny() {
    header('X-Mohityar-Now: ' . time()); // lets a phone with a wrong clock correct itself and try again
    http_response_code(404);
    exit;
}

$f = isset($_GET['f']) ? $_GET['f'] : '';
$t = isset($_SERVER['HTTP_X_MOHITYAR_TIME']) ? $_SERVER['HTTP_X_MOHITYAR_TIME'] : '';
$s = isset($_SERVER['HTTP_X_MOHITYAR_SIG']) ? strtolower($_SERVER['HTTP_X_MOHITYAR_SIG']) : '';
if (!isset($FILES[$f]) || !ctype_digit($t) || abs(time() - (int)$t) > $WINDOW) deny();
if (!hash_equals(hash_hmac('sha256', $f . '|' . $t, $KEY), $s)) deny();

$path = __DIR__ . '/' . $FILES[$f];
if (!is_file($path)) { http_response_code(404); exit; }

@set_time_limit(0);
while (ob_get_level() > 0) ob_end_clean();
header('Content-Type: application/octet-stream');
header('Content-Length: ' . filesize($path));
header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');
$h = fopen($path, 'rb');
fpassthru($h);
fclose($h);
"""

HTACCESS = """# Mohityar update folder: no listing, and the encrypted files only through index.php
Options -Indexes
<FilesMatch "\\.bin$">
    <IfModule mod_authz_core.c>
        Require all denied
    </IfModule>
    <IfModule !mod_authz_core.c>
        Order allow,deny
        Deny from all
    </IfModule>
</FilesMatch>
"""


def main() -> None:
    if len(sys.argv) < 5:
        sys.exit(__doc__)
    apk_path, version_code, version_name, out_dir = sys.argv[1:5]
    notes = sys.argv[5] if len(sys.argv) > 5 else ""
    key = os.environ.get("MOHITBAN_UPDATE_KEY", "").strip()
    if len(key) != 64:
        sys.exit("MOHITBAN_UPDATE_KEY (64 hex characters) is missing")

    apk = open(apk_path, "rb").read()
    manifest = {
        "versionCode": int(version_code),
        "versionName": version_name,
        "size": len(apk),
        "sha256": hashlib.sha256(apk).hexdigest(),
        "notes": notes.strip()[:500],
        "publishedAt": int(time.time() * 1000),
    }
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "app.bin"), "wb") as f:
        f.write(encrypt(apk, key))
    with open(os.path.join(out_dir, "version.bin"), "wb") as f:
        f.write(encrypt(json.dumps(manifest, ensure_ascii=False).encode("utf-8"), key))
    with open(os.path.join(out_dir, "index.php"), "w", encoding="utf-8") as f:
        f.write(PHP.replace("__GATE_KEY__", gate_key(key)))
    with open(os.path.join(out_dir, ".htaccess"), "w", encoding="utf-8") as f:
        f.write(HTACCESS)
    print(f"update package: version {version_name} ({version_code}), {len(apk)} bytes")


if __name__ == "__main__":
    main()
