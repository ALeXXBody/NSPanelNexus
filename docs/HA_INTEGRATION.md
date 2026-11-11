# NSPanel Nexus — Home Assistant integration

Controls an NSPanel Pro running the **Nexus firmware / Nexus app** from Home
Assistant.

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

Plus a **service** `nspanel_nexus.command` to send any device command
(`{"device_name": "nspanelpro", "command": "wake_up"}`).

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
