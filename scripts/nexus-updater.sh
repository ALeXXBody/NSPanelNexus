#!/system/bin/sh
# Nexus OTA updater for NSPanel Pro.
# Checks a manifest, downloads the system image, verifies sha256 and writes it.
#
# Usage (on panel, as root):
#   nexus-updater.sh <manifest_url> [--force]
#
# The manifest looks like:
#   { "version": "1.0.0", "url": "http://host/system.img", "sha256": "...", "size": ... }
set -eu

URL="${1:-}"
FORCE="${2:-}"
VERFILE=/data/nexus/version
TMP=/data/nexus/system.img.new
BK=/data/nexus/system.img.bak

[ -z "$URL" ] && { echo "usage: nexus-updater.sh <manifest_url>"; exit 1; }
mkdir -p /data/nexus

echo "[*] fetching manifest"
curl -s "$URL" -o /data/nexus/manifest.json
NEWVER=$(grep -o '"version":"[^"]*"' /data/nexus/manifest.json | head -1 | cut -d'"' -f4)
IMGURL=$(grep -o '"url":"[^"]*"' /data/nexus/manifest.json | head -1 | cut -d'"' -f4)
SHA=$(grep -o '"sha256":"[^"]*"' /data/nexus/manifest.json | head -1 | cut -d'"' -f4)
SIZE=$(grep -o '"size":[0-9]*' /data/nexus/manifest.json | head -1 | cut -d':' -f2)
echo "    version=$NEWVER size=$SIZE"

CUR=$(cat "$VERFILE" 2>/dev/null || echo none)
if [ "$CUR" = "$NEWVER" ] && [ "$FORCE" != "--force" ]; then
  echo "[*] already up to date ($CUR)"
  exit 0
fi

echo "[*] downloading image"
curl -s "$IMGURL" -o "$TMP"
[ "$(stat -c %s "$TMP")" != "$SIZE" ] && { echo "size mismatch"; rm -f "$TMP"; exit 1; }
ACTUAL=$(sha256sum "$TMP" | cut -d' ' -f1)
[ "$ACTUAL" != "$SHA" ] && { echo "sha256 mismatch"; rm -f "$TMP"; exit 1; }
echo "[*] image verified"

echo "[*] backing up current system"
dd if=/dev/block/mmcblk1p11 of="$BK" bs=1048576 2>/dev/null

echo "[*] writing new system"
dd if="$TMP" of=/dev/block/mmcblk1p11 bs=1048576 2>/dev/null
sync
rm -f "$TMP"
echo "$NEWVER" > "$VERFILE"

echo "[*] done — rebooting"
reboot
