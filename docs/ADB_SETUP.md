# ADB setup for NSPanel Pro

1. Pair the device once with eWeLink app (mandatory).
2. Enable developer mode: tap device id rapidly in eWeLink.
3. Get IP from router DHCP or eWeLink settings.
4. From a machine with adb:
   ```
   adb tcpip 5555
   adb connect <ip>:5555
   adb devices -l
   ```
5. Install a custom launcher (UltraSmall Launcher) so the device is usable.
6. (Optional) Update WebView for HA companion app / Fully Kiosk.

## Useful commands

```bash
adb connect <ip>:5555
adb shell input keyevent 3   # home
adb shell input keyevent 26  # power
adb shell cmd statusbar expand-notifications
adb install -r app.apk
adb shell screencap -p /sdcard/s.png
adb pull /sdcard/s.png .
```

## Nexus equivalents

```bash
nspanel-nexus adb-connect
nspanel-nexus shell "input keyevent 3"
nspanel-nexus install app.apk
nspanel-nexus screenshot -o s.png
```
