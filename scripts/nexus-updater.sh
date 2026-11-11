#!/usr/bin/env bash
# Nexus OTA updater for NSPanel Pro — GitHub Release channel.
#
# Checks a manifest (GitHub release or any URL), downloads the system image,
# verifies sha256 + size, backs up the current system, writes the new one
# and reboots.
#
# Usage (on panel, as root):
#   nexus-updater.sh <manifest_url> [--force] [--rollback]
#
# The manifest looks like:
#   {
#     "version": "1.0.0",
#     "url": "https://github.com/…/releases/download/…/system.img",
#     "sha256": "…",
#     "size": 1610612736
#   }
set -eu

URL="${1:-}"
FORCE="${2:-}"
VERFILE=/data/nexus/version
TMP=/data/nexus/system.img.new
BK=/data/nexus/system.img.bak
SYSTEM_DEV=/dev/block/mmcblk1p11

if [ "${1:-}" = "--rollback" ]; then
  [ -f "$BK" ] || { echo "no backup found at $BK"; exit 1; }
  echo "[rollback] writing backup to $SYSTEM_DEV"
  dd if="$BK" of="$SYSTEM_DEV" bs=1048576 2>/dev/null
  sync
  rm -f "$BK"
  echo "[rollback] done — rebooting"
  reboot
  exit 0
fi

[ -z "$URL" ] && { echo "usage: nexus-updater.sh <manifest_url> [--force|--rollback]"; exit 1; }
mkdir -p /data/nexus

echo "[*] fetching manifest"
curl -sL --fail "$URL" -o /data/nexus/manifest.json || { echo "manifest fetch failed"; exit 1; }
NEWVER=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' /data/nexus/manifest.json | head -1 | sed 's/.*: *"\([^"]*\)"/\1/')
IMGURL=$(grep -o '"url"[[:space:]]*:[[:space:]]*"[^"]*"' /data/nexus/manifest.json | head -1 | sed 's/.*: *"\([^"]*\)"/\1/')
SHA=$(grep -o '"sha256"[[:space:]]*:[[:space:]]*"[^"]*"' /data/nexus/manifest.json | head -1 | sed 's/.*: *"\([^"]*\)"/\1/')
SIZE=$(grep -o '"size"[[:space:]]*:[[:space:]]*[0-9]*' /data/nexus/manifest.json | head -1 | sed 's/.*: *//')
echo "    version=$NEWVER size=$SIZE"

CUR=$(cat "$VERFILE" 2>/dev/null || echo none)
if [ "$CUR" = "$NEWVER" ] && [ "$FORCE" != "--force" ]; then
  echo "[*] already up to date ($CUR)"
  exit 0
fi

echo "[*] downloading image"
curl -sL --fail "$IMGURL" -o "$TMP"
ACTUAL_SIZE=$(stat -c %s "$TMP")
[ "$ACTUAL_SIZE" != "$SIZE" ] && { echo "size mismatch ($ACTUAL_SIZE != $SIZE)"; rm -f "$TMP"; exit 1; }
ACTUAL_SHA=$(sha256sum "$TMP" | cut -d' ' -f1)
[ "$ACTUAL_SHA" != "$SHA" ] && { echo "sha256 mismatch"; rm -f "$TMP"; exit 1; }
echo "[*] image verified (sha256 ✓, size ✓)"

echo "[*] backing up current system → $BK"
dd if="$SYSTEM_DEV" of="$BK" bs=1048576 2>/dev/null
sync

echo "[*] writing new system"
dd if="$TMP" of="$SYSTEM_DEV" bs=1048576 2>/dev/null
sync
rm -f "$TMP"
echo "$NEWVER" > "$VERFILE"

echo "[*] done — rebooting (rollback available: nexus-updater.sh --rollback)"
reboot
