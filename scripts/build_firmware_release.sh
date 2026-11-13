#!/usr/bin/env bash
# Build and publish a NSPanel Nexus firmware release.
#
# Pipeline (all steps verified on the live panel):
#   1. Ensure the panel is debloated (runs build_clean_firmware.sh if needed)
#   2. Dump the /system partition (md5-verified against the device)
#   3. Package: manifest.json (version, size, sha256) + system.img
#   4. Publish to GitHub Releases (gh release create with the image as an asset)
#
# Usage:
#   ./build_firmware_release.sh <panel_ip> <version> [--publish]
#
# Example:
#   ./build_firmware_release.sh <panel_ip> 1.0.0 --publish
set -euo pipefail

IP="${1:?usage: build_firmware_release.sh <panel_ip> <version> [--publish]}"
VERSION="${2:?version required}"
PUBLISH="${3:-}"
TARGET="$IP:5555"
REPO="${GITHUB_REPO:-ALeXXBody/NSPanelNexus}"
OUTDIR="${FIRMWARE_OUT:-.}"

adb connect "$TARGET" >/dev/null
ADB="adb -s $TARGET"

echo "=== NSPanel Nexus firmware release builder ==="
echo "  panel:  $TARGET"
echo "  version: $VERSION"
echo "  repo:   $REPO"
echo

# ---- 1. verify the panel is in the debloated state --------------------
echo "[1/5] checking panel state"
KISS_OK=$($ADB shell "su -c 'test -f /system/priv-app/Kiss/Kiss.apk && echo yes || echo no'" | tr -d '\r')
KZ_OK=$($ADB shell "test -d /system/app/kz_app && echo yes || echo no" | tr -d '\r')
if [ "$KISS_OK" != "yes" ] || [ "$KZ_OK" = "yes" ]; then
  echo "  panel is NOT debloated — running build_clean_firmware.sh first"
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  bash "$SCRIPT_DIR/build_clean_firmware.sh" "$IP"
  echo "  waiting for the panel to reboot…"
  sleep 60
  adb connect "$TARGET" >/dev/null
  sleep 30
KISS_OK=$($ADB shell "su -c 'test -f /system/priv-app/Kiss/Kiss.apk && echo yes || echo no'" | tr -d '\r')
  if [ "$KISS_OK" != "yes" ]; then
    echo "ERROR: debloat did not complete"; exit 1
  fi
fi
echo "  panel debloated ✓ (KISS launcher present, Sonoff apps removed)"

# ---- 2. dump the system partition (md5-verified) ----------------------
echo "[2/5] dumping /system"
SYSTEM_IMG="$OUTDIR/system.img"
$ADB shell "su -c 'dd if=/dev/block/mmcblk1p11 of=/data/nx.img bs=1048576 2>/dev/null; chmod 644 /data/nx.img; sync'" >/dev/null
$ADB pull /data/nx.img "$SYSTEM_IMG" 2>&1 | tail -1
$ADB shell "su -c "rm -f /data/nx.img""

HOST_MD5=$(md5sum "$SYSTEM_IMG" | cut -d' ' -f1)
DEV_MD5=$($ADB shell "su -c 'md5sum /dev/block/mmcblk1p11'" | cut -d' ' -f1)
SIZE=$(stat -c %s "$SYSTEM_IMG")
if [ "$HOST_MD5" != "$DEV_MD5" ]; then
  echo "ERROR: md5 mismatch (host $HOST_MD5 != device $DEV_MD5)"; exit 1
fi
if [ "$SIZE" != "1610612736" ]; then
  echo "ERROR: unexpected size $SIZE"; exit 1
fi
echo "  system.img: $SIZE bytes, md5 $HOST_MD5 ✓"

# ---- 3. package manifest ---------------------------------------------
echo "[3/5] writing manifest"
SHA256=$(sha256sum "$SYSTEM_IMG" | cut -d' ' -f1)
cat > "$OUTDIR/manifest.json" <<EOF
{
  "version": "$VERSION",
  "url": "system.img",
  "size": $SIZE,
  "sha256": "$SHA256"
}
EOF
cat "$OUTDIR/manifest.json"

# ---- 4. GitHub release ------------------------------------------------
if [ "$PUBLISH" = "--publish" ]; then
  echo "[4/5] publishing to GitHub Releases"
  # upload the image first, then attach the manifest with the release URL
  gh release create "firmware-$VERSION" \
    --repo "$REPO" \
    --target main \
    --title "Firmware $VERSION" \
    --notes "Clean NSPanel Pro firmware (Android 8.1, debloated, KISS Launcher).
sha256: \`$SHA256\`
md5: \`$HOST_MD5\`" \
    "$SYSTEM_IMG" 2>&1 | tail -1
  ASSET_URL="https://github.com/$REPO/releases/download/firmware-$VERSION/system.img"
  # update the manifest with the final URL and re-upload
  python3 -c "
import json
m = json.load(open('$OUTDIR/manifest.json'))
m['url'] = '$ASSET_URL'
json.dump(m, open('$OUTDIR/manifest.json','w'), indent=2)
"
  gh release upload "firmware-$VERSION" --repo "$REPO" --clobber "$OUTDIR/manifest.json"
  echo "  release: https://github.com/$REPO/releases/tag/firmware-$VERSION"
  # keep the in-repo manifest (the panel's default manifest URL) tracking latest
  cp "$OUTDIR/manifest.json" "$(dirname "$0")/../firmware/manifest.json"
  echo "  in-repo firmware/manifest.json updated — commit + push it so panels see the new version"
else
  echo "[4/5] skipping publish (no --publish flag)"
  echo "  to publish: gh release create firmware-$VERSION --repo $REPO $SYSTEM_IMG $OUTDIR/manifest.json"
fi

# ---- 5. summary -------------------------------------------------------
echo "[5/5] done"
echo
echo "To install on a panel:"
echo "  nspanel-nexus firmware flash system $SYSTEM_IMG"
echo "Or OTA from the panel (as root):"
echo "  nexus-updater.sh <manifest_url>"
echo
echo "Rollback: keep the stock image (system_stock.img) — nspanel-nexus firmware flash system <stock_img>"
