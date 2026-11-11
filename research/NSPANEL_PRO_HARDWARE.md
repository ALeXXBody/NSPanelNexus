# NSPanel Pro — Hardware & Platform Research

## What it is

The Sonoff **NSPanel Pro** is NOT a microcontroller panel like the classic
NSPanel. It is a small Android tablet in a wall-switch form factor:

- **SoC**: Rockchip PX30 (Cortex-A35) on the 86" model; Rockchip RK3326S on the
  120" model.
- **Display**: 3.96" 480×480 (86) / 4.7" 750×1334 (120), capacitive touch.
- **Memory**: 2 GB RAM, 8 GB eMMC.
- **Radios**: Wi-Fi, Bluetooth, Zigbee 3.0 (via Silicon Labs ZStack).
- **Sensors**: proximity, ambient light, (GPIO on some variants).
- **OS**: Android 8.1 Oreo (AOSP), rooted by accepting the eWeLink dev-mode
  EULA. No Google Play Services by default.
- **Extras**: speaker, microphone, 2 built-in relays (SW1/SW2) on some models.

Because it is Android, the whole modding story is "sideload apps over ADB" rather
than "flash ESP firmware". There is no TFT/HMI to compile.

## Gaining control (rooting / ADB)

1. Pair the device once with the eWeLink app (mandatory — licence data).
2. Tap the device id quickly in the eWeLink app to enable **Developer mode**.
   Accepting is permanent and disables OTA updates.
3. `adb tcpip 5555` → `adb connect <ip>` (ADB over TCP is enabled by default).
4. Install a custom launcher (e.g. UltraSmall Launcher) so the panel is usable.
5. Optionally update the WebView (arm64/armv7 build) to run HA companion app /
   Fully Kiosk.

See `seaky/nspanel_pro_roottool_apk` for re-installing official OTA zips on a
rooted unit, and `blakadder.com/nspanel-pro-sideload` for the full walkthrough.

## Firmware versions — pick carefully

- **≤ 1.9 / 2.x**: keeps adjustable proximity threshold and screen-off touch
  events. Recommended by the community for modders.
- **≥ 1.10**: single-touch wake by default → loses screen-off touch events.
- **≥ 3.0**: new kernel proximity driver → proximity threshold no longer
  adjustable (capability loss).
- **≥ 3.6**: only incremental OTA packages.

## What "controlling the panel" means here

Unlike the classic NSPanel (MQTT + TFT), the Pro is controlled through:

- **ADB** (shell, install, screenshot, keyevents, reboot).
- **MQTT** (when running seaky Pro Tools or Panel Assistant): wake/sleep,
  brightness, switches, config query/update, gestures, sensors.
- **Home Assistant** via MQTT discovery (seaky) or the HA add-on/installer
  (Panel Assistant).
- **HTTP** endpoints exposed by some apps (zigbee2mqtt frontend on :8080).

## Related repos / prior art (see ECOSYSTEM.md)

- seaky/nspanel_pro_tools_apk — the reference on-device tool (Android APK).
- seaky/nspanel_pro_roottool_apk — OTA re-flasher for rooted units.
- jxlarrea/kiosk-satellite — HA kiosk + voice satellite (supports NSPanel Pro
  proximity quirk).
- panel-assistant (maxlyth/ha-paneld + ha-integration) — full HA-managed
  install/update/voice satellite.
- knoop7/Ava — lightweight HA satellite with Bluetooth proxy.
- maxlyth/ha-paneld — universal Android wall-panel dashboard app.
- RapierXbox/walldisplay-dashboards — auto-generated HA dashboards.
- Fully Kiosk Browser — popular kiosk layer on top of Pro Tools.
- Blackymas/NSPanel_HA_Blueprint / edwardtfn/NSPanel-Easy — classic NSPanel
  only (ESPHome), not Pro.
