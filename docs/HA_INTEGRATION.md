# Nexus app — Home Assistant integration

The **Nexus app** is the Home Assistant side of the system. It provides the
sidebar manager, entities, and services. The panel itself runs the
**Nexus firmware** (the clean Android image + on-device app).

The two coexist and complete each other: the firmware runs on the panel,
the app runs in HA, and they communicate over MQTT.

## What you get

Once the panel is added (Settings → Devices & services → Add integration →
NSPanel Nexus), HA creates one device with these entities:

| Entity | Type | What it does |
|---|---|---|
| Touch | event | tap / swipe_up / swipe_down / swipe_left / swipe_right |
| Proximity | event | triggered |
| Light event | event | light_above / light_below / light_normal |
| Presence | binary_sensor | on while someone is at the panel |
| Illuminance | sensor | lux from the panel's light sensor |
| IP address | sensor | diagnostic |
| Uptime | sensor | diagnostic |
| Firmware | sensor | current Nexus version |
| **Screen** | switch | wake / sleep |
| **Brightness** | number (slider) | 0–255 |
| **Remote desktop** | switch | turn the browser remote desktop on/off |
| Voice satellite | switch | enable/disable the Assist satellite |
| Zigbee | switch | enable/disable the Zigbee module |
| Wake / Sleep / Reboot | button | device commands |
| Check firmware / Re-read config | button | diagnostics |
| Kiosk mode | sensor | dashboard / firmware / pin / login / screensaver / camera (diagnostic) |
| Screensaver | sensor | off / clock / blank / dim (diagnostic) |
| Day/night | sensor | day / night + applied brightness (diagnostic) |
| Wi-Fi signal | sensor | RSSI in dBm (diagnostic) |
| Storage free | sensor | free % (diagnostic) |
| RAM free | sensor | free % (diagnostic) |
| Zigbee permit join | sensor | seconds remaining open (diagnostic) |
| **Nexus app** | update | installed vs latest APK, Install runs `app:update` |
| **System image** | update | installed vs latest system image, Install runs `firmware:install` |
| Notification | notify | `notify.<panel>` shows a toast on the panel |
| MQTT host/user/password, topic prefix, HA URL/token, dashboard URL, remote-desktop token, manifest URL, Zigbee topic base, screen schedules, night window | text | full firmware configuration (passwords masked) |
| MQTT port, remote-desktop port, light thresholds, proximity delta, brightness presets, day/night brightness, screensaver timeout/brightness, auto-return, dashboard reload | number | full firmware configuration |
| Zigbee role, screensaver mode | select | coordinator / router; clock / blank / dim |
| Screensaver on/off | switch | enable the kiosk screensaver (config) |
| Sensors, Display, Dashboard, Firmware, Fleet (+ remote desktop, voice, Zigbee) | switch | enable/disable each module (no MQTT switch: it would silence the panel) |

Plus services:

| Service | What it does |
|---|---|
| `nspanel_nexus.command` | send any device command (`{"device_name": "…", "command": "wake_up"}`) |
| `nspanel_nexus.config_update` | patch any setting (`{"device_name": "…", "config": {"brightness": 180}}`) |
| `nspanel_nexus.speak` | TTS text on the panel speaker |
| `nspanel_nexus.toast` | short toast message on the panel screen |
| `nspanel_nexus.show_camera` / `hide_camera` | doorbell/camera full-screen popup (url, title, duration) |
| `nspanel_nexus.navigate` | open a path or full URL in the panel dashboard |
| `nspanel_nexus.refresh_dashboard` | reload the panel's dashboard WebView |
| `nspanel_nexus.show_screensaver` / `hide_screensaver` | force the screensaver on/off (optional mode) |
| `nspanel_nexus.get_config` | return the panel's current settings (secrets appear as `"<set>"`) |
| `nspanel_nexus.restore_config` | apply a saved config object (keys set to `"<set>"` are skipped) |

The sidebar Info popup shows kiosk / screensaver / day-night / Wi-Fi /
storage / RAM / Zigbee rows plus a Speak box and Refresh-dashboard /
Show-screensaver buttons; the Settings popup has a **Kiosk behaviour**
accordion (screensaver on/off + mode + timeout/brightness, auto-return,
dashboard reload, day/night brightness + night window).

Live screen: the sidebar card and Remote popup no longer talk to the panel
directly (browsers block plain-http streams inside an https page). Home
Assistant proxies the stream and the tap/swipe/key input through same-origin
`/api/nspanel_nexus/rd/<entry_id>/…` URLs (short-lived signed URLs for the
image, HA-authenticated POSTs for input), so the live screen works over http
and https, on the LAN and remotely. The panel RD token/port are persisted in
the config entry options whenever they are saved from Settings (or any
`config_update` call), so the proxy survives HA restarts. If the stream says
the token is not saved yet, set it once in Settings → Remote desktop → Save RD.

The sidebar's **Settings tab** is a full configuration form: Load from panel,
edit, Save to panel (one update).

## Requirements

- The panel runs the Nexus app with the **MQTT module on**
- MQTT broker reachable from both HA and the panel (the panel's own broker
  works for a single panel; a shared broker is better for HA discovery)

## Install

**Marketplace (HA 2026.11+):** Settings → Marketplace → Add from link →
`https://github.com/ALeXXBody/NSPanelNexus`, install **NSPanel Nexus**, restart HA.
On older HA versions use HACS (custom repository, category Integration).

**Manual:** copy `custom_components/nspanel_nexus/` into your HA
`config/custom_components/` folder and restart.

## Add a panel

1. Settings → Devices & services → Add integration → **NSPanel Nexus**
2. The integration asks the LAN for panels (the panel's Fleet module answers
   on UDP 5000) and lists them
3. Enter the panel name (as the Nexus app shows it) and the topic prefix
   (default `nspanelpro`)

## How it talks to the panel

Everything goes over the panel's own MQTT protocol:

```
nspanelpro/<panel>/command/device   {"command": "wake_up"}
nspanelpro/<panel>/config/update    {"module.remote_desktop.enabled": true,
                                     "brightness": 180}
nspanelpro/<panel>/event/touch      {"event_type": "swipe_up"}
nspanelpro/<panel>/state/brightness {"brightness": 180}
nspanelpro/<panel>/status           online | offline
```
