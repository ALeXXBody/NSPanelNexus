"""NSPanel Nexus — Home Assistant integration."""
from __future__ import annotations

import logging

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant

from .const import CONF_DEVICE_NAME, CONF_TOPIC_PREFIX, DOMAIN
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)

PLATFORMS = ["event", "sensor", "binary_sensor", "switch", "number", "button"]

SERVICE_COMMAND_SCHEMA = vol.Schema({
    vol.Required("device_name"): str,
    vol.Required("command"): str,
})


async def async_setup(hass, config) -> bool:
    hass.data.setdefault(DOMAIN, {})

    async def _command(call):
        name = call.data["device_name"]
        command = call.data["command"]
        for entry_data in hass.data[DOMAIN].values():
            link: PanelLink = entry_data["link"]
            if link.device_name == name:
                await link.async_command(command)
                return
        _LOGGER.warning("no panel named %s", name)

    hass.services.async_register(DOMAIN, "command", _command, schema=SERVICE_COMMAND_SCHEMA)
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
