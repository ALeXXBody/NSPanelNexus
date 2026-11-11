#!/usr/bin/env bash
# Debloat a rooted NSPanel Pro via ADB.
# Removes Sonoff factory/test bloat and (optionally) the Sonoff control panel app.
# Usage: ./debloat_nspanel_pro.sh <ip> [--keep-sonoff]
set -euo pipefail

IP="${1:-192.168.50.22}"
KEEP_SONOFF="${2:-}"
TARGET="$IP:5555"

adb connect "$TARGET" >/dev/null
ADB="adb -s $TARGET"

echo "[*] remounting /system /vendor /oem read-write"
$ADB shell "su -c 'mount -o rw,remount /system; mount -o rw,remount /vendor; mount -o rw,remount /oem'"

echo "[*] removing factory/test bloat"
for p in \
  /system/app/factoryApp \
  /system/app/RkDeviceTest \
  /system/priv-app/StressTest \
  /system/priv-app/DeviceTest \
  /system/app/ThomassRoom \
  /oem/bundled_persist-app/CoolKit_22101317 \
; do
  $ADB shell "su -c 'rm -rf $p'" && echo "  removed $p"
done

if [ "$KEEP_SONOFF" != "--keep-sonoff" ]; then
  echo "[*] removing Sonoff control panel (kz_app / eWeLinkControlPanel)"
  $ADB shell "su -c 'rm -rf /system/app/kz_app'"
  $ADB shell "su -c 'pm uninstall com.eWeLinkControlPanel 2>/dev/null || true'"
else
  echo "[*] keeping Sonoff control panel"
fi

echo "[*] disabling extra test packages"
for pkg in com.rockchip.devicetest com.cghs.stresstest com.DeviceTest com.smatek.test com.android.gl2jni; do
  $ADB shell "su -c 'pm disable $pkg 2>/dev/null || true'" >/dev/null
done

echo "[*] syncing"
$ADB shell "su -c 'sync'"

echo "[*] done. Rebooting."
$ADB reboot
echo "Reboot issued. Wait ~60s then reconnect."
