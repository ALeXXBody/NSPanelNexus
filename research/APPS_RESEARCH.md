# NSPanel Pro — App & Tool Ecosystem Research

A survey of the Android apps and tools the community uses on the Sonoff
NSPanel Pro (86P / 120P), what they actually do, and where the gaps are that
NSPanel Nexus could fill.

## 1. seaky NSPanel Pro Tools (the reference)

`seaky/nspanel_pro_tools_apk` · Android APK, runs on the panel · closed source

The original and still the most NSPanel-Pro-specific tool. It is a background
service + settings app that talks to the panel's own sensors:

| Area | What it does |
|---|---|
| Wake | wake-on-wave (proximity), wake-on-gesture, wake on network error |
| Display | brightness (manual + light-below/above triggered), dim level, display sleep, sleep mode (off/dim), screen-on schedules (weekday/weekend) |
| Sensors | proximity (threshold, inverted logic option), light (below/above/normal events), GPIO monitoring |
| Tools | autostart app after reboot, wait-for-Wi-Fi, watchdog, switch-to-app, switch-to-launcher, home-on-gesture, navigation/notification bar toggles, scheduled reboot, multi-touch gestures on dark screen |
| Integration | MQTT v3 (events + commands + config query/update over MQTT), **Home Assistant MQTT discovery** (auto-creates entities) |
| Zigbee | coordinator/router mode, installable ZStack packages (Sonoff / Zigbee2MQTT), permit join, device list, factory reset |
| System | hostname, reboot, ADB enable, debug/verbose logging, audio feedback, screenshot capture |
| Extras | MQTT-based remote configuration (query/update the app's own settings over MQTT), device admin for sleep |

Key design detail: **it speaks MQTT both ways** — it publishes panel events and
accepts commands *and* configuration changes over MQTT, so the whole app can be
driven from Home Assistant without touching the panel. That is the pattern
Nexus already mirrors.

Weak points: closed source, single-device focus, MQTT only non-SSL in 2.x, and
the Zigbee integration needs rework for Sonoff 4.x firmware.

## 2. seaky NSPanel Pro Roottool

`seaky/nspanel_pro_roottool_apk` · APK · closed source

A single-purpose tool: re-installs **official Sonoff OTA zips** on a rooted
panel (developer mode blocks official updates otherwise). It pushes the zip to
`/storage/emulated/0/system_update.zip` and calls the same updater the stock
app uses. Indispensable for anyone who wants to move between firmware
versions after rooting.

## 3. Fully Kiosk Browser

`de.ozerov.fully` · APK · **commercial** (freemium)

The most common kiosk layer stacked on top of Pro Tools. What it does:

- Renders a web dashboard (usually Home Assistant) full screen, kiosk-locked.
- **Motion detection** via the front camera (wake/keep awake), face detection,
  screensaver with photo/clock, screen-off scheduling.
- **Remote Admin**: a web UI on the device plus a **REST API** and
  **MQTT** bridge — Home Assistant has a first-party
  [`fully_kiosk` integration](https://www.home-assistant.io/integrations/fully_kiosk/)
  that exposes battery, brightness, screen state, volume, TTS, media, and
  remote commands.
- A JavaScript interface so dashboards can trigger device functions.

Strengths: mature, huge feature set, first-party HA integration.
Weaknesses: closed source, paywalled "PLUS" features, and it is a browser — on
the PX30 the WebView is the main cost (see the Flutter app below).

## 4. Kiosk Satellite

`jxlarrea/kiosk-satellite` · Flutter · free for personal use, **CC BY-NC-ND 4.0**

A dedicated Home Assistant kiosk with a very broad feature set:

| Area | What it does |
|---|---|
| Voice | built-in **Assist voice satellite** — no HA integration to install; wake-word works screen-off or in background |
| Dashboard | WebView-based, with optimizations that pause rendering during screensavers to cut CPU/GPU |
| Music | Music Assistant sync (Sendspin), AirPlay receiver, DLNA, Now Playing UI, music library |
| Screensaver | Immich albums, local photos, clocks, weather mood, live night sky |
| Detection | on-device motion/face/person detection via camera or built-in sensor; publishes as HA sensors |
| HA link | built-in ESPHome connection exposes screen/volume/sensors; optional Bluetooth proxy |
| Remote | web-based remote admin (screenshots, logs, config), **fleet management** |
| Extras | intercom (voice/video between kiosks, auto-discovery), alarms with sunrise, calendar views, camera views (12 live feeds, Go2RTC import), PIN kiosk mode, gestures (hand/finger counts → HA actions), RTSP, Shizuku support, plugin manager |

Notable: it documents the **NSPanel Pro proximity quirk** (inverted sensor
behaviour depending on firmware) — useful knowledge for any tool reading the
sensor.

## 5. Panel Assistant (ha-paneld + HA integration)

`panel-assistant/android` + `panel-assistant/ha-integration` · **Apache-2.0, fully open source**

The most infrastructure-minded project. Two halves:

- **ha-paneld** — the Android app: runs your dashboards, exposes the panel's
  screen/LEDs/buttons/relays/sensors as HA entities, and acts as an
  **Assist voice satellite** on panels with a microphone.
- **The HA integration** — installs, updates and *repairs* the app **from
  Home Assistant**: add a panel in Settings, get an update entity, get
  Repairs that name the panel and what to do. Installs over the network
  (ADB) or over **USB from the browser** — no command line on the user's PC.

Key ideas worth borrowing:

- **Entity filter**: it learns which entities your dashboard actually uses and
  asks HA to stream only those — that is where most of its "feels fast"
  reputation comes from.
- **Idempotent installs**: it checks what already happened on the panel before
  changing anything, and resumes after interruption.
- **Hardware profiles**: YAML profiles per panel family, so the same
  integration drives NSPanel Pro, Shelly Wall Display, Tuya TPA10, Electron,
  Smatek, Meta — with bounded privileged operations in a root helper.
- Backs up the panel's settings before every update.

## 6. Ava Pro

`knoop7/Ava` · native C++/Kotlin · **CC BY-NC-ND 4.0**

A "do-everything-in-one-app" Android companion for HA, aimed at weak hardware:

- Native C++ voice engine: custom wake words, wake-while-music, on-device
  voiceprint recognition, ambient-sound detection (glass break, baby cry…).
- ESPHome **BLE proxy** (replaces an ESP32 Bluetooth proxy).
- Room-to-room intercom, Sendspin multi-room audio.
- Floating overlays above any app (clock, weather, quick entities) instead of
  owning the screen — so it can sit *under* another launcher.
- Camera snapshot/motion as HA sensors, sensors (light, magnetic, proximity…),
  UDP LAN fleet management with ADB.
- **Mods**: dynamically loaded plugins (DLNA renderer, AirPlay receiver,
  Edge TTS, offline STT, Zigbee gateway, biometrics, RTSP…) without touching
  the core.

Architecturally interesting for its **module decoupling** — every subsystem can
be disabled individually, and plugins install at runtime.

## 7. NSPanel app (Flutter)

`mennovanhout/nspanelpro-flutter` + `mennovanhout/NSPanel-cards` · **MIT**

Built specifically for the NSPanel Pro 86 because the WebView was the
bottleneck. It renders a set of `custom:nspanel-*` Lovelace cards **natively**
(Flutter → GPU) instead of running HA's web frontend:

- Reads your Lovelace dashboard over the HA websocket — you keep configuring
  in HA, the panel follows, and edits hot-reload.
- 13 card types (light, switch, climate, sensors, weather, alarm keypad,
  camera…), no browser in the loop.
- **Owns the display**: native screensaver (photo + wandering clock), sleep
  (real backlight off via its own ADB daemon on 127.0.0.1:5555), wake on
  proximity with a *learned resting level* (handles the sensor's
  unit-dependent inverted behaviour and display-light contamination),
  auto-brightness from the light sensor, text scaling.
- **Self-updating**: uses the panel's own ADB endpoint to `pm install` a new
  APK from GitHub Releases, and shows an **update entity** in HA.
- Registers itself as an HA device over **MQTT discovery** with a rich entity
  set (proximity, presence, illuminance, screensaver, page, brightness, volume,
  notify/announce with built-in generated sounds, diagnostics).
- Provisioning without a keyboard: push a `setup.json` over ADB.

A very good reference for how to handle the proximity sensor correctly.

## 8. PanelDash

`mcomisso/Panelist` · Jetpack Compose, Kotlin · **MIT**

A "small native dashboard, one YAML file" approach:

- Renders large status tiles on a 480×480 screen with bottom navigation.
- YAML elements: stacks, grids, labels, buttons, toggles, progress bars.
- Streams state over HA's **WebSocket API** (~100 ms pushes), REST for actions,
  long-lived-token auth, optimistic state flip after taps.
- Config hot-reload in ~2 s from `/sdcard/paneldash/config.yaml` — edit the
  YAML, the panel follows, no reinstall.
- Automatic day/night theme from the ambient light sensor, with sun-time
  fallback.
- Kiosk behaviour, idle return to the main page, camera tiles, a "health page"
  that only lights up when something needs attention, doorbell chime.
- Boots straight into the dashboard (default home).

## 9. WallPanel

`thanksmister/wallpanel-android` · generic Android dashboard app

The older, generic option: a WebView kiosk with MQTT/HTTP remote control
(url, brightness, wake), camera streaming (MJPEG), motion/face detection,
screensaver, TTS announcements, sensor reporting (light, temp, battery).
Runs on old Android versions. Largely superseded by the above for NSPanel Pro.

## 10. Home Assistant Companion (official)

`io.homeassistant.companion.android` · official app

Runs the full HA web frontend in a WebView plus native helpers (notifications,
assist, sensors). On the PX30 it is heavy — the community repeatedly reports it
as slow/laggy on the NSPanel Pro, which is why the lighter, panel-specific apps
above exist.

## 11. RapierXbox/walldisplay-dashboards

HA integration that **auto-generates** a dashboard per display, so panels of
different sizes get a correctly sized dashboard without hand-crafting
Lovelace. Complementary to the apps above.

---

## Comparison

| | Pro Tools | Fully Kiosk | Kiosk Satellite | Panel Assistant | Ava Pro | NSPanel app | PanelDash |
|---|---|---|---|---|---|---|---|
| Licence | closed | closed (freemium) | CC BY-NC-ND | **Apache-2.0** | CC BY-NC-ND | **MIT** | **MIT** |
| Renderer | background service | WebView | WebView (optimized) | own renderer + WebView | native overlays | **native (Flutter)** | **native (Compose)** |
| Voice satellite | – | – | ✅ built-in | ✅ | ✅ (C++) | via HA TTS | – |
| Bluetooth proxy | – | – | ✅ optional | – | ✅ | – | – |
| Music / media | – | ✅ | ✅ Sendspin/AirPlay/DLNA | – | ✅ Sendspin | announcements only | chime only |
| Zigbee coordinator/router | ✅ | – | – | – | ✅ (mod) | – | – |
| MQTT config + commands | ✅ | ✅ (REST+MQTT) | ✅ | ✅ (via HA) | ✅ (ESPHome/MQTT) | ✅ discovery | ✅ WebSocket |
| Proximity wake | ✅ | camera | ✅ camera + person | ✅ | ✅ | ✅ (learned level) | ✅ |
| Light-based brightness | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ auto | ✅ day/night theme |
| Fleet management | – | ✅ (remote admin) | ✅ | ✅ (from HA) | ✅ (UDP+ADB) | ✅ (HA update entity) | – (manual deploy) |
| Install/update from HA | – | via integration | – | ✅ (install + update entity + repairs) | ✅ update entity | ✅ update entity | – |
| Open source | ❌ | ❌ | ❌ (free) | ✅ | ❌ (free) | ✅ | ✅ |

## Gaps none of them fully close

1. **Panel firmware-level control.** All of these are *apps*. None of them can
   remove Sonoff's bloat, disable the `frp` backdoor, or keep a verified
   firmware image — that is what Nexus's firmware tooling does.
2. **Verified, reproducible firmware images** with md5/sha256-checked dumps
   and rollback.
3. **An OTA channel independent of Sonoff's signing key.**
4. **Cross-tool orchestration.** Pro Tools, Fully Kiosk, Kiosk Satellite and
   ha-paneld each have their own remote-admin surface; nothing unifies them.
5. **Proximity sensor correctness** is solved well only by the Flutter app
   (learned resting level, display-light contamination, firmware-dependent
   inversion) — worth adopting elsewhere.

## What Nexus should take from this

- From **Pro Tools**: MQTT events + commands + remote config, wake/brightness/
  sleep, Zigbee package management.
- From **Panel Assistant**: idempotent installs, hardware profiles, "install
  and update from HA", backups before every change.
- From **Kiosk Satellite / Ava**: fleet management, modules that can be
  disabled individually, Bluetooth proxy / voice satellite as optional
  components.
- From **NSPanel app**: the correct proximity-sensor handling and native
  rendering instead of a WebView.
- From **PanelDash**: config hot-reload from a pushed file.
- And the thing none of them do: **firmware + OTA + debloat**, which is
  Nexus's unique layer.
