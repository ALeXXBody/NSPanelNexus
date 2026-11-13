"""NSPanel Nexus — sidebar panel + manager services."""

from __future__ import annotations

import json
import logging
from pathlib import Path

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, SupportsResponse
from homeassistant.exceptions import ServiceValidationError

from . import rd_proxy
from .const import CONF_DEVICE_NAME, CONF_TOPIC_PREFIX, DOMAIN, PLATFORMS, topic_safe
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)

PANEL_URL = "/nspanel-nexus"

_MANIFEST = Path(__file__).parent / "manifest.json"


def _read_version() -> str:
    """Read the integration version (blocking — executor only)."""
    try:
        return str(json.loads(_MANIFEST.read_text(encoding="utf-8"))["version"])
    except Exception:  # noqa: BLE001
        _LOGGER.warning("could not read integration version")
        return "0.0.0"


SERVICE_COMMAND_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("command"): str,
    vol.Optional("payload"): dict,
})

SERVICE_CONFIG_UPDATE_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("config"): dict,
})

SERVICE_PANEL_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
})

SERVICE_MESSAGE_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("message"): str,
})

SERVICE_CAMERA_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("url"): str,
    vol.Optional("title", default="Camera"): str,
    vol.Optional("duration", default=30): vol.All(int, vol.Range(min=5, max=3600)),
})

SERVICE_NAVIGATE_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("path"): str,
})

SERVICE_SCREENSAVER_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Optional("mode", default=""): vol.In(["", "clock", "blank", "dim"]),
})

SERVICE_RESTORE_CONFIG_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("config"): dict,
})


async def _register_sidebar(hass: HomeAssistant) -> None:
    """Put NSPanel Nexus in HA's left sidebar as the panel manager."""
    # imported lazily: importing HA components at module import time blocks
    # the event loop (HA logs a "blocking call to import_module" warning)
    from homeassistant.components import panel_custom
    from homeassistant.components.http import StaticPathConfig

    js_url = PANEL_URL + "/panel.js"
    js_path = Path(__file__).parent / "www" / "nspanel-nexus-panel.js"

    version = await hass.async_add_executor_job(_read_version)

    # serve the panel script + brand images
    brand_path = Path(__file__).parent / "www" / "brand"
    await hass.http.async_register_static_paths(
        [
            StaticPathConfig(js_url, str(js_path), False),
            StaticPathConfig(PANEL_URL + "/brand", str(brand_path), True),
        ]
    )

    await panel_custom.async_register_panel(
        hass,
        frontend_url_path=PANEL_URL.lstrip("/"),
        webcomponent_name="nspanel-nexus-manager",
        sidebar_title="NSPanel Nexus",
        sidebar_icon="mdi:tablet-dashboard",
        # module_url is the modern load path; js_url is deprecated (and can
        # be dropped by newer frontends, which left the panel element
        # undefined and the page blank).
        # The ?v= cache-buster is required: this URL never changed between
        # releases, so a browser that cached an older panel.js kept running it
        # (older builds used REST entity-registry calls that 404 on HA 2026.11
        # and never rendered). Bump = guaranteed cache miss.
        module_url=f"{js_url}?v={version}",
        config={"domain": DOMAIN, "title": "NSPanel Nexus", "version": f"v{version}"},
        require_admin=True,
    )


async def async_setup(hass: HomeAssistant, config) -> bool:
    hass.data.setdefault(DOMAIN, {})

    async def _link_for(name: str) -> PanelLink | None:
        want = topic_safe(name)
        for entry_data in hass.data[DOMAIN].values():
            link: PanelLink = entry_data["link"]
            if link.device_name == want:
                return link
        return None

    def _resolve(name: str) -> PanelLink:
        want = topic_safe(name)
        for entry_data in hass.data[DOMAIN].values():
            link = entry_data.get("link")
            if link is not None and link.device_name == want:
                return link
        raise ServiceValidationError(f"No Nexus panel named {name}")

    async def _command(call) -> None:
        link = await _link_for(call.data["device_name"])
        if link is None:
            _LOGGER.warning("no panel named %s", call.data["device_name"])
            return
        await link.async_command(call.data["command"], **(call.data.get("payload") or {}))

    async def _config_update(call) -> None:
        link = await _link_for(call.data["device_name"])
        if link is None:
            _LOGGER.warning("no panel named %s", call.data["device_name"])
            return
        await link.async_config_update(call.data["config"])
        # The RD proxy serves the stream from the server side, so it needs the
        # panel token/port here too. Persist them in the entry options (the
        # proxy reads them live; the options listener reload is harmless).
        patch = {
            k: call.data["config"][k]
            for k in ("remote_desktop_token", "remote_desktop_port", "dark_mode")
            if k in call.data["config"]
        }
        if patch:
            for entry in hass.config_entries.async_entries(DOMAIN):
                dev = topic_safe(entry.options.get(CONF_DEVICE_NAME, entry.data.get(CONF_DEVICE_NAME, "")))
                if dev == link.device_name:
                    hass.config_entries.async_update_entry(
                        entry, options={**entry.options, **patch}
                    )
                    break

    hass.services.async_register(DOMAIN, "command", _command,
                                 schema=SERVICE_COMMAND_SCHEMA)
    hass.services.async_register(DOMAIN, "config_update", _config_update,
                                 schema=SERVICE_CONFIG_UPDATE_SCHEMA)

    # ---- kiosk / presence services ---------------------------------------

    async def _speak(call) -> None:
        await _resolve(call.data["device_name"]).async_command(
            "tts:speak", text=call.data["message"])

    async def _toast(call) -> None:
        await _resolve(call.data["device_name"]).async_command(
            "notify:toast", message=call.data["message"])

    async def _show_camera(call) -> None:
        await _resolve(call.data["device_name"]).async_command(
            "camera:show", url=call.data["url"], title=call.data["title"],
            duration=call.data["duration"])

    async def _hide_camera(call) -> None:
        await _resolve(call.data["device_name"]).async_command("camera:hide")

    async def _navigate(call) -> None:
        await _resolve(call.data["device_name"]).async_command(
            "dashboard:navigate", path=call.data["path"])

    async def _refresh_dashboard(call) -> None:
        await _resolve(call.data["device_name"]).async_command("dashboard:refresh")

    async def _show_screensaver(call) -> None:
        await _resolve(call.data["device_name"]).async_command(
            "screensaver:show", mode=call.data.get("mode", ""))

    async def _hide_screensaver(call) -> None:
        await _resolve(call.data["device_name"]).async_command("screensaver:hide")

    async def _get_config(call) -> dict:
        link = _resolve(call.data["device_name"])
        return {"device_name": link.device_name, "config": await link.async_get_config()}

    async def _restore_config(call) -> None:
        link = _resolve(call.data["device_name"])
        # Masked placeholders ("<set>") must never overwrite a real secret.
        patch = {
            k: v for k, v in call.data["config"].items()
            if v is not None and v != "<set>"
        }
        await link.async_config_update(patch)

    hass.services.async_register(DOMAIN, "speak", _speak, schema=SERVICE_MESSAGE_SCHEMA)
    hass.services.async_register(DOMAIN, "toast", _toast, schema=SERVICE_MESSAGE_SCHEMA)
    hass.services.async_register(DOMAIN, "show_camera", _show_camera,
                                 schema=SERVICE_CAMERA_SCHEMA)
    hass.services.async_register(DOMAIN, "hide_camera", _hide_camera,
                                 schema=SERVICE_PANEL_SCHEMA)
    hass.services.async_register(DOMAIN, "navigate", _navigate,
                                 schema=SERVICE_NAVIGATE_SCHEMA)
    hass.services.async_register(DOMAIN, "refresh_dashboard", _refresh_dashboard,
                                 schema=SERVICE_PANEL_SCHEMA)
    hass.services.async_register(DOMAIN, "show_screensaver", _show_screensaver,
                                 schema=SERVICE_SCREENSAVER_SCHEMA)
    hass.services.async_register(DOMAIN, "hide_screensaver", _hide_screensaver,
                                 schema=SERVICE_PANEL_SCHEMA)
    hass.services.async_register(DOMAIN, "get_config", _get_config,
                                 schema=SERVICE_PANEL_SCHEMA,
                                 supports_response=SupportsResponse.OPTIONAL)
    hass.services.async_register(DOMAIN, "restore_config", _restore_config,
                                 schema=SERVICE_RESTORE_CONFIG_SCHEMA)

    try:
        rd_proxy.async_register(hass)
    except Exception:
        _LOGGER.exception("RD proxy registration failed — live stream falls back to direct panel URL")

    # register the sidebar last, and never let a frontend problem disable
    # the whole integration (entities keep working without it)
    try:
        await _register_sidebar(hass)
    except Exception:
        _LOGGER.exception("sidebar registration failed — integration continues")

    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    _LOGGER.info("setting up entry %s data=%s", entry.entry_id, dict(entry.data))
    # Migrate pre-0.4.35 unique_ids (device only) to prefix/device.
    # Never fail setup because of the migration: on newer HA the unique_id
    # change may be rejected — the entry keeps working with the old id.
    try:
        _dev = topic_safe(entry.options.get(CONF_DEVICE_NAME, entry.data.get(CONF_DEVICE_NAME, "nspanel")))
        _pfx = entry.options.get(CONF_TOPIC_PREFIX, entry.data.get(CONF_TOPIC_PREFIX, "nspanelpro"))
        if "/" not in (entry.unique_id or ""):
            hass.config_entries.async_update_entry(entry, unique_id=f"{_pfx}/{_dev}")
    except Exception:  # noqa: BLE001
        _LOGGER.warning("unique_id migration skipped for entry %s", entry.entry_id)
        _dev = topic_safe(entry.options.get(CONF_DEVICE_NAME, entry.data.get(CONF_DEVICE_NAME, "nspanel")))
        _pfx = entry.options.get(CONF_TOPIC_PREFIX, entry.data.get(CONF_TOPIC_PREFIX, "nspanelpro"))
    link = PanelLink(hass, _dev, _pfx)
    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = {"link": link}
    await link.async_start()
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    _LOGGER.info("entry %s forwarded to %s", entry.entry_id, PLATFORMS)
    try:
        entry.async_on_unload(entry.add_update_listener(async_update_options))
    except Exception:  # noqa: BLE001
        _LOGGER.warning("could not register options listener for entry %s", entry.entry_id)
    return True


async def async_update_options(hass: HomeAssistant, entry: ConfigEntry) -> None:
    """Reload the entry when options change."""
    await hass.config_entries.async_reload(entry.entry_id)


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unload = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    data = hass.data[DOMAIN].pop(entry.entry_id, None)
    if data:
        await data["link"].async_stop()
    return unload
