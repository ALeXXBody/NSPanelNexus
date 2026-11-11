#!/usr/bin/env bash
# Debloat a rooted NSPanel Pro via ADB.
# 1. Installs KISS Launcher (minimal, fast) and sets it as default home.
# 2. Removes Sonoff factory/test bloat (and optionally the Sonoff control panel).
# 3. Reboots.
#
# Prerequisites: rooted panel with ADB over TCP, host has adb + (optional) kiss.apk.
# Download KISS apk first:
#   curl -L -o kiss.apk https://f-droid.org/repo/fr.neamar.kiss_219.apk
#
# Usage: ./debloat_nspanel_pro.sh <ip> [--keep-sonoff]
set -euo pipefail

IP="${1:-192.168.50.22}"
KEEP_SONOFF="${2:-}"
TARGET="$IP:5555"

adb connect "$TARGET" >/dev/null
ADB="adb -s $TARGET"

# --- 0. ensure a launcher is installed BEFORE removing Sonoff ---
KISS_APK=""
for c in "./kiss.apk" "/tmp/opencode/kiss.apk" "$HOME/kiss.apk"; do
  if [ -f "$c" ]; then KISS_APK="$c"; break; fi
done

if [ -n "$KISS_APK" ]; then
  echo "[*] installing KISS launcher from $KISS_APK"
  $ADB push "$KISS_APK" /data/local/tmp/kiss.apk >/dev/null
  $ADB shell "pm install -r /data/local/tmp/kiss.apk" | tail -1
  $ADB shell "cmd package set-home-activity fr.neamar.kiss/.MainActivity" >/dev/null
  echo "    default home set to KISS"
else
  echo "[!] kiss.apk not found; assuming a launcher is already default home."
  echo "    Download: curl -L -o kiss.apk https://f-droid.org/repo/fr.neamar.kiss_219.apk"
fi

# verify home resolves to something other than Sonoff
HOME_ACT=$($ADB shell "cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | tr -d '\r' | tail -1")
echo "[*] current home: $HOME_ACT"

echo "[*] remounting /system /vendor /oem read-write"
$ADB shell "su -c 'mount -o rw,remount /system; mount -o rw,remount /vendor; mount -o rw,remount /oem'"

echo "[*] backing up bloat to /data/backup_bloat (rollback)"
$ADB shell "su -c 'mkdir -p /data/backup_bloat; cp -r /system/app/kz_app /data/backup_bloat/ 2>/dev/null; cp -r /system/app/factoryApp /data/backup_bloat/ 2>/dev/null; cp -r /system/app/RkDeviceTest /data/backup_bloat/ 2>/dev/null; cp -r /system/priv-app/StressTest /data/backup_bloat/ 2>/dev/null; cp -r /system/priv-app/DeviceTest /data/backup_bloat/ 2>/dev/null; cp -r /system/app/ThomassRoom /data/backup_bloat/ 2>/dev/null; cp -r /oem/bundled_persist-app/CoolKit_22101317 /data/backup_bloat/ 2>/dev/null; echo backup-done'"

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
else
  echo "[*] keeping Sonoff control panel (--keep-sonoff)"
fi

echo "[*] disabling test packages"
for pkg in com.rockchip.devicetest com.cghs.stresstest com.DeviceTest com.smatek.test com.eWeLinkNSPro.dev com.android.gl2jni; do
  $ADB shell "su -c 'pm disable $pkg 2>/dev/null || true'" >/dev/null
done

$ADB shell "su -c 'sync'"
echo "[*] done. Rebooting."
$ADB reboot
echo "Reboot issued. Wait ~60s, then verify ADB + home screen screenshot."
