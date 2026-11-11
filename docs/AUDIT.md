# NSPanel Nexus — Full audit (integration + app + scripts)

Audited against a **live HA instance** (real browser, real screenshots) and the
installed panel app. Findings and their status:

## Fixed — Home Assistant integration

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | Critical | `PLATFORMS` missing from `const.py` → import failed, "did not return boolean… Disabling component" | defined |
| 2 | Critical | `async_setup` dropped `return True` → component disabled | restored |
| 3 | High | `panel_custom.async_register_panel` not awaited (coroutine never awaited) | awaited |
| 4 | High | `frontend.async_register_extra_module_url` no longer exists in current HA → setup crash | `StaticPathConfig` + `add_extra_js_url` + `webcomponent_name` |
| 5 | High | Panel script loaded **twice** → double `customElements.define` throws, blank page | single load via `panel_custom` |
| 6 | High | `_render` crashed on `entry.data` undefined (REST strips data) | websocket `config_entries/get` + unique_id reconstruction fallback |
| 7 | High | `entity_category` passed as string → entities failed to add | `EntityCategory` enum |
| 8 | Medium | Uptime sensor used `TIMESTAMP` device class with a raw number → add failed | plain sensor |
| 9 | Medium | Presence interval callback signature → error every minute | `async_track_time_interval(now)` |
| 10 | Medium | Entity-map fallback used the wrong entity_id prefix | deterministic `nspanel_<dev>_<obj>` ids |
| 11 | Medium | `_refresh` hit the config-entries API on **every** hass update (API spam) | throttled to 5 s, forced after actions |
| 12 | Low | `hass.states` is a Proxy — `Object.keys` fails | direct candidate lookups |
| 13 | Low | Blank page on any error | skeleton render + in-panel error surfacing |
| 14 | Low | mDNS discovery missing | `_nspanel-nexus._tcp` advertised; HA config flow auto-discovery |

## Fixed — Android app

| # | Severity | Finding | Fix |
|---|---|---|---|
| 15 | **Critical (security)** | Remote-desktop HTTP server had **no authentication** — anyone on the LAN could view the screen and inject input | token auth (server refuses to start without one; all requests require it; web UI prompts once) |
| 16 | High | Sonoff `frp` remote-access backdoor started at boot | removed in clean firmware build |
| 17 | Medium | OTA updater could run concurrent `dd` writes | `synchronized` lock around download+flash |
| 18 | Low | Dead `ThreadLocal` in VoiceModule | removed |

## Verified working (live test)

- Integration: setup, entry setup, **18 entities created**, live states
  (`illuminance 430`, `screen on`, `ip_address`, `uptime`…), MQTT command
  round-trip, config query/update with **secrets masked**
- Sidebar manager: two panel cards, green/gray status bars, live brightness,
  wake/sleep, remote desktop toggle, reboot, firmware tab
- Clean firmware on the panel: boots to KISS launcher, Zigbee stack intact,
  `/system` 76 % (was 100 %), no Sonoff processes

## Known limitations (by design, documented)

- Panel OTA cannot use Sonoff's recovery (their signing key) — Nexus has its
  own verified OTA channel instead
- Voice satellite is push-to-talk in phase 1; wake word is phase 2
- Dashboard rendering is WebView in phase 1; native renderer phase 2
- `WRITE_SETTINGS` needs a one-time grant (`adb shell appops set io.nexus.panel
  WRITE_SETTINGS allow`) for brightness control

## Security posture

- Remote desktop: **token required** (server disabled without one)
- MQTT config answers: secrets masked (`<set>`)
- OTA: sha256-verified + automatic backup before write
- Sidebar panel: admin-only
- No cloud, no telemetry
