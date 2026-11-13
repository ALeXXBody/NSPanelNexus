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
| P1 | Sidebar manager must never render blank; always show content or a readable error | ✅ Done (v0.4.27, browser-verified) |
| P2 | Use the modern panel load path (`module_url`), not the deprecated `js_url` | ✅ Done (v0.4.27) |
| P3 | Replace REST calls that 404 (`config/entity_registry/list`) with WebSocket API calls | ✅ Done (v0.4.27) |
| P4 | Version line read from the panel config, not hardcoded | ✅ Done (v0.4.27) |
| P5 | Verify in a real browser (Playwright + Chromium) | ✅ Done (rendered, 2 cards, no 4xx) |
| P6 | A new release must never be masked by a stale browser cache: panel module URL must be cache-busted per version | ✅ Done (v0.4.31, stale-cache simulation verified) |

> **P6 evidence (2026-11-12, on the user's real HA 192.168.50.20, HA 2026.11.1 /
> frontend 20261028.5):** the panel rendered in a clean browser profile (element
> defined, shadow root 3679 chars, version v0.4.29) while the user still saw a
> blank page. The panel is loaded from `/nspanel-nexus/panel.js` via
> `module_url`, and that URL never changed between releases, so a browser that
> cached an older build keeps running it. Older builds read the entity registry
> over REST (`config/entity_registry/list` → 404 on HA 2026.11) and never
> rendered. Fix: append the manifest version to the module URL
> (`panel.js?v=<version>`), so each release is a guaranteed cache miss.
>
> **P6 verification (2026-11-12, exact stack HA 2026.11.1 + frontend
> 20261028.5):**
> - Panel config now reports `module_url=/nspanel-nexus/panel.js?v=v0.4.31`.
> - With an old v0.4.26 panel.js planted at the unversioned URL (served with
>   `max-age=2678400`), the browser never fetched the unversioned URL (route
>   hits 0) and rendered the current panel from the versioned URL on load and
>   reload: `<nspanel-nexus-manager>` defined, shadow root populated, no 4xx,
>   no console errors.

**Evidence:** `GET /api/config/entity_registry/list` returns 404 (WS-only
endpoint); the panel swallows it. `js_url` is the legacy load path and is
deprecated in current HA frontend. A run showed
`<nspanel-nexus-manager>` present but empty.

**Version correction:** the fix was first built as `0.4.26`, but the published
`v0.4.26` tag/release already exists (`6baf5dc`, "unlink + device name
extraction"). Rule 11 forbids reusing a version number, so this work ships as
**`0.4.27`** instead (manifest, hacs.json, panel fallback version and ledger
all aligned before the commit).

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

### 2.5 Full audit 2026-11-13 (user: "take over, full audit, app is a mess")

### 2.8 ImportError: UnitOfLuminance/UnitOfTime not on user's HA (user-supplied log)

User log proved entry setup died in `sensor.py` import — the unit enums don't
exist in `homeassistant.const` on their HA build. Fix: try/except import with
plain `"lx"`/`"s"` fallback (works on every HA version, old or new).
Lesson: never import version-fragile names unguarded; the venv here (2026.2.3)
is NOT the user's HA. Verified: ruff + py_compile clean.

### 2.8 Full firmware configuration from the Nexus app (user: done, released v0.4.39)

### 2.8 Full firmware configuration from the Nexus app (done, released v0.4.39)

Transport already exists (config_update service + Settings.patch); what's missing is UI + entities. Scope for this pass (verify each):
- [x] C1: HA `text` entities for all string settings (mqtt_host/user/pass, topic_prefix, ha_topic_prefix, ha_url, ha_token, dashboard_url, remote_desktop_token, nexus_manifest_url, zigbee_topic_base, 4× screen schedules); password mode for secrets; ignore `"<set>"` masked values on read
- [x] C2: HA `number` entities for ports/thresholds (mqtt_port, remote_desktop_port, light_below/above, proximity_delta, brightness_on_light_below/above); `select` for zigbee_role; module switches for sensors/display/dashboard/firmware/fleet (NOT mqtt — remote kill-switch)
- [x] C3: Sidebar Settings tab becomes a real form (Load from panel via query button, Save-all via single config_update, module toggles via switch entities)
- [x] C4: Android: mask remote_desktop_token in config answers; hot-apply dashboard_url + remote_desktop token/port changes; publish modules state (already done v0.4.35)
- [x] C5: verify (ruff/node/py/gradle), docs update, bump + release

### 2.20 HA dashboard error on the panel (user, 2026-11-13)

User reports the dashboard from HA shows an error on the panel even though the
dashboard URL is saved and "Test dashboard URL" reported "link ok" (HA answers 200
for any path, including the login shell, so that test proves nothing about the
dashboard itself). Need to capture the actual WebView error and report it
verbatim instead of a generic "Dashboard URL failed" toast.

- [x] H1: log every `onReceivedError` / `onReceivedHttpError` to logcat (tag `NexusDash`) with url + description + code (v0.4.55)
- [x] H2: diagnosed on the live panel — the "error" was the unauthenticated dashboard shell before OAuth; after in-app OAuth (v0.4.51) and the HA native dark theme (v0.4.56) the dashboard loads with no `NexusDash` errors (re-checked 2026-11-13 on 0.4.69)
- [x] H3: closed here; superseded by the kiosk work in 2.15/2.18/2.30/2.31

### 2.30 Info + Remote popups match Settings style (user, 2026-11-13)

User: make info and RD screen same style as settings. Done: info popup is now a
compact 3-col grid of uppercase micro-labels + values with a labeled brightness
slider and matching buttons; remote popup got micro-label captions (Keys, Type
text), primary Type button, muted log line, bordered stream frame and compact
density. Also deduplicated the info popup (the card button delegated to
_openInfoModal; fixes a latent bare `entry` reference in its Unlink handler).

- [x] U1: restyle info + remote popups to settings look
- [x] U2: dedupe info renderer into _openInfoModal
- [x] U3: verify (node --check + headless render of both popups, no errors) + bump + release

### 2.29 Logo-red accent + darker theme + always-visible settings (user, 2026-11-13)

User: (1) too many colours — drop the yellow/orange, use the colour from the logo
(#F14616 sampled from the brand mark), darker background; (2) settings must always
show values (except secrets). Implemented: single #F14616 accent everywhere
(buttons, chevrons, dirty/save indicators, links, tab, toggles), backgrounds
darkened one step across host/topbar/cards/modal/inputs; greens kept only for
on-state, red only for destructive. Values resolve entity -> config entry
(data/options incl. dark_mode, now persisted server-side) -> last-known
localStorage cache; cached values get a dashed border + dot + tooltip; passwords
never shown/cached. Dirty tracking + save compare against the same resolved
values, so edits on cached data save correctly.

- [x] T1: single #F14616 accent, darker surfaces
- [x] T2: knownOf()/knownVal() resolution chain + stale markers + cache
- [x] T3: backend persists dark_mode in entry options
- [x] T4: verify (ruff/node/py + headless render of all three sources + save patch) + bump + release

### 2.28 Real settings layout overhaul (user: cosmetics are not a redesign, 2026-11-13)

Proper restructure, not a paint job: accordion sections (collapsed advanced ones),
sticky save bar with dirty-count, no duplicated token/port fields (RD box owns them),
short labels at the source, denser grids, toggle chips for modules.

- [x] L1: accordion sections + sticky save footer + dirty tracking
- [x] L2: RD token/port live ONLY in the RD box (removed from generic lists)
- [x] L3: verify (node --check + headless render), bump + release

### 2.28 Version split: Nexus app vs system image, OTA channel repaired (user, 2026-11-13)

User: info popup showed "Firmware 1.0.0" with no Nexus version anywhere and no working
Nexus update path. Root causes found: (1) no UI ever showed the APK version —
the firmware sensor only carried the system-image version; (2) releases never
attached an APK so "Check for app update" could never find one; (3) the default
manifest URL 404s and the panel's configured manifest pointed at
releases/latest (an app release, no manifest) so system OTA was dead too.

- [x] V1: panel publishes app version in firmware state (start + check paths)
- [x] V2: HA firmware sensor exposes app_version/available_version/update_error attributes
- [x] V3: sidebar Info shows "Nexus app" + "System image" rows; Setup shows both
- [x] V4: repo firmware/manifest.json tracks latest system release; release script refreshes it
- [x] V5: attach APK to releases going forward; bump, build, release with APK, install on panel
- [x] V6: verify live (sensor attribute, sidebar rows, setup text, check-for-update no-op vs 1.0.0)

### 2.29 OTA end-to-end proven + fixes found by testing it (user follow-up, 2026-11-13)

Testing the real path exposed two blockers: (1) no release ever shipped an APK,
so there was nothing to download; (2) the installer intent used a bare file://
URI and the app lacked REQUEST_INSTALL_PACKAGES, so the system installer refused
it. Fixed with a FileProvider content URI + grant flag, the permission, and APK
assets attached to releases going forward. Panel self-updated 0.4.67 -> 0.4.68
entirely on its own (proven live).

- [x] Add REQUEST_INSTALL_PACKAGES + FileProvider (cache-path) + grant flag
- [x] Test release proved discovery+download+installer intent; then deleted
- [x] Real 0.4.67 release ships the APK; panel updated to 0.4.68 fully on its own (check -> download -> installer UI -> user taps INSTALL -> verified running 0.4.68, publishes app 0.4.68)
- [x] Update toasts: system-check reports up-to-date/failure on screen; app-check warns when a release has no APK (ships next release)

### 2.27 Modern compact futuristic settings page (user, 2026-11-13)

Redesign of the HA sidebar Settings popup: glassmorphism modal (gradient, blur,
orange accent glow), section headers with dividers, compact 2-column grid,
tighter spacing, uppercase micro-labels, gradient primary buttons, pill status
for RD, shorter hints, removed verbose text. Dark mode toggle added to the form.

- [x] S1: modernize CSS (glassmorphism, gradients, neon accents, compact inputs)
- [x] S2: restructure form into sections (Connection, Dashboard & firmware, Zigbee & screens, Ports & thresholds, Dark mode, Modules)
- [x] S3: shorten labels and hints, remove useless text
- [x] S4: verify (node --check), bump + release

### 2.25 Firmware update button checked panel firmware, not Nexus app (user, 2026-11-13)

User: the firmware update button was searching for the panel firmware, not for the
Nexus firmware/app. The FirmwareModule OTA (system image) stays for the panel OS,
but Setup now also has a "Check for app update" button that queries GitHub releases
for a newer Nexus APK and downloads + installs it via the system package installer.
The original "Check for firmware update" button is relabelled to make clear it
updates the panel system image.

- [ ] A1: add checkForAppUpdate() to FirmwareModule (GitHub releases API, tag compare, APK asset URL)
- [ ] A2: add "Check for app update" button to Setup (download APK to cache, launch ACTION_VIEW installer)
- [ ] A3: verify on the live panel (button reports "up to date" since 0.4.60 is latest) + docs + bump + release

### 2.26 Settings wiped after update (user, 2026-11-13)

User: after the last update, the panel's HA data (mqtt_host, ha_url, dashboard_url,
topic_prefix) was wiped from settings, leaving only the token. Root cause: the
sidebar "Save to panel" sent empty strings for text fields whose entities were
unavailable, and Settings.patch() blindly put them, overwriting stored values.
Fixed on both sides: sidebar no longer sends empty text values when the current
value is unknown/unavailable; Settings.patch() refuses to overwrite non-empty
connectivity fields with blank strings.

- [x] W1: Settings.patch() guards against blanking connectivity fields
- [x] W2: sidebar save() guards against sending empty text when current value unknown
- [x] W3: verify on the live panel (settings intact after save; no wipe), docs + bump + release

### 2.24 HA sidebar lost connection after v0.4.58 — root cause + fix (user, 2026-11-13)

User reported the HA sidebar lost connection with the panel after the last update.
Probed: MQTT status was `offline` (not a crash — the app was running, pid 1322).
The last log line before the silence was `publishDiscovery() done` with no
error, i.e. the broker simply dropped the retained `status=online` (or the
panel's will fired) at some point and nothing re-published it. Root cause:
`isCleanSession = true` + a will of "offline" + no periodic re-publish of
"online" means a single dropped connection (or broker restart) leaves the
panel silently offline forever until the app is restarted. Fixed: publish
"online" on every `connectComplete` (including reconnects), and add a 60s
keepalive watchdog that re-publishes "online" while connected.

- [x] M1: onConnected publishes status=online (retained, qos 1) on every connect/reconnect
- [x] M2: 60s watchdog re-publishes status=online while connected
- [x] M3: verify on the live panel (reboot → status goes online within ~10s and stays; HA sidebar shows online) + bump + release

### 2.23 In-app firmware OTA (user, 2026-11-13)

User: firmware updates are being pushed with `adb install` on my side, which is fine for
testing but not for production. Nexus firmware must update itself over the air. The
script `scripts/nexus-updater.sh` already exists on the panel side (manifest + sha256
+ backup + rollback). What is missing is the *app* side: a button in Nexus that
fetches the manifest, downloads the image, runs the updater, and reboots — plus HA
control over the same flow.

- [x] O1: Android: "Check for firmware update" / "Install update" in Setup +
      Diagnostics (fetch manifest, compare version, download with progress, sha256
      verify, invoke `nexus-updater.sh`, reboot); show status line
- [x] O2: HA: expose `nspanel_nexus.firmware_update` service (device_name, manifest_url
      optional) + a "Check firmware" button already wired in the sidebar
- [x] O3: verify on the live panel (point at a real manifest, dry-run download +
      sha256 check without writing) + docs update + bump + release

### 2.22 Inverted-colors "dark mode" is wrong (user, 2026-11-13)

The previous dark mode inverted the whole page with hue-rotate (looks like a photo
negative: images wrong, colors wrong, everything inverted). That is not a dark
theme — it is a hack. Remove it entirely. HA already supports dark themes natively
via the `selectedTheme` (or the `default_dark_theme`), so the panel should just
set that on login/injection instead of filtering the page. Keep the `dark_mode`
toggle as a user preference but have it drive the HA theme selection, not a CSS
filter.

- [x] I1: delete the invert filter + probe machinery; remove the Setup toggle's
      side effects (no recreate needed)
- [x] I2: inject the HA dark theme on login and on page load when dark_mode is on
      (use the standard `frontend/set_user_data` theme key, or pass ?theme=...);
      verify native dark renders (no inversion) live
- [x] I3: docs update, bump + release

### 2.21 Android system navigation bar visible over the kiosk (user, 2026-11-13)

User: the bottom navigation bar (back/home/recents) is still visible while HA is the
default state, so it sits on top of the dashboard and interferes with taps. It should
be hidden in kiosk mode and reappear only after the 6-tap exit (and PIN) reveals the
firmware pages.

- [x] V1: hide the system nav bar in dashboard mode (immersive sticky via
      setSystemUiVisibility flags, re-applied on window focus changes and in
      onWindowFocusChanged) and restore the default UI flags in firmware mode
- [x] V2: verify on the live panel (reboot → screenshot shows no nav bar; exit →
      nav bar back; Return to HA → hidden again), docs update, bump + release

### 2.19 Bottom nav always visible + app backgrounds after tap (user, 2026-11-13)

User: the bottom command bar (Setup/Modules/Diagnostics) is always visible and tapping
any of its buttons puts the Nexus firmware in the background. The nav bar should be
hidden by default and appear only after the 6-tap exit (and PIN) sequence — i.e.
the dashboard is the only thing the user normally sees.

- [x] B1: hide `pagesContainer` entirely while a dashboard is shown (no title bar, no nav bar, no pages)
- [x] B2: show it again only after the 6-tap exit (and PIN) succeeds
- [x] B3: re-test on the live panel (reboot, 6-tap exit, PIN, Return to HA) + docs/bump/release

### 2.18 Dark mode + auto-run at boot (user, 2026-11-13)

1. Dark mode: the native firmware UI is already dark (Theme.Nexus); what is
light is the dashboard WebView (HA follows `prefers-color-scheme` when its theme
is auto). Fix in firmware: force app night mode so the WebView reports dark,
behind a Setup toggle (`dark_mode`, default on; changing it recreates the
activity). No AppCompat needed (`updateConfiguration`, works on API 27).
2. Auto-run: BootReceiver already starts NexusService at boot, but never the
kiosk UI — after a reboot MQTT/RD work but no dashboard is shown. Fix: the
receiver also launches MainActivity (allowed on 8.1), plus QUICKBOOT_POWERON
for Rockchip ROMs.

- [x] N1: night-mode reporting + Setup toggle (+ recreate on change); WebView-local dark filter (body-targeted invert, media double-inverted) as the guaranteed mechanism
- [x] N2: BootReceiver launches MainActivity (NEW_TASK) + QUICKBOOT_POWERON action; MQTT connect retry with backoff (boot race fix, found live)
- [x] N3: verified (reboot → dashboard up + MQTT online with zero touches; dark/light both ways via flag; kiosk doc added), bump + release

### 2.17 Proper HA login in the app: OAuth, not trusted networks (user, 2026-11-13)

User rejected trusted_networks ("not correct/safe") — right: IP-based auth can be
borrowed by any device on that IP. The proper way (same as the official Companion
app): the panel logs in as an OAuth client once (username+password+MFA in-app),
stores the refresh token, mints access tokens itself, and injects the session into
the dashboard WebView via localStorage. Revocable per-device from the HA UI, no
passwords stored, works over http/https, LAN/remote.

- [x] A1: confirm mechanism against the user's HA: frontend `hassTokens` key/format, `/auth/authorize` + `/auth/token` behavior
- [x] A2: app: "Log in to Home Assistant" (OAuth authorize in a login WebView, custom-scheme code capture, code exchange, refresh token in Settings); dashboard WebView session injection (mint+inject+single reload per load); status line + logout/clear
- [x] A3: verify (gradle build, install; login page opens; bad-code path; user completes the one-time login with their own credentials), docs update, bump + release

### 2.16 Nexus replaces Fully Kiosk + NSPanel Pro Tools (user, 2026-11-13)

User: from my guidance, Nexus should replace (be better than) Fully Kiosk and the
sneaky tools — those are not needed anymore. Found both installed on the live panel
(`de.ozerov.fully`, `com.seaky.nspanelpro.tools`); the debloat script never removed
them (user apps, not system bloat) and INSTALL.md still tells users to install seaky.
Nexus firmware already covers it all: dashboard kiosk (WebView, default state),
MQTT control (own MqttModule), RD, display/voice/zigbee — no third-party app needed.

- [x] R1: uninstall both apps from the live panel; verify Nexus still fully alive (MQTT status online, RD stream 200, dashboard showing) via evidence, not assumption
- [x] R2: docs: FIRMWARE.md verification note (FK/Pro Tools "still running" → removed, replaced by Nexus), INSTALL.md "On-panel software" section (no longer "install seaky" for Nexus firmware), DashboardModule docstring ("Fully-Kiosk path" → native WebView kiosk)
- [x] R3: verify (ruff/node/gradle unaffected — docs + uninstall only; live evidence), bump + release

### 2.15 Dashboard kiosk: default state, 6-tap exit, Return button, RD text input (user, 2026-11-13)

User: dashboard must be the default state; exit via 6 quick taps into firmware;
a "Return to HA" button brings the dashboard back; (plus: panel showed the HA
*login* page — login once through RD, so the Remote popup needs a text field).
Verified on the live screenshot: after restart the panel shows the HA login page
(dashboard loads at start — 2.14 works). WebView session cookies persist the login.

- [x] K1: MainActivity: title bar becomes a row (title + "Return to HA" button, visible only when a dashboard is set and hidden); 6 quick taps anywhere while dashboard shown → exit to firmware (replaces double-tap-titlebar); toast hints
- [x] K2: sidebar Remote popup: text field + Type button (POST input/text, value encoded) so HA login can be typed from the browser
- [x] K3: verify (gradle build, install on live panel, E2E screenshot; headless JS for popup), docs update, bump + release
- [x] K4: optional exit PIN (6 taps → PIN pad → firmware; set in Settings; IME auto-hidden while pad is up so the keyboard never covers the keys)

### 2.14 Dashboard URL saved but panel stays on Setup (user, 2026-11-13)

Root cause (read the code): Setup → Save only restarts modules. The dashboard
WebView loads in `MainActivity.onCreate` and on MQTT `dashboard:url` / `dashboard:wake`
— never after a Setup save. So the panel keeps showing the firmware until the app
is killed. The Setup "Test" button only checks the raw HTTP code (HA answers 200
even for the login shell), which is why it said "link ok".
Fix: `save()` invokes `DashboardModule.onUrlChanged` with the new URL (module
instances persist, so the MainActivity wiring is intact) — dashboard appears instantly,
clearing the URL hides it instantly.

- [x] F1: Setup save() hot-applies dashboard_url via onUrlChanged (blank → hide)
- [x] F2: verify (gradle build, install on live panel via ADB, E2E screenshot shows dashboard), docs update, bump + release

### 2.13 Exact 502 cause (user reports the 502 message, 2026-11-13)

The 502 lumped three causes: token never saved into entry options (most likely —
the old browser-token prompt never reached the server; Save RD is required once),
panel IP unknown, panel refusing/unreachable. Split them server-side with distinct
reason strings and map each to an actionable UI message (incl. panel-answered-401
= wrong token saved).

- [x] E1: proxy returns distinct reasons (rd-token-missing / panel-ip-unknown / panel-unreachable@host:port / panel-answered-<status>); frontend reads error body and maps each
- [x] E2: verify (ruff/node + proxy unit tests extended for each reason + headless), docs update, bump + release

### 2.12 Still not working — proper diagnosis (user, 2026-11-13)

Probed the user's HA directly:
- `/nspanel-nexus/panel.js` serves **v0.4.47** with the proxy code → frontend is current
- `/api/nspanel_nexus/rd/…` answers **403** unsigned on both LAN-http and public-https → proxy view is registered and enforcing auth (404 would mean old code)

So backend+frontend are live; remaining suspects: (a) RD token never saved into entry options (Save RD required once after 0.4.47), (b) WS signer failing, (c) stream `<img>` destroyed+recreated on every 5s refresh (confirmed in `_refresh` → `_render` full rebuild) — even a working stream would restart constantly.

- [x] D1: persistent per-entry `<img>` node re-adopted across re-renders (no 5s stream restart); prune nodes for removed entries
- [x] D2: exact failure reasons in the UI: signer errors shown verbatim; img errors probed with fetch to report 502 (token/panel) vs 403 (stale signature) distinctly
- [x] D3: verify (ruff/node + headless: two renders share one live img node; proxy unit tests re-run), docs update, bump + release

### 2.11 Live RD stream must work over https (user: "make it work as it is now", 2026-11-13)

Root cause (verified by me): panel serves `http://192.168.50.22:8080/stream?token=…` fine (HTTP 200, valid MJPEG).
But the user opens HA via `https://ha.bit-service.co.uk` — browsers block the plain-http image (mixed content).
No token can fix that; HA must serve the stream itself (same-origin https).

- [x] P1: backend proxy view `GET/POST /api/nspanel_nexus/rd/<entry_id>/<path>` (requires_auth=False, HMAC-signed `?auth_sig=` via `async_sign_path`/`async_validate_signed_request`); forwards to panel with server-side token, streams bytes back
- [x] P2: WS command `nspanel_nexus/rd_url` {entry_id, path} → signed URL (10 min, bound to caller's refresh token)
- [x] P3: persist `remote_desktop_token`/`remote_desktop_port` into entry options on `config_update` (proxy reads them live; survives restart)
- [x] P4: panel.js card + Remote popup use signed same-origin URLs for stream AND input POSTs; drop browser-token/localStorage flow
- [x] P5: verify (ruff/node/py + proxy unit test vs fake panel + headless JS render), docs update, bump + release

### 2.10 Settings popup rework: RD on/off + token stay in Settings, wider + compact (user request, 2026-11-13)

User: "remote desktop should be activated/deactivated from Settings! and the token tab should stay there as well. you have to rework the Settings pop-up to be wider and more compact"

- [x] T1: dedicated Remote-desktop section pinned at TOP of Settings popup: on/off toggle (module_remote_desktop switch, applies immediately), token field (password, empty = unchanged) + port field, Save-RD-only button, Open-remote button when RD on
- [x] T2: token stays in Settings (both the quick RD-section token AND the full-form remote_desktop_token field); card no longer asks for token (points to Settings instead)
- [x] T3: wider + more compact: modal 900px already, add 2-column field grid, tighter paddings, RD box styling; single column under 700px
- [x] T4: verify (ruff + node --check + py_compile), bump + release
- [x] T5: OBSERVED via headless render — settings card squeezed into narrow left grid column (`.grid` auto-fill), fix: single-column full-width grid inside modal; re-render to confirm, then bump + release

### 2.9 Panels-first sidebar redesign (user: firmware/settings pages are useless, remove them)

New card layout per panel (Panels tab only):
- [ ] R1: card shows live remote-desktop image on top (MJPEG stream, token from browser localStorage, prompt-once UX like the RD web UI)
- [ ] R2: under the image: quick actions (kept) + **Info** and **Settings** buttons opening modal popups
- [ ] R3: Info popup = full live info (IP, lux, uptime, firmware + check-for-update, sensors, module states)
- [ ] R4: Settings popup = full config form (Load/Save-all) + module toggles (moved from removed Settings tab)
- [ ] R5: REMOVE Firmware and Settings top tabs (keep Panels + About); delete dead tab renderers
- [ ] R6: modal suspends background refresh while open (same pattern as add-flow); Esc/✕/overlay-click closes
- [ ] R7: verify (node --check), docs update, bump + release

### 2.7 Sidebar-first add flow + setup hardening (user: sidebar must show by default, add panel from there)

Requested logic: install → sidebar visible → add panel from the sidebar. Fixes:
- [x] S1: empty state keeps Panels tab with + Add panel button (no dead-end message; form survives refresh)
- [x] S2: migration + update-listener registration wrapped so setup can never fail because of them
- [x] S3: duplicate guard across unique_id schemes (old device-only vs new prefix/device) in user + zeroconf steps
- [x] S4: verify: ruff + node --check + py_compile; bump + release

### 2.6 Open-item repair batch (user: "please start working on them")

Remaining open items being repaired now (each verified, no dirty fixes):
- [x] O1: Touch event dead (entity exists, never published) → publish throttled tap from MainActivity dispatchTouchEvent
- [x] O2: Module switches optimistic-only (HA goes stale) → retained `state/modules` doc published by Android; switches subscribe + sync
- [x] O3: `unique_id` ignores prefix (same name/different prefix collide) → qualify new-entry unique_ids with prefix; existing entries untouched
- [x] O4: `device_info` identifiers ignore prefix (two prefixes merge into one HA device) → identifiers include prefix (entities move once, history kept)
- [x] O5: Zigbee permit_join via wrong broker → dedicated local-broker publish (127.0.0.1) with graceful fallback
- [x] O6: Firmware version via `su` on UI thread → updater chmods version file readable; app reads direct first, su fallback with timeout
- [x] O7: Release version-drift class → `scripts/bump_version.sh` single source (manifest/hacs/panel/Android)

Parallel audits from 4 angles (HA linkages / panel.js / Android / versions).
Fix list for this pass (verify each, no dirty fixes):
- [x] H1: `_link_for` normalize with topic_safe; PanelLink defence sanitize
- [x] H2: wire `add_update_listener` so options save reloads
- [x] H3: options defaults from merged data+options; zeroconf topic segment sanitize
- [x] H4: single `topic_safe()` in const.py
- [x] H5: sensor units (`LIGHT_LUX`->`UnitOfLuminance`, `s`->`UnitOfTime`), number int, DOMAIN const
- [x] P1: panel.js prefer `entry.options.device_name`; registry fallback entity map
- [x] P2: fallback `panel`->`nspanel`; debounce brightness + .catch; url guard; online default; suspend refresh while addForm; fix double-refresh; escape dev; select shape; remove dead `_token`+logs; drop dead OBJ_ID
- [x] F1: Android route ip/started/uptime via `state()` (retain); exact `command/device` match
- [x] F2: DisplayModule NPE guard; SetupFragment test current field; Firmware fallback try/catch; Voice Assist proto fix
- [x] V1: bump manifest/hacs/panel together; Android BuildConfig version; backfill LEDGER; untrack .gradle; ignore ha-brands

---

### 2.31 Ecosystem improvement program — "please do them all" (user, 2026-11-13)

User approved the full researched improvement program and asked for all of it:
kiosk completeness (screensaver, idle return, scheduled reload, day/night
brightness), remote control/presence (TTS, toasts, camera/doorbell popup,
dashboard navigate/refresh), voice, Zigbee depth (permit-join + device state),
fleet/ops (health sensors, config backup/restore, update entities), hardware
variants (capability flags) and reliability (MQTT hygiene, OTA rollback
safety). Shipped incrementally; each item is only checked when it compiles and
is verified on the live panel.

- [x] F1: screensaver engine (clock/blank/dim, idle timeout, tap-to-dismiss)
- [x] F2: idle auto-return to the dashboard from firmware pages
- [x] F3: scheduled dashboard reload (`dashboard_reload_minutes`)
- [x] F4: day/night brightness curve (window + two levels, manual override safe)
- [x] F5: TTS `speak`, `notify:toast`, camera/doorbell overlay (`camera:show`)
- [x] F6: dashboard `navigate` / `refresh` / `return` remote commands
- [x] F7: kiosk + screensaver state publishing (dashboard/firmware/pin/camera)
- [x] F8: health sensors (Wi-Fi RSSI, free storage %, free RAM %)
- [x] F9: Zigbee permit-join countdown + device-count state
- [x] F10: HA `update` entities (Nexus app + system image) with install action
- [x] F11: HA `notify` entity + services `speak`/`toast`/`show_camera`/`navigate`/`refresh_dashboard`/`show_screensaver`/`hide_screensaver`
- [x] F12: config backup/restore services (`get_config`, `restore_config`) with secrets masked
- [x] F13: panel capability flags published (`capabilities`) for hardware variants
- [x] F14: sidebar Info popup shows dashboard/screensaver/RSSI/storage + Speak/Refresh controls
- [x] F15: docs, version bump, release with signed APK, installed + verified on panel

**Verified live on the panel (2026-11-13, app 0.4.69 / system image 1.0.0):**
`state/kiosk` dashboard→screensaver→dashboard (forced + idle timeout + tap-to-dismiss),
dashboard→firmware→dashboard on `auto_return_seconds=10`, WebView reload at
`dashboard_reload_minutes=1`, `day_night` switching to `night` with
`brightness=80` inside a test window, `state/zigbee` published
(role coordinator, permit_join 0), `state/capabilities`, `state/wifi|storage|ram`,
TTS engine `eng_GBR` with `speak result=0`, toast shown, camera overlay state
`camera:true`→false, and `app_available=0.4.69` after the GitHub release.
Sidebar UI verified with the headless Chromium harness (Info rows + Kiosk
behaviour fields, no console/page errors).

Remaining program items deferred to the next batch (still open):
- Voice wake word on device (needs a wake-word model + audio pipeline work).
- Full Zigbee device list UI (depends on the panel-local zgateway topic set).
- Gen2 hardware profiles (no Gen2 unit available to verify against).
- OTA automatic rollback trigger (backup image is written; restore is manual).

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
| 10 | 2026-11-12 | CRITICAL: topic_safe not applied in async_setup_entry — MQTT topic mismatch | v0.4.29 |
| 11 | 2026-11-12 | `js_url` legacy load path, REST `config/entity_registry/list` 404, hardcoded version, blank before `hass` arrives | v0.4.27 |
| 12 | 2026-11-12 | Stale browser cache masking new panel.js (unversioned module URL) | v0.4.31 |
| 13 | 2026-11-13 | Full 4-angle audit (HA linkages / panel.js / Android / versions): _link_for not normalized, update listener not wired, options defaults stale, zeroconf segment raw, 3x topic_safe copies, deprecated units, float churn, DOMAIN hardcode, registry fallback missing, fallback panel/nspanel, brightness debounce, url guard, online default, form wipe, double refresh, XSS, select shape, dead token/logs/OBJ_ID, Android retain/exact-match/NPE/stale-URL/version-crash/OTA-su/Voice-proto, .gradle tracked, ha-brands untracked, LEDGER gaps, Android version frozen | v0.4.33 |
| 14 | 2026-11-13 | Full 4-angle REPAIR applied + verified (ruff/node/gradle green); v0.4.34 cut but tag pointed at pre-fix commit, re-shipped as below | v0.4.34 |
| 15 | 2026-11-13 | Open-item repairs (v0.4.35) + Paho callback deadlock root-caused via SIGQUIT trace and fixed (v0.4.36) |
| 16 | 2026-11-13 | Sidebar-first add flow + setup hardening (migration/listener never fail, cross-scheme duplicate guard) | v0.4.37 |
| 17 | 2026-11-13 | UnitOfLuminance/UnitOfTime ImportError on user's HA → version-proof unit import with string fallback | v0.4.38 |
| 18 | 2026-11-13 | Full firmware configuration from the app: text/number/select entities, all module switches, sidebar Settings form, Android token masking + dashboard/rd hot-apply. Verified live: modules state, config round-trip 170→180, secrets masked | v0.4.39 |
| 19 | 2026-11-13 | Panels-first redesign: card = RD image + Info/Settings/Remote only; all data/actions in popups; wider+compact Settings; RD on/off + token in Settings | v0.4.44 |
| 20 | 2026-11-13 | Settings rework: RD section pinned at top (on/off toggle, token+port, Save RD, Browser token, Open remote); token stays in full form; 2-column compact grid, 900px modal | v0.4.45 |
| 21 | 2026-11-13 | OBSERVED via headless render: settings card squeezed into narrow grid column (`.grid` auto-fill); fixed with `.modal .grid{1fr}` single-column; re-rendered to confirm full-width | v0.4.46 |
| 22 | 2026-11-13 | HA-side RD proxy (same-origin signed URLs, server-side token) so live screen works over https/remote; fixed missing `module_remote_desktop` entity-map alias (RD toggle was dead); verified: 6 proxy unit tests + live E2E (real panel screen in card) | v0.4.47 |
| 23 | 2026-11-13 | Probed user HA: frontend v0.4.47 live + proxy registered (403). Fixed stream restart every 5s refresh (persistent re-adopted img node, proven sameNode across rebuilds); exact failure reasons in UI (fetch probe: 502 token/panel vs 403 stale signature vs setup error); square NSPanel Pro placeholder | v0.4.48 |
| 24 | 2026-11-13 | Split 502 causes (rd-token-missing / panel-ip-unknown / panel-unreachable@host:port / panel-answered-401) with per-cause UI text; fixed UnboundLocalError 500 on panel-down (found by unit test); 8 proxy tests green | v0.4.49 |
| 25 | 2026-11-13 | Kiosk loop live-verified: dashboard default, 6-tap exit, optional PIN pad (IME auto-hidden), Return-to-HA button, RD text input; removed Fully Kiosk + Pro Tools from panel (were device admins); raised RD stream cap 3→8 | v0.4.50 |
| 26 | 2026-11-13 | Proper HA login (OAuth authorize in-app, custom-scheme code capture, refresh token stored, WebView session injected via hassTokens, logout clears) — verified to the credential step; user completes the one-time login | v0.4.51 |
| 27 | 2026-11-13 | Dark dashboard (night reporting + WebView-local invert filter w/ media keep-normal; dark+light both verified live) + boot autostart (kiosk UI + MQTT backoff retry; verified by real reboot, zero touches) | v0.4.52 |
| 28 | 2026-11-13 | Firmware chrome hidden by default (pages container GONE until 6-tap exit; Return-to-HA only in firmware mode) — fixes bottom nav bar always visible + app backgrounding on tap; verified live | v0.4.53 |
| 29 | 2026-11-13 | PIN pad false trigger fixed: exit gesture now requires 6 taps in the top-left corner (80x80); normal dashboard use no longer accumulates taps; leftover test PIN '0208' cleared from the panel | v0.4.54 |
| 30 | 2026-11-13 | Android system navigation bar hidden in kiosk mode (immersive sticky, re-applied on window focus, restored in firmware mode); verified live | v0.4.55 |
| 31 | 2026-11-13 | Removed the inverted-colors 'dark mode' hack; dark mode now selects HA's native dark theme (no inversion, media stays normal); verified live | v0.4.56 |
| 32 | 2026-11-13 | Verified: firmware OTA module already present (checkForUpdate + sha256 + dd + reboot) and wired to MQTT firmware:check/install + sidebar 'Check firmware' button; no ADB needed in production | v0.4.58 |
| 33 | 2026-11-13 | HA sidebar lost connection: root cause was a dropped retained status (no re-publish); fixed with status=online on every connectComplete + 60s keepalive watchdog; verified live (status online after restart, stays online) | v0.4.59 |
| 34 | 2026-11-13 | Settings wiped: root cause was empty-string patches from sidebar Save + Settings.patch blindly putting them; guarded both sides; verified settings intact after update | v0.4.61 |
| 35 | 2026-11-13 | Ecosystem program (F1–F15). Live-found defect: activity bound dashboard callbacks before the service registered modules, so screensaver/camera/TTS/toast/navigate/refresh commands were dropped; binding retries + rebinds onResume, kiosk/day-night state self-heal, disabling the screensaver now hides it. Verified on-panel: kiosk transitions, auto-return, scheduled reload, day/night brightness, zigbee/wifi/storage/ram state, TTS SUCCESS, camera overlay, app_available=0.4.69 | v0.4.69 |
| 36 | 2026-11-13 | Docs catch-up for the ecosystem program: HA_INTEGRATION (entity + 12-service tables, Info/Kiosk UI) and MQTT_COMMANDS (new commands + state topics). No code changes; APK re-versioned, installed, state/firmware shows app 0.4.70 | v0.4.70 |

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

### Resolved in verification (2026-11-13, live panel + SIGQUIT trace)
- [x] New-build onConnected published nothing (no status/modules/ip/uptime/discovery) — root cause: blocking `c.publish()` + `c.subscribe()` on Paho's CommsCallback thread deadlocks it (Token.waitForResponse waits on the thread that must dispatch the ack). Found via `kill -QUIT` stack dump (`MqttClient.publish:570` <- `onConnected:119`). Fixed by moving the entire post-connect sequence to the single publish worker thread. Verified live: status online, state/modules, ip_address, uptime, discovery all retained; tap → `event/touch {"event_type":"tap"}`.
- [x] HA shows old integration/app versions — HA served panel.js v0.4.35 (update applied); panel APK was 0.2.0 (12 releases behind). Rebuilt, signed, reinstalled → 0.4.35, verified via `pm dump`.

### Known issues to investigate
- [x] HA sidebar blank page — fixed in v0.4.27 (`module_url` + immediate skeleton + WS registry)
- [x] Unlink Delete 401 / Unknown command — REST DELETE is the only valid path; WS `config_entries/delete` does not exist. Keep callApi DELETE + manual-remove fallback (v0.4.32)
- [x] DisplayModule retry publish — replaced with queue-until-connected in MqttModule + connect-time state flush (v0.4.35)
- [x] FirmwareModule reads version via su — updater now chmods 644; app reads direct first, su fallback with timeout (v0.4.35)
- [ ] VoiceModule publishEntities() is no-op (phase 2 — Assist satellite entity is a Wyoming-protocol concern, not MQTT discovery)
- [x] ZigbeeModule local publish — dedicated panel-local broker publish with graceful fallback (v0.4.35)
- [ ] Entity unique_ids remain device-only (same name on 2 prefixes collides on one platform) — accepted edge; entry unique_ids + device identifiers are prefix-qualified since v0.4.35

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
| v0.4.26 | 2026-11-12 | Fix unlink (callApi DELETE), device-name prefix strip |
| v0.4.27 | 2026-11-12 | `module_url` load path, WS entity registry, dynamic version, never-blank skeleton |
| v0.4.28 | 2026-11-12 | Unlink via WS `config_entries/delete` attempt + hass capability logging (reverted: WS command does not exist) |
| v0.4.29 | 2026-11-12 | CRITICAL: topic_safe in async_setup_entry (MQTT mismatch root cause) |
| v0.4.30 | 2026-11-12 | Version alignment (manifest/hacs/panel fallback) |
| v0.4.31 | 2026-11-12 | Cache-busted panel module URL (`panel.js?v=<version>`) — a stale cached panel.js can no longer mask a release |
| v0.4.32 | 2026-11-12 | Unlink back to callApi DELETE + helpful manual-remove message |
| v0.4.33 | 2026-11-13 | 4-angle full audit: HA normalization/update-listener/options/units, panel registry fallback/debounce/XSS/select-shape, Android retain/exact-match/NPE/Voice-proto/OTA-su, versions aligned, .gradle untracked |
| v0.4.34 | 2026-11-13 | 4-angle audit REPAIR applied (ruff/node/gradle green); tag pointed at pre-fix commit so fixes re-shipped in .35 |
| v0.4.35 | 2026-11-13 | Open-item repairs: touch tap/swipe publisher, retained state/modules sync both sides, prefix-qualified entry unique_ids + migration, prefix-qualified device identifiers, zigbee panel-local publish, updater chmod 644, bump_version.sh |
| v0.4.36 | 2026-11-13 | Paho callback-thread deadlock fix (post-connect moved to worker) + @Volatile client/uptimeThread/onConfigUpdated; verified live on panel |
| v0.4.37 | 2026-11-13 | Sidebar-first add flow (empty state keeps +Add), setup hardening (migration/listener never fail setup), cross-scheme duplicate guard |
| v0.4.38 | 2026-11-13 | Version-proof sensor units (ImportError fix from live user log) |
| v0.4.39 | 2026-11-13 | Full firmware configuration from the Nexus app (15 text + 7 number + zigbee select, all module switches, sidebar Settings form with Load/Save-all, Android secret masking + dashboard/rd hot-apply) |
| v0.4.44 | 2026-11-13 | Panels-first redesign: card = RD image + Info/Settings/Remote; all data/actions in popups; wider+compact Settings |
| v0.4.45 | 2026-11-13 | Settings rework: RD section on top (on/off + token + Save RD + Browser token + Open remote); 2-column compact grid |
| v0.4.46 | 2026-11-13 | Settings modal cards full-width (`.modal .grid` single column); verified with headless render |
| v0.4.47 | 2026-11-13 | RD proxy (signed same-origin URLs, server-side token, HA-authed input); fixed dead RD module toggle alias; proxy unit-tested + live E2E verified |
| v0.4.48 | 2026-11-13 | Persistent stream node across refreshes; exact UI failure reasons; square placeholder; verified headless (sameNode) |
| v0.4.49 | 2026-11-13 | Per-cause proxy errors incl. wrong-token (401) detection; fixed panel-down 500 crash; 8 tests green |
| v0.4.50 | 2026-11-13 | Kiosk: 6-tap exit + PIN pad + Return-to-HA + RD text input (all live-verified); FK/Pro Tools removed; RD cap 8 |
| v0.4.51 | 2026-11-13 | In-app OAuth login + WebView session injection (Companion-style); logout; verified to credential step |
| v0.4.52 | 2026-11-13 | Dark dashboard toggle + boot autostart with MQTT retry (both live-verified incl. reboot test) |
| v0.4.53 | 2026-11-13 | Chrome hidden by default; 6-tap exit reveals it (live-verified) |
| v0.4.54 | 2026-11-13 | Exit gesture restricted to top-left corner; test PIN cleared |
| v0.4.55 | 2026-11-13 | Immersive mode: nav bar hidden in kiosk, restored in firmware (live-verified) |
| v0.4.56 | 2026-11-13 | Dark mode via HA native theme (no inversion); media stays normal |
| v0.4.58 | 2026-11-13 | OTA exposed in Setup; sidebar Check firmware already wired |
| v0.4.59 | 2026-11-13 | MQTT keepalive: status re-published on connect + every 60s (fixes silent offline) |
| v0.4.61 | 2026-11-13 | Settings wipe guard (panel + sidebar) |
| v0.4.69 | 2026-11-13 | Ecosystem program: screensaver, auto-return, reload, day/night, TTS/toast/camera, navigate/refresh, kiosk+health+zigbee state, capabilities, HA update/notify entities, 12 services, config backup/restore (live-verified) |
| v0.4.70 | 2026-11-13 | Docs for the ecosystem program (no code changes) |

> **Note:** v0.4.30 was already published by a concurrent session (tag pointed
> at the pre-fix commit 82c92dd), so this cache-bust fix ships as v0.4.31 to
> avoid reusing a version number (rule 11).

---

## 7. How to use this ledger

1. **Before any task**: Write the request in section 2 (or a new subsection)
2. **During work**: Update status as you go (building → done)
3. **After work**: Verify end-to-end, update version, create release
4. **Rules**: Follow section 3 — no exceptions
