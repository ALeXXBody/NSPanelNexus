"""NSPanel Nexus — sidebar panel + manager services."""
from __future__ import annotations

import logging
from pathlib import Path

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import CONF_DEVICE_NAME, CONF_TOPIC_PREFIX, DOMAIN, PLATFORMS
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)

PANEL_URL = "/nspanel-nexus"

SERVICE_COMMAND_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("command"): str,
})

SERVICE_CONFIG_UPDATE_SCHEMA = vol.Schema({
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

    # serve the panel script + brand images
    brand_path = Path(__file__).parent / "www" / "brand"
    await hass.http.async_register_static_paths([
        StaticPathConfig(js_url, str(js_path), False),
        StaticPathConfig(PANEL_URL + "/brand", str(brand_path), True),
    ])

    await panel_custom.async_register_panel(
        hass,
        frontend_url_path=PANEL_URL.lstrip("/"),
        webcomponent_name="nspanel-nexus-manager",
        sidebar_title="NSPanel Nexus",
        sidebar_icon="mdi:tablet-dashboard",
        js_url=js_url,
        config={"domain": DOMAIN, "title": "NSPanel Nexus"},
        require_admin=True,
    )


async def async_setup(hass: HomeAssistant, config) -> bool:
    hass.data.setdefault(DOMAIN, {})

    async def _link_for(name: str) -> PanelLink | None:
        for entry_data in hass.data[DOMAIN].values():
            link: PanelLink = entry_data["link"]
            if link.device_name == name:
                return link
        return None

    async def _command(call) -> None:
        link = await _link_for(call.data["device_name"])
        if link is None:
            _LOGGER.warning("no panel named %s", call.data["device_name"])
            return
        await link.async_command(call.data["command"])

    async def _config_update(call) -> None:
        link = await _link_for(call.data["device_name"])
        if link is None:
            _LOGGER.warning("no panel named %s", call.data["device_name"])
            return
        await link.async_config_update(call.data["config"])

    hass.services.async_register(DOMAIN, "command", _command,
                                 schema=SERVICE_COMMAND_SCHEMA)
    hass.services.async_register(DOMAIN, "config_update", _config_update,
                                 schema=SERVICE_CONFIG_UPDATE_SCHEMA)

    # register the sidebar last, and never let a frontend problem disable
    # the whole integration (entities keep working without it)
    try:
        await _register_sidebar(hass)
    except Exception:
        _LOGGER.exception("sidebar registration failed — integration continues")

    return True


async def async_setup_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    link = PanelLink(
        hass,
        entry.data[CONF_DEVICE_NAME],
        entry.data.get(CONF_TOPIC_PREFIX, "nspanelpro"),
    )
    hass.data.setdefault(DOMAIN, {})[entry.entry_id] = {"link": link}
    await link.async_start()
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: ConfigEntry) -> bool:
    unload = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    data = hass.data[DOMAIN].pop(entry.entry_id, None)
    if data:
        await data["link"].async_stop()
    return unload
