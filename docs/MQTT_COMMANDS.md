# MQTT command reference (Nexus firmware)

Topic base: `<prefix>/<device_name>` (default `nspanelpro/<name>`).

## Device commands — `<base>/command/device`

Payload: `{"command": "<cmd>", ...extra fields}`

| Command | Extra fields | Effect |
|---|---|---|
| `wake_up` | — | Wake the panel |
| `sleep` | — | Sleep immediately |
| `reboot` | — | Reboot (send 3×) |
| `launcher` | — | Switch to launcher |
| `default_app` | — | Switch to default app |
| `adb` | — | Enable on-device ADB |
| `tts:speak` | `text` | Speak text on the panel speaker |
| `notify:toast` | `message` | Show a toast on the panel screen |
| `camera:show` | `url`, `title`, `duration` (s) | Doorbell/camera full-screen popup |
| `camera:hide` | — | Close the camera popup |
| `dashboard:navigate` | `path` | Open a path (`lovelace/cameras`) or full URL |
| `dashboard:refresh` | — | Reload the dashboard WebView |
| `dashboard:return` | — | Return to the dashboard |
| `dashboard:url` | `url` | Set + load a new dashboard URL |
| `screensaver:show` | `mode` (clock/blank/dim, optional) | Force the screensaver on |
| `screensaver:hide` | — | Dismiss the screensaver |
| `firmware:check` | — | Check the system-image manifest |
| `firmware:install` | — | Install a downloaded system image |
| `app:check` | — | Check the latest Nexus app (APK) on GitHub |
| `app:update` | — | Download + install the latest Nexus app |
| `update:check` | — | Check both channels at once |
| `zigbee:permit_join` | `seconds` | Open the Zigbee network for joining |

## Switches — `<base>/command/switch_1` (and `switch_2`)

Payload: `{"state": "on"|"off"}`

## Config query — `<base>/config/query`

- Empty payload → return all config
- `{"brightness": null}` → return only brightness

Answers arrive on `<base>/config`.

## Config update — `<base>/config/update`

Payload: `{"<key>": <value>}` e.g. `{"brightness": 80, "hostname": "panel1"}`.

## Events published by the panel (selectable)

- `event/proximity` → `{"event_type": "triggered"}`
- `event/touch` → tap/swipe_up/down/left/right (screen off only)
- `event/light` → light_above / light_below / light_normal

## State topics

- `status` → online/offline
- `state/started_at`, `state/scheduled_reboot_at`, `state/ip_address`
- `state/kiosk` → `{"kiosk": "dashboard|firmware|pin|login|screensaver|camera", "screensaver": "off|clock|blank|dim", "camera": false}`
- `state/screensaver` → `{"screensaver": "off|clock|blank|dim"}`
- `state/day_night` → `{"period": "day|night", "brightness": <applied>}`
- `state/wifi` → `{"rssi": -54, "ssid": "..."}`
- `state/storage` → `{"free": MB, "total": MB, "free_pct": %}`
- `state/ram` → `{"free": MB, "total": MB, "free_pct": %}`
- `state/zigbee` → `{"role", "permit_join": s, "device_count", "bridge", "gateway_running"}`
- `state/capabilities` → model, android/sdk, screen, zigbee/screensaver/camera_popup/ota flags
- `state/firmware` → `{"current", "app", "available", "app_available", "error?"}` (merged, retained)
