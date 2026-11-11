# NSPanel Nexus — the AIO plan

Yes. Here's exactly what Nexus becomes, phase by phase, and what's real versus
what's scaffolded right now.

## Phase 1 — the AIO app (building now)

One app on the panel, one foreground service, **every function a switch**
(individually enable/disable, from the app's UI *and* remotely over MQTT):

| Module | Status | What it gives you |
|---|---|---|
| **MQTT** | building | HA MQTT discovery, panel events out, commands + **remote config** in — Pro-Tools-compatible protocol so existing HA setups work unchanged |
| **Sensors** | building | proximity with a **learned resting baseline** (the approach that actually handles this panel's firmware-dependent inversion + display-light contamination), light with below/above events |
| **Display** | building | wake/sleep, brightness (manual + light-triggered), screen-on schedules (weekday/weekend), wake-on-proximity wired to the sensor module |
| **Dashboard** | building | HA dashboard kiosk in a WebView (works today); URL hot-swappable over MQTT |
| **Voice** | building | **Assist satellite**: mic → HA pipeline, TTS reply → speaker. Phase 1 is push-to-talk; the wake word is phase 2 |
| **Zigbee** | building | coordinator/router role, permit-join, device list, factory reset — driven over the panel's own local MQTT broker (`zgateway` runs at boot, independent of the Sonoff app) |
| **Firmware** | building | the **Nexus OTA channel** (manifest + sha256, verified dump + rollback) |
| **Fleet** | building | UDP LAN discovery so the host tooling finds every panel |

Everything is one service (`NexusService`) hosting modules registered in a
`ModuleManager`; a module that is disabled consumes nothing.

## Phase 2 — the hard parts

- **Wake word on-device** (openWakeWord / microWakeWord) so the satellite wakes
  hands-free.
- **Native dashboard renderer** that draws `nspanel-*` cards without a WebView
  (the Flutter app proved it is the only way to make this panel feel fast).
- **Bluetooth proxy** (ESPHome) on panels that have a chip.
- **Install/update from Home Assistant** like Panel Assistant does.

## Phase 3 — extras

- Runtime **mods** (Ava-style plugins): DLNA renderer, AirPlay, RTSP, offline
  STT, Zigbee gateway manager.
- Dashboard provisioning (auto-generate per panel, like walldisplay-dashboards).

## What already exists and is proven

- Clean firmware (Sonoff bloat removed, KISS baked in, frp backdoor disabled)
  built and **verified working on the live panel**.
- Verified md5 dumps of **every** partition as the rollback path.
- `nspanel-nexus firmware dump|flash|manifest|serve` CLI.
- Nexus OTA updater script for the panel.
- Debloat script for rooted panels.
- Full research on the app ecosystem and firmware feasibility.

## Build

```bash
cd app && ./gradlew :app:assembleRelease
adb connect <panel>:5555 && adb install -r app/app/build/outputs/apk/release/app-release.apk
```
