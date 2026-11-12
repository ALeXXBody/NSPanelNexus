# NSPanel Nexus — Master Ledger (Guide & Rule Book)

> **RULE: Before starting ANY development, modification, or removal task,
> write the request in this ledger first, then begin work.**
> If this ledger is not respected, the repository will be audited 100 times.

---

## 1. What is NSPanel Nexus?

Two halves that **coexist and complete each other**:

- **Nexus firmware** — the clean Android 8.1 image + the on-device app that runs **on the panel**.
  Handles: MQTT, sensors, display, voice, Zigbee, remote desktop, OTA, fleet discovery.
- **Nexus app** — the Home Assistant integration that runs **in HA**.
  Provides: sidebar manager, entities, services, config flow, auto-discovery.

They communicate over MQTT. The firmware publishes state; the app consumes it and exposes HA entities.
The app can also send commands back to the firmware.

---

## 2. Ledger — What the user wants/needs

### 2.1 Nexus firmware (Android app on panel)

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| F1 | Clean Android 8.1 firmware (debloated) | ✅ Done | KISS launcher, Sonoff bloat removed, frp backdoor removed |
| F2 | MQTT client (publish events, accept commands) | ✅ Done | Pro-Tools-compatible protocol |
| F3 | Sensors (proximity + light) | ✅ Done | Learned baseline, light presets |
| F4 | Display (wake/sleep, brightness, schedules) | ✅ Done | Screen-on schedules, wake-on-proximity |
| F5 | Dashboard (HA dashboard in WebView) | ✅ Done | URL hot-swappable over MQTT |
| F6 | Voice satellite (Assist) | ⚠️ Phase 1 | Push-to-talk; wake word is phase 2 |
| F7 | Zigbee (coordinator/router) | ✅ Done | Role, permit-join, device list, factory reset |
| F8 | Firmware OTA | ✅ Done | Manifest + sha256, verified dump + rollback |
| F9 | Fleet (mDNS + UDP discovery) | ✅ Done | `_nspanel-nexus._tcp.` + UDP 5000 |
| F10 | Remote desktop (browser view + control) | ✅ Done | Token-authenticated HTTP on port 8080 |
| F11 | Auto-discover firmware version | ✅ Done | Reads `/data/nexus/version`, probes markers |
| F12 | Publish screen/brightness state to MQTT | ✅ Done | Retained, published after MQTT connects |
| F13 | Publish uptime to MQTT | ✅ Done | Every 60s, retained |
| F14 | Publish firmware version to MQTT | ✅ Done | On start, after 5s delay |
| F15 | Boot service (start on boot) | ✅ Done | Uses startForegroundService on Android O+ |
| F16 | All modules individually enable/disable | ✅ Done | From app UI and remotely over MQTT |
| F17 | No hardcoded IP addresses | ✅ Done | All IPs removed from codebase |

### 2.2 Nexus app (HA integration)

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| A1 | Panel auto-discovery (mDNS) | ✅ Done | manifest.json zeroconf entry (`.local.` suffix) |
| A2 | Panel auto-discovery (UDP) | ✅ Done | Broadcast on port 5000 |
| A3 | Sidebar manager panel | ✅ Done | 4 tabs: Panels/Firmware/Settings/About |
| A4 | Panel cards with live stats | ✅ Done | IP, lux, uptime, brightness, screen state |
| A5 | Add panel (config flow in-panel) | ✅ Done | mDNS + manual + UDP discovery |
| A6 | Remove panel (unlink) | ✅ Done | Deletes config entry |
| A7 | Panel controls (Wake/Sleep, brightness, RD, reboot) | ✅ Done | All buttons verified working |
| A8 | Firmware tab (version + check update) | ✅ Done | Calls firmware:check command |
| A9 | Settings tab | ✅ Done | Links to HA config entries |
| A10 | About tab | ✅ Done | Project info |
| A11 | Error handling on service calls | ✅ Done | .catch() with alert |
| A12 | Responsive layout | ✅ Done | Media queries for mobile/tablet |
| A13 | Proper branding (HA Marketplace) | ✅ Done | brand/ folder + repo root icon.png/logo.png |
| A14 | No hardcoded IP addresses | ✅ Done | All IPs removed |
| A15 | Config flow with topic_safe | ✅ Done | All flows apply topic_safe() |
| A16 | Options flow with auto-reload | ✅ Done | async_update_options reloads entry |
| A17 | Retained state messages | ✅ Done | HA receives last values on (re)subscribe |

### 2.3 Request — sidebar panel blank page (2026-11-12)

| # | Request | Status |
|---|---------|--------|
| P1 | Sidebar manager must never render blank; always show content or a readable error | ✅ Done (v0.4.26, browser-verified) |
| P2 | Use the modern panel load path (`module_url`), not the deprecated `js_url` | ✅ Done (v0.4.26) |
| P3 | Replace REST calls that 404 (`config/entity_registry/list`) with WebSocket API calls | ✅ Done (v0.4.26) |
| P4 | Version line read from the panel config, not hardcoded | ✅ Done (v0.4.26) |
| P5 | Verify in a real browser (Playwright + Chromium) | ✅ Done (rendered, 2 cards, no 4xx) |

**Evidence:** `GET /api/config/entity_registry/list` returns 404 (WS-only
endpoint); the panel swallows it. `js_url` is the legacy load path and is
deprecated in current HA frontend. A run showed
`<nspanel-nexus-manager>` present but empty.

### 2.4 Cross-cutting (both halves)

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| C1 | MQTT topic consistency | ✅ Done | HA uses topic_safe(device_name), Android uses topicId |
| C2 | Entity ID consistency | ✅ Done | All 18 entity IDs verified against HA registry |
| C3 | State publishing consistency | ✅ Done | Android publishes, HA subscribes, payloads match |
| C4 | Command flow consistency | ✅ Done | HA sends commands, Android handles them |
| C5 | Config update flow | ✅ Done | HA sends patches, Android applies them |
| C6 | Firmware auto-discovery | ✅ Done | App reads /data/nexus/version, publishes to MQTT |
| C7 | Panel-to-HA discovery | ✅ Done | mDNS + UDP, no hardcoded IPs |

---

## 3. Rules for development

1. **Write in this ledger FIRST** before starting any task
2. **Verify every change** — don't just code and hope
3. **Test end-to-end** — check that both halves work together
4. **No hardcoded IPs** — ever
5. **No quick dirty fixes** — do it properly
6. **Verify entity IDs** against the actual HA entity registry
7. **Verify MQTT topics** match between Android and HA
8. **Verify state payloads** match between publisher and subscriber
9. **Verify command handlers** are registered and match
10. **Bump version** and create release after every change

---

## 4. Audit history

| Pass | Date | Issues found | Issues fixed |
|------|------|-------------|-------------|
| 1 | 2026-11-11 | Entity ID mismatches (remote_desktop, light) | v0.4.10-v0.4.11 |
| 2 | 2026-11-11 | Brightness slider onclick, 700ms delay | v0.4.6 |
| 3 | 2026-11-11 | Uptime topic mismatch, firmware version | v0.4.5, v0.4.15 |
| 4 | 2026-11-11 | Zeroconf manifest missing, .local. suffix | v0.4.13, v0.4.18 |
| 5 | 2026-11-11 | Boot crash, options flow, topic_safe | v0.4.16, v0.4.14, v0.4.21 |
| 6 | 2026-11-11 | Display state not published, not retained | v0.4.19, v0.4.20 |
| 7 | 2026-11-11 | Blank page, constructor not initialized | v0.4.23, v0.4.24 |
| 8 | 2026-11-12 | (re-verification pass) | — |
| 9 | 2026-11-12 | (re-verification pass) | — |
| 10 | 2026-11-12 | `js_url` legacy load path, REST `config/entity_registry/list` 404, hardcoded version, blank before `hass` arrives | v0.4.26 |

---

## 5. Pending / future work

### Phase 2 (from PLAN.md)
- [ ] Wake word on-device (openWakeWord / microWakeWord)
- [ ] Native dashboard renderer (no WebView)
- [ ] Bluetooth proxy (ESPHome)
- [ ] Install/update from Home Assistant

### Phase 3 (from PLAN.md)
- [ ] Runtime mods/plugins (DLNA, AirPlay, RTSP, etc.)
- [ ] Dashboard provisioning (walldisplay-dashboards)

### Known issues to investigate
- [x] HA sidebar blank page — fixed in v0.4.26 (`module_url` + immediate skeleton + WS registry)
- [ ] DisplayModule publishes state with retry (hacky, should use MQTT connect callback)
- [ ] FirmwareModule reads version via su (should be readable by app)
- [ ] VoiceModule publishEntities() is no-op (phase 2)
- [ ] ZigbeeModule doesn't publish state to HA (local only)

---

## 6. Version history

| Version | Date | Key changes |
|---------|------|-------------|
| v0.1.0 | 2026-11-11 | Initial release |
| v0.2.0 | 2026-11-11 | Sidebar manager, audit fixes 1-18 |
| v0.3.0 | 2026-11-11 | Audit fixes 19-31 |
| v0.4.0 | 2026-11-11 | Sidebar add/remove panels |
| v0.4.1 | 2026-11-11 | Sidebar live stats + working buttons |
| v0.4.2 | 2026-11-11 | Bigger logo + version line |
| v0.4.3 | 2026-11-11 | Firmware auto-discovery |
| v0.4.4 | 2026-11-11 | Responsive layout |
| v0.4.5 | 2026-11-11 | Firmware version via su |
| v0.4.6 | 2026-11-11 | Button feedback + brightness oninput |
| v0.4.7 | 2026-11-11 | Error handling on service calls |
| v0.4.8 | 2026-11-11 | Fix entity suffixes + debug logging |
| v0.4.9 | 2026-11-11 | Fix light/remote_desktop suffixes |
| v0.4.10 | 2026-11-11 | Fix light event suffix |
| v0.4.11 | 2026-11-11 | Fix remote desktop entity ID |
| v0.4.12 | 2026-11-11 | Full audit, all entity IDs verified |
| v0.4.13 | 2026-11-11 | Zeroconf manifest entry |
| v0.4.14 | 2026-11-11 | Options flow + auto-reload |
| v0.4.15 | 2026-11-11 | Uptime publishing fix |
| v0.4.16 | 2026-11-11 | Boot crash fix (startForegroundService) |
| v0.4.17 | 2026-11-11 | Full audit pass 2 |
| v0.4.18 | 2026-11-11 | Zeroconf .local. fix |
| v0.4.19 | 2026-11-11 | Display state publishing fix |
| v0.4.20 | 2026-11-11 | Retained state messages |
| v0.4.21 | 2026-11-11 | topic_safe in all config flows |
| v0.4.22 | 2026-11-12 | Render error handling |
| v0.4.23 | 2026-11-12 | Render error visibility |
| v0.4.24 | 2026-11-12 | Constructor init fix |
| v0.4.25 | 2026-11-12 | Separate display name from topic-safe name in sidebar |
| v0.4.26 | 2026-11-12 | `module_url` load path, WS entity registry, dynamic version, never-blank skeleton |

---

## 7. How to use this ledger

1. **Before any task**: Write the request in section 2 (or a new subsection)
2. **During work**: Update status as you go (building → done)
3. **After work**: Verify end-to-end, update version, create release
4. **Rules**: Follow section 3 — no exceptions
