"""PanelLink logic tests — no Home Assistant needed.

`link.py` only needs three names from HA (`HomeAssistant`, `callback`,
`DeviceInfo`), so we stub those modules and load the real file. This tests
the actual shipped code: topic sanitising, the version read, and the config
request/response cache (the piece the sidebar Settings form depends on).
"""
from __future__ import annotations

import asyncio
import importlib.util
import json
import sys
import types
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
INTEGRATION = REPO / "custom_components" / "nspanel_nexus"


def _stub_homeassistant() -> None:
    if "homeassistant" in sys.modules:
        return
    ha = types.ModuleType("homeassistant")
    core = types.ModuleType("homeassistant.core")
    core.HomeAssistant = type("HomeAssistant", (), {})
    core.callback = lambda f: f
    helpers = types.ModuleType("homeassistant.helpers")
    device_registry = types.ModuleType("homeassistant.helpers.device_registry")

    class DeviceInfo(dict):
        def __init__(self, **kwargs):
            super().__init__(**kwargs)
            self.__dict__.update(kwargs)

    device_registry.DeviceInfo = DeviceInfo
    ha.core = core
    ha.helpers = helpers
    sys.modules["homeassistant"] = ha
    sys.modules["homeassistant.core"] = core
    sys.modules["homeassistant.helpers"] = helpers
    sys.modules["homeassistant.helpers.device_registry"] = device_registry


def _load_pkg_module(name: str):
    pkg_name = "nexus_test_pkg"
    pkg = sys.modules.get(pkg_name)
    if pkg is None:
        pkg = types.ModuleType(pkg_name)
        pkg.__path__ = [str(INTEGRATION)]
        sys.modules[pkg_name] = pkg
    full = f"{pkg_name}.{name}"
    if full in sys.modules:
        return sys.modules[full]
    spec = importlib.util.spec_from_file_location(full, INTEGRATION / f"{name}.py")
    assert spec and spec.loader
    mod = importlib.util.module_from_spec(spec)
    sys.modules[full] = mod
    spec.loader.exec_module(mod)
    return mod


_stub_homeassistant()
const = _load_pkg_module("const")
link_mod = _load_pkg_module("link")
PanelLink = link_mod.PanelLink


class FakeMsg:
    def __init__(self, payload: bytes):
        self.payload = payload


def _make_link(**kwargs) -> PanelLink:
    return PanelLink(hass=object(), device_name="Conservatory Panel",
                     topic_prefix="nspanelpro", **kwargs)


def test_topic_safe() -> None:
    assert const.topic_safe("Conservatory Panel") == "Conservatory_Panel"
    assert const.topic_safe("a/b c!") == "a_b_c"
    assert const.topic_safe("  __x__  ") == "x"
    assert const.topic_safe("") == "nspanel"


def test_platforms_include_new_channels() -> None:
    assert "update" in const.PLATFORMS
    assert "notify" in const.PLATFORMS


def test_integration_version_matches_manifest() -> None:
    manifest = json.loads((INTEGRATION / "manifest.json").read_text())
    assert link_mod.integration_version() == manifest["version"]


def test_device_info_reports_real_version() -> None:
    info = _make_link().device_info
    manifest = json.loads((INTEGRATION / "manifest.json").read_text())
    assert info["sw_version"] == manifest["version"]
    assert info["sw_version"] != "Nexus"


def test_on_config_merges_bare_object() -> None:
    link = _make_link()
    link._on_config(FakeMsg(b'{"brightness": 180}'))
    assert link.config == {"brightness": 180}


def test_on_config_merges_wrapped_object() -> None:
    link = _make_link()
    link._on_config(FakeMsg(b'{"brightness": 180}'))
    link._on_config(FakeMsg(b'{"config": {"mqtt_port": 1883}}'))
    assert link.config == {"brightness": 180, "mqtt_port": 1883}


def test_on_config_ignores_non_dict() -> None:
    link = _make_link()
    link._on_config(FakeMsg(b'[1, 2]'))
    link._on_config(FakeMsg(b'not json'))
    assert link.config == {}


def test_on_config_wakes_waiters() -> None:
    link = _make_link()
    event = asyncio.Event()
    link._config_waiters.append(event)
    link._on_config(FakeMsg(b'{"a": 1}'))
    assert event.is_set()
    assert link._config_waiters == []


def test_async_get_config_uses_cache() -> None:
    link = _make_link()
    link.config = {"brightness": 180}
    calls: list[str] = []

    async def _fail() -> None:
        calls.append("query")

    link.async_config_query = _fail  # type: ignore[method-assign]
    assert asyncio.run(link.async_get_config()) == {"brightness": 180}
    assert calls == []


def test_async_get_config_asks_panel_once() -> None:
    link = _make_link()
    queries: list[str] = []

    async def _answer() -> None:
        queries.append("query")
        link._on_config(FakeMsg(b'{"config": {"brightness": 170}}'))

    link.async_config_query = _answer  # type: ignore[method-assign]
    assert asyncio.run(link.async_get_config(timeout=2.0)) == {"brightness": 170}
    assert queries == ["query"]


def test_async_get_config_times_out_cleanly() -> None:
    link = _make_link()

    async def _silent() -> None:
        pass

    link.async_config_query = _silent  # type: ignore[method-assign]
    assert asyncio.run(link.async_get_config(timeout=0.05)) == {}
    assert link._config_waiters == []
