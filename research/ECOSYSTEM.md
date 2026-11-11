# Ecosystem — what other people have built for NSPanel Pro

This is the landscape NSPanel Nexus unifies. Each row is a project we either
integrate with, wrap, or learn from.

| Project | Type | What it does | Relevance to Nexus |
|---|---|---|---|
| **seaky/nspanel_pro_tools_apk** | Android APK (on-panel) | The reference tool: wake-on-wave/gesture, brightness, screen-on schedules, autostart, MQTT + HA discovery, Zigbee (Sonoff/z2m packages), GPIO, screenshots, config over MQTT | **Primary protocol source** — Nexus speaks its MQTT command/config protocol |
| **seaky/nspanel_pro_roottool_apk** | Android APK | Re-installs official OTA zips on rooted panels | Documented in install guide; Nexus can push OTA zips via ADB |
| **jxlarrea/kiosk-satellite** | Android APK | HA kiosk + voice satellite; handles NSPanel Pro proximity quirk (inverted 0/1 vs 1000–4000) | Integrate / detect proximity mode |
| **panel-assistant (maxlyth)** | Android APK + HACS integration | HA-managed install/update/repair, voice satellite, Bluetooth proxy, hardware profiles | Complement — Nexus can drive its ADB endpoints |
| **knoop7/Ava** | Android APK | Lightweight HA satellite + BT proxy | Alternative frontend option |
| **maxlyth/ha-paneld** | Android APK | Universal wall-panel dashboard app with hardware profiles | Profile reuse for NSPanel Pro |
| **Fully Kiosk Browser** | Android APK (commercial) | Kiosk layer, screensaver, motion detection | Commonly stacked with Pro Tools |
| **RapierXbox/walldisplay-dashboards** | HA integration | Auto-generates dashboards for wall displays | Dashboard provisioning helper |
| **Blackymas/NSPanel_HA_Blueprint** | HA blueprint | Classic NSPanel (ESPHome) only | Not applicable to Pro (different hardware) |
| **edwardtfn/NSPanel-Easy** | ESPHome | Classic NSPanel only | Not applicable to Pro |
| **NSPManager/NSPanelManager** | Docker + firmware | Classic NSPanel manager | Upstream of our NSPanel Core (non-Pro) |

## Feature matrix we aim to unify

Grouped by the seaky Pro Tools manual, plus additions from the rest of the ecosystem.

### Display
- Wake-on-wave, wake-on-gesture (multi-tap/swipe), wake-on-network-error
- Brightness (manual + light-below/above triggered)
- Display sleep, sleep mode (off/dim), screen-on schedules (weekday/weekend)
- Set display rotation, dim level
- Screenshot capture (seaky v2.3.4+)

### Sensors
- Proximity (threshold; note FW≥3.0 fixed 0/1)
- Light (below/above/normal events)
- GPIO monitoring
- Inverted proximity logic (seaky v2.3.4)

### Tools
- Autostart app after reboot, wait-for-Wi-Fi, watchdog
- Switch-to-app, switch-to-launcher, home-on-gesture
- Navigation/notification bar toggles
- Scheduled reboot
- Multi-touch gestures on dark screen

### Integration (MQTT / HA)
- MQTTv3/v5 (SSL/TLS planned), multiple connections
- Publish selectable events with sequence numbers
- HA MQTT discovery (entities + diagnostics)
- Commands: wake_up, sleep, reboot, launcher, default_app, adb, switches
- Config query/update over MQTT
- Zigbee: coordinator/router, z2m/Sonoff packages, permit join, device list

### System
- Hostname, reboot, ADB enable
- Debug/verbose logging, view log
- Device admin for sleep
- Root tool / OTA re-flash

### Nexus additions (new)
- Unified CLI over ADB + MQTT (this repo)
- Proximity-mode auto-detection (kiosk-satellite quirk)
- Screenshot → file pipeline
- HA discovery publisher helper
- Config backup/restore
- Multi-panel fleet management (planned)
- Dashboard provisioning via walldisplay-dashboards (planned)
