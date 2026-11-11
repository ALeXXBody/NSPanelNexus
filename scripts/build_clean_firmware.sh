#!/usr/bin/env bash
# Build the clean firmware for a rooted NSPanel Pro.
#
# Step 1: modify /system in place (removes Sonoff bloat, bakes in the KISS
#         launcher as the default home, disables the Sonoff frp backdoor).
# Step 2: dump the modified /system partition to the host — that dump IS the
#         firmware image.
#
# Both steps are read-only-safe for the panel: the modification is the same
# change that has already been verified working on a live panel.
#
# Usage: ./build_clean_firmware.sh <ip> [--reboot]
set -euo pipefail

IP="${1:-192.168.50.22}"
DO_REBOOT="${2:-}"
TARGET="$IP:5555"

adb connect "$TARGET" >/dev/null
ADB="adb -s $TARGET"

# --- 0. require KISS launcher apk on the host ---
KISS_APK=""
for c in "./kiss.apk" "/tmp/opencode/kiss.apk" "$HOME/kiss.apk"; do
  if [ -f "$c" ]; then KISS_APK="$c"; break; fi
done
if [ -z "$KISS_APK" ]; then
  echo "ERROR: kiss.apk not found. Download it first:"
  echo "  curl -L -o kiss.apk https://f-droid.org/repo/fr.neamar.kiss_219.apk"
  exit 1
fi

# --- 1. modify /system ---
echo "[*] remounting /system rw"
$ADB shell "su -c 'mount -o rw,remount /system; mount -o rw,remount /oem'"

echo "[*] installing KISS launcher into /system/priv-app"
$ADB shell "su -c 'mkdir -p /system/priv-app/Kiss'"
$ADB push "$KISS_APK" /data/local/tmp/Kiss.apk >/dev/null
$ADB shell "su -c 'cp /data/local/tmp/Kiss.apk /system/priv-app/Kiss/Kiss.apk; rm /data/local/tmp/Kiss.apk; chmod 0644 /system/priv-app/Kiss/Kiss.apk; chown root:root /system/priv-app/Kiss/Kiss.apk; chown 0:0 /system/priv-app/Kiss/Kiss.apk; ls -l /system/priv-app/Kiss/'"

echo "[*] removing Sonoff bloat"
for p in \
  /system/app/kz_app \
  /system/app/factoryApp \
  /system/app/RkDeviceTest \
  /system/app/ThomassRoom \
  /system/app/Lightning \
  /system/app/Music \
  /system/priv-app/StressTest \
  /system/priv-app/DeviceTest \
  /oem/bundled_persist-app/CoolKit_22101317 \
; do
  $ADB shell "su -c 'rm -rf $p'" && echo "  removed $p"
done

echo "[*] disabling Sonoff test packages"
for pkg in com.rockchip.devicetest com.cghs.stresstest com.DeviceTest com.smatek.test com.eWeLinkNSPro.dev com.android.gl2jni com.eWeLinkControlPanel; do
  $ADB shell "su -c 'pm disable $pkg 2>/dev/null || true'" >/dev/null
done

echo "[*] disabling the Sonoff frp backdoor in rootsudaemon.sh"
$ADB shell "su -c 'sed -i \"/cp -rf \/oem\/frp/d; /chmod -R 777 \/data\/local\/tmp\/frp/d\" /system/bin/rootsudaemon.sh'"
$ADB shell "su -c 'grep -n frp /system/bin/rootsudaemon.sh || echo  \"  frp lines removed\"'"

echo "[*] syncing"
$ADB shell "su -c 'sync; mount -o ro,remount /system'"

echo "[*] verifying"
$ADB shell "su -c 'ls /system/priv-app/Kiss/; ls -d /system/app/kz_app 2>&1 || true'"
$ADB shell "su -c 'df -h /system | tail -1'"

if [ "$DO_REBOOT" = "--reboot" ]; then
  echo "[*] rebooting (AFTER reboot, verify the desktop appears)"
  $ADB reboot
  echo "    Wait ~60 s, then: adb connect $TARGET && adb shell input keyevent 3 && adb shell screencap -p /sdcard/boot.png && adb pull /sdcard/boot.png"
else
  echo "[*] NOT rebooting (use --reboot when you are ready to verify the first boot)"
fi
