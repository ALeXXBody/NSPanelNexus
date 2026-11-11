# MQTT command reference (seaky Pro Tools protocol)

Topic base: `<prefix>/<device_name>` (default `nspanelpro/<name>`).

## Device commands — `<base>/command/device`

Payload: `{"command": "<cmd>"}`

| Command | Effect |
|---|---|
| `wake_up` | Wake the panel |
| `sleep` | Sleep immediately |
| `reboot` | Reboot (send 3×) |
| `launcher` | Switch to launcher |
| `default_app` | Switch to default app |
| `adb` | Enable on-device ADB |

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

All messages carry a `seq` sequence field (seaky v2.3.3+).
