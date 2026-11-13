"""Release consistency + cross-side contract regression tests.

No Home Assistant needed: everything here works on the source tree with the
standard library, so it runs in CI and locally in seconds.

What it guards (all real bugs we have hit before):
- version drift between manifest / hacs.json / APK versionName / panel.js
- a service registered in Python but missing from services.yaml (or dead
  YAML entries) — the sidebar calls services by name
- hardcoded LAN addresses leaking into shipped code (ledger rule 4)
- MQTT key mismatches between the Kotlin publishers and the Python
  consumers (e.g. update.py reading a key the panel never sends)
- panel.js Settings fields referencing config keys HA knows nothing about
"""
from __future__ import annotations

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
INTEGRATION = REPO / "custom_components" / "nspanel_nexus"
PANEL_JS = INTEGRATION / "www" / "nspanel-nexus-panel.js"
APP = REPO / "app" / "app" / "src" / "main" / "java" / "io" / "nexus" / "panel"


def _read_version_files() -> dict[str, str]:
    manifest = json.loads((INTEGRATION / "manifest.json").read_text())
    hacs = json.loads((REPO / "hacs.json").read_text())
    gradle = (REPO / "app" / "app" / "build.gradle.kts").read_text()
    m = re.search(r'versionName = "(0\.\d+\.\d+)"', gradle)
    assert m, "versionName not found in build.gradle.kts"
    fallback = re.search(r'return v \|\| "(v0\.\d+\.\d+)"', PANEL_JS.read_text())
    assert fallback, "panel.js version fallback not found"
    return {
        "manifest": manifest["version"],
        "hacs": hacs["version"],
        "apk": m.group(1),
        "panel_js": fallback.group(1).lstrip("v"),
    }


def test_versions_in_sync() -> None:
    versions = _read_version_files()
    assert len(set(versions.values())) == 1, f"version drift: {versions}"


def test_services_match_yaml() -> None:
    import yaml  # pyyaml ships with the HA test env; pip install pyyaml locally

    declared = set(
        yaml.safe_load((INTEGRATION / "services.yaml").read_text()).keys()
    )
    registered = set(
        re.findall(r'async_register\(DOMAIN,\s*"([a-z_]+)"', (INTEGRATION / "__init__.py").read_text())
    )
    assert registered, "no services found registered in __init__.py"
    assert registered - declared == set(), f"registered but not in services.yaml: {registered - declared}"
    assert declared - registered == set(), f"in services.yaml but not registered: {declared - registered}"


def test_no_hardcoded_lan_addresses() -> None:
    lan = re.compile(r"\b(?:192\.168|10\.|172\.(?:1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}\b")
    offenders: list[str] = []
    for path in list(INTEGRATION.rglob("*.py")) + list(INTEGRATION.rglob("*.js")):
        if "tests" in path.parts:
            continue
        for i, line in enumerate(path.read_text().splitlines(), 1):
            if lan.search(line):
                offenders.append(f"{path.relative_to(REPO)}:{i}: {line.strip()}")
    assert offenders == [], "hardcoded LAN address in shipped code:\n" + "\n".join(offenders)


def _kotlin_put_keys(*files: str) -> set[str]:
    """Payload fields the panel publishes: JSONObject().put("field", ...)."""
    keys: set[str] = set()
    for f in files:
        text = (APP / f).read_text()
        keys.update(re.findall(r'\.put\("([a-z_]+)"', text))
    return keys


def _kotlin_state_topics() -> set[str]:
    """State topics the panel publishes: state("topic", …) / .state("topic", …)."""
    topics: set[str] = set()
    for path in (APP).rglob("*.kt"):
        topics.update(re.findall(r'(?:^|[^\w.])state\("([a-z_]+)"', path.read_text()))
        topics.update(re.findall(r'\.state\("([a-z_]+)"', path.read_text()))
    return topics


def _python_consumed_keys(*files: str) -> set[str]:
    """Payload fields the HA side reads out of state messages."""
    keys: set[str] = set()
    for f in files:
        text = (INTEGRATION / f).read_text()
        keys.update(re.findall(r'json_key="([a-z_]+)"', text))
        keys.update(re.findall(r'data\.get\("([a-z_]+)"', text))
        keys.update(re.findall(r'data\["([a-z_]+)"\]', text))
    return keys


def _python_consumed_topics() -> set[str]:
    """State topics the HA side subscribes to."""
    topics: set[str] = set()
    for path in INTEGRATION.glob("*.py"):
        text = path.read_text()
        topics.update(re.findall(r'async_subscribe_state\("([a-z_]+)"', text))
    # NexusSensor subscribes to its own key: NexusSensor(link, "<key>", ...).
    for m in re.finditer(r'NexusSensor\(link,\s*"([a-z_]+)"',
                         (INTEGRATION / "sensor.py").read_text()):
        topics.add(m.group(1))
    return topics


def _kotlin_event_kinds() -> set[str]:
    kinds: set[str] = set()
    for path in (APP).rglob("*.kt"):
        kinds.update(re.findall(r'\.event\("([a-z_]+)"', path.read_text()))
    return kinds


def _python_consumed_events() -> set[str]:
    kinds: set[str] = set()
    for path in INTEGRATION.glob("*.py"):
        text = path.read_text()
        kinds.update(re.findall(r'async_subscribe_event\("([a-z_]+)"', text))
    for m in re.finditer(r'NexusEvent\(link,\s*"([a-z_]+)"',
                         (INTEGRATION / "event.py").read_text()):
        kinds.add(m.group(1))
    return kinds


def test_mqtt_event_contract() -> None:
    missing = _python_consumed_events() - _kotlin_event_kinds()
    assert missing == set(), \
        f"HA subscribes to events the panel never publishes: {missing}"


def test_mqtt_state_contract() -> None:
    published_fields = _kotlin_put_keys(
        "modules/firmware/FirmwareModule.kt",
        "modules/sensors/SensorModule.kt",
        "modules/zigbee/ZigbeeModule.kt",
        "modules/display/DisplayModule.kt",
        "modules/dashboard/DashboardModule.kt",
        "modules/mqtt/MqttModule.kt",
    )
    consumed_fields = _python_consumed_keys("sensor.py", "update.py")
    missing_fields = consumed_fields - published_fields
    assert missing_fields == set(), \
        f"HA reads state fields the panel never publishes: {missing_fields}"

    published_topics = _kotlin_state_topics()
    consumed_topics = _python_consumed_topics()
    missing_topics = consumed_topics - published_topics
    assert missing_topics == set(), \
        f"HA subscribes to state topics the panel never publishes: {missing_topics}"


def _ha_config_keys() -> set[str]:
    keys: set[str] = set()
    text_src = (INTEGRATION / "text.py").read_text()
    keys.update(re.findall(r'^\s*\("([a-z_0-9]+)",', text_src, re.MULTILINE))
    num_src = (INTEGRATION / "number.py").read_text()
    keys.update(re.findall(r'^\s*\("([a-z_0-9]+)",', num_src, re.MULTILINE))
    sel_src = (INTEGRATION / "select.py").read_text()
    keys.update(re.findall(r'screensaver_mode|zigbee_role', sel_src))
    sw_src = (INTEGRATION / "switch.py").read_text()
    keys.update(re.findall(r'NexusConfigSwitch\(link,\s*"([a-z_0-9]+)"', sw_src))
    return keys


def _panel_js_config_keys() -> set[str]:
    src = PANEL_JS.read_text()
    keys: set[str] = set()
    # ["mqtt_host","mqtt_user",...].forEach((k) => { candidates["cfg_" + k] ... })
    for m in re.finditer(r'\[((?:"[a-z_0-9]+",?\s*)+)\]\.forEach\(\(k\)', src):
        keys.update(re.findall(r'"([a-z_0-9]+)"', m.group(1)))
    keys.update(re.findall(r'candidates\["cfg_([a-z_0-9]+)"\]', src))
    keys.update(re.findall(r'\bsel\("([a-z_0-9]+)"', src))
    return keys


def test_panel_js_config_keys_known_to_ha() -> None:
    ha_keys = _ha_config_keys()
    panel_keys = _panel_js_config_keys()
    assert panel_keys, "no config keys found in panel.js — extractor is stale"
    unknown = panel_keys - ha_keys
    assert unknown == set(), f"panel.js Settings references unknown config keys: {sorted(unknown)}"
