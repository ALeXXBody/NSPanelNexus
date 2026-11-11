# NSPanel Nexus — Android app (AIO)

One app on the panel, every function a switch.

## Architecture

One foreground service hosts **modules**. Every module is independently
enable/disable from the app's UI (and remotely, over MQTT). MQTT is the spine:
events go out, commands and configuration come in, and the app registers itself
in Home Assistant with MQTT discovery — the same protocol seaky Pro Tools uses,
so existing HA setups keep working.

```
NexusService (foreground)
 ├── MqttModule        HA discovery · events · commands · remote config
 ├── SensorModule      proximity (learned baseline) · light
 ├── DisplayModule     wake/sleep · brightness · light-triggered · schedules
 ├── DashboardModule   HA dashboard kiosk (WebView now, native later)
 ├── VoiceModule       Assist satellite: mic → HA pipeline, TTS → speaker
 ├── ZigbeeModule      coordinator/router via the panel's own zgateway
 ├── FirmwareModule    Nexus OTA channel (manifest + sha256, verified)
 └── FleetModule       UDP discovery · multi-panel
```

## Status

| Module | Phase 1 | Notes |
|---|---|---|
| MQTT | ✅ building | Pro-Tools-compatible protocol |
| Sensors | ✅ building | learned baseline (Flutter-app approach) |
| Display | ✅ building | wake/brightness/schedules |
| Dashboard | ✅ WebView | native renderer in phase 2 |
| Voice | ✅ push-to-talk + TTS | wake word in phase 2 |
| Zigbee | ✅ coordinator/router | via the panel's zgateway |
| Firmware/OTA | ✅ | verified dumps + rollback |
| Fleet | ✅ UDP discovery | |

## Build

```bash
./gradlew :app:assembleRelease
adb connect <panel>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```
