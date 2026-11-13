"""Headless UI regression test for the sidebar panel (panel.js).

Loads the real shipped panel.js in Chromium (system chromium + Playwright),
renders the Panels tab, opens the Info popup and the Settings popup with
every accordion expanded, and fails on any console/page error.

Skips cleanly when Playwright or a Chromium binary is unavailable (e.g. CI
without browsers) — the file-level checks in test_release.py still run.
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

import pytest

playwright_sync = pytest.importorskip("playwright.sync_api", reason="playwright not installed")

REPO = Path(__file__).resolve().parent.parent
PANEL_JS = REPO / "custom_components" / "nspanel_nexus" / "www" / "nspanel-nexus-panel.js"

DEV = "Conservatory_Panel"
SLUG = "conservatory_panel"


def _build_states() -> dict:
    states: dict = {}

    def st(eid: str, value, attrs=None):
        states[eid] = {"entity_id": eid, "state": str(value), "attributes": attrs or {}}

    for dom, suffix, val in [
        ("switch", "screen", "on"),
        ("number", "brightness", 180),
        ("switch", "remote_desktop", "on"),
        ("switch", "voice_satellite", "off"),
        ("switch", "zigbee", "on"),
        ("switch", "sensors", "on"),
        ("switch", "display", "on"),
        ("switch", "dashboard", "on"),
        ("switch", "firmware", "on"),
        ("switch", "fleet", "off"),
        ("switch", f"{SLUG}_screensaver_enabled", "off"),
        ("button", "re_read_config", "2026-11-13T10:00:00+00:00"),
        ("sensor", "illuminance", 42),
        ("sensor", "ip_address", "192.168.50.22"),
        ("sensor", "uptime", 86400),
        ("sensor", "kiosk", "dashboard"),
        ("sensor", "screensaver", "off"),
        ("sensor", "day_night", "day"),
        ("sensor", "wifi", -57),
        ("sensor", "storage", 71.4),
        ("sensor", "ram", 38.2),
        ("sensor", "zigbee", 0),
    ]:
        st(f"{dom}.nspanel_{SLUG}_{suffix}", val)
    states["sensor.nspanel_conservatory_panel_firmware"] = {
        "entity_id": "sensor.nspanel_conservatory_panel_firmware",
        "state": "1.0.0",
        "attributes": {"app_version": "0.4.71", "available_version": "1.0.0"},
    }
    for key, val in {
        "mqtt_host": "192.168.50.20", "mqtt_user": "ALeXX", "topic_prefix": "nspanelpro",
        "ha_topic_prefix": "homeassistant", "ha_url": "http://192.168.50.20:8123",
        "dashboard_url": "http://192.168.50.20:8123/lovelace/0",
        "zigbee_topic_base": "zigbee", "night_start": "22:00", "night_end": "07:00",
        "screen_on_weekday_begin": "00:00", "screen_on_weekday_end": "00:00",
        "screen_on_weekend_begin": "00:00", "screen_on_weekend_end": "00:00",
    }.items():
        st(f"text.nspanel_{SLUG}_{key}", val)
    for key, val in {
        "mqtt_port": 1883, "light_below": 20, "light_above": 200, "proximity_delta": 30,
        "brightness_on_light_below": 60, "brightness_on_light_above": 200,
        "brightness_day": 200, "brightness_night": 40, "screensaver_timeout": 120,
        "screensaver_brightness": 0, "auto_return_seconds": 180,
        "dashboard_reload_minutes": 30,
    }.items():
        st(f"number.nspanel_{SLUG}_{key}", val)
    st(f"select.nspanel_{SLUG}_zigbee_role", "coordinator")
    st(f"select.nspanel_{SLUG}_screensaver_mode", "clock")
    return states


def _harness_html(tmp_path: Path) -> Path:
    states = _build_states()
    entry = {
        "entry_id": "entry123", "domain": "nspanel_nexus",
        "title": "NSPanel Conservatory Panel",
        "data": {"device_name": DEV, "topic_prefix": "nspanelpro"},
        "options": {}, "raw_data": {},
    }
    registry = []
    for eid in states:
        domain, obj = eid.split(".", 1)
        registry.append({
            "entity_id": eid, "domain": domain,
            "unique_id": f"nexus_{SLUG}_{obj.replace('nspanel_' + SLUG + '_', '')}",
            "config_entry_id": "entry123",
        })
    hass_js = (
        """
window.__svc = [];
window.__hass = {
  states: __STATES__,
  callWS: async (msg) => {
    if (msg.type === "config_entries/get") return __ENTRY__;
    if (msg.type === "config/entity_registry/list") return __REGISTRY__;
    return {};
  },
  callApi: async () => __ENTRY__,
  callService: async (domain, service, data) => { window.__svc.push([domain, service, data]); },
};
""".replace("__STATES__", json.dumps(states))
        .replace("__ENTRY__", json.dumps([entry]))
        .replace("__REGISTRY__", json.dumps(registry))
    )
    html = (
        """<!doctype html><html><head><meta charset="utf-8">
<style>html,body{margin:0;background:#0a0c10}nexus-harness{display:block}</style>
</head><body>
<nexus-harness></nexus-harness>
<script src="file://__PANEL_JS__"></script>
<script>
customElements.define("nexus-harness", class extends HTMLElement {
  constructor() {
    super();
    const el = document.createElement("nspanel-nexus-manager");
    el.setAttribute("id", "mgr");
    this.appendChild(el);
    window.__mgr = el;
  }
});
</script>
<script>__HASS_JS__</script>
<script>
  customElements.whenDefined("nspanel-nexus-manager").then(() => {
    window.__mgr._rdUrl = async () => "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
    setTimeout(() => { window.__mgr.hass = window.__hass; }, 30);
  });
</script>
</body></html>
""".replace("__PANEL_JS__", str(PANEL_JS)).replace("__HASS_JS__", hass_js)
    )
    path = tmp_path / "harness.html"
    path.write_text(html, encoding="utf-8")
    return path


def _chromium_available() -> bool:
    return shutil.which("chromium") or shutil.which("chromium-browser") or shutil.which("google-chrome") is not None


@pytest.mark.skipif(not _chromium_available(), reason="no chromium binary")
def test_sidebar_renders_with_both_versions_and_no_errors(tmp_path: Path) -> None:
    html_path = _harness_html(tmp_path)
    errors: list[str] = []
    with playwright_sync.sync_playwright() as p:
        browser = p.chromium.launch()
        try:
            page = browser.new_page(viewport={"width": 1280, "height": 1000})
            page.on("console", lambda m: errors.append(f"console.{m.type}: {m.text}")
                    if m.type in ("error", "warning") else None)
            page.on("pageerror", lambda e: errors.append(f"pageerror: {e}"))
            page.goto(html_path.as_uri())
            page.wait_for_timeout(1200)

            topbar = page.evaluate("""() => {
              const el = document.getElementById('mgr').shadowRoot.querySelector('.topbar .ver');
              return el ? el.textContent : null;
            }""")
            assert topbar and "integration v" in topbar and "panel app v" in topbar, \
                f"topbar must label both versions, got: {topbar!r}"

            def click_btn(label: str) -> bool:
                return page.evaluate("""(label) => {
                  const nodes = document.getElementById('mgr').shadowRoot.querySelectorAll('button');
                  for (const b of nodes) if (b.textContent.trim() === label) { b.click(); return true; }
                  return false;
                }""", label)

            assert click_btn("Info"), "Info button not found"
            page.wait_for_timeout(700)
            info = page.evaluate("""() => {
              const sr = document.getElementById('mgr').shadowRoot;
              return {
                rows: Array.from(sr.querySelectorAll('.setgrid.cols3 .lbl')).map((e) => e.textContent.trim()),
                speak: !!sr.querySelector('[data-act=speak]'),
                refresh: !!sr.querySelector('[data-act=refresh]'),
                screensaver: !!sr.querySelector('[data-act=ssToggle]'),
              };
            }""")
            for row in ("Kiosk", "Screensaver", "Day / night", "Wi-Fi",
                        "Storage free", "RAM free", "Zigbee join"):
                assert row in info["rows"], f"info popup missing row: {row}"
            assert info["speak"] and info["refresh"] and info["screensaver"]
            page.evaluate("""() => {
              const x = document.getElementById('mgr').shadowRoot.querySelector('.modal .x');
              if (x) x.click();
            }""")
            page.wait_for_timeout(400)

            assert click_btn("Settings"), "Settings button not found"
            page.wait_for_timeout(700)
            page.evaluate("""() => {
              document.getElementById('mgr').shadowRoot
                .querySelectorAll('.acc.closed .acc-head').forEach((h) => h.click());
            }""")
            page.wait_for_timeout(400)
            kiosk = page.evaluate("""() => {
              const sr = document.getElementById('mgr').shadowRoot;
              return {
                keys: Array.from(sr.querySelectorAll('[data-cfg]')).map((e) => e.getAttribute('data-cfg')),
                heads: Array.from(sr.querySelectorAll('.acc-head')).map((e) => e.textContent.trim()),
              };
            }""")
            for key in ("screensaver_enabled", "screensaver_mode", "screensaver_timeout",
                        "auto_return_seconds", "dashboard_reload_minutes",
                        "brightness_day", "brightness_night", "night_start", "night_end"):
                assert key in kiosk["keys"], f"settings missing kiosk field: {key}"
            assert any("KIOSK" in h.upper() for h in kiosk["heads"])
        finally:
            browser.close()
    real = [e for e in errors if "ERR_FILE_NOT_FOUND" not in e]
    assert not real, "console/page errors:\n" + "\n".join(real)
