"""Config flow: add a Nexus panel by name and topic prefix (with LAN discovery)."""
from __future__ import annotations

import asyncio
import json
import logging
import socket
from typing import Any

from homeassistant import config_entries
from homeassistant.core import callback
from homeassistant.data_entry_flow import FlowResult

from .const import (
    CONF_DEVICE_NAME,
    CONF_TOPIC_PREFIX,
    DEFAULT_DEVICE_NAME,
    DEFAULT_TOPIC_PREFIX,
    DOMAIN,
)

_LOGGER = logging.getLogger(__name__)

DISCOVERY_PORT = 5000
DISCOVERY_TIMEOUT = 2.0


async def _discover_panels() -> list[dict[str, Any]]:
    """Ask the LAN for Nexus panels (FleetModule replies on UDP 5000)."""
    loop = asyncio.get_running_loop()

    def _do() -> list[dict[str, Any]]:
        found: dict[str, dict[str, Any]] = {}
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.settimeout(DISCOVERY_TIMEOUT)
                s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
                s.sendto(b"nexus-discover", ("255.255.255.255", DISCOVERY_PORT))
                while True:
                    try:
                        data, addr = s.recvfrom(4096)
                        info = json.loads(data.decode())
                        name = info.get("name") or addr[0]
                        info["_ip"] = addr[0]
                        found[name] = info
                    except (TimeoutError, ValueError):
                        break
        except OSError:
            pass
        return list(found.values())

    try:
        return await loop.run_in_executor(None, _do)
    except OSError:
        return []


class NexusConfigFlow(config_entries.ConfigFlow, domain=DOMAIN):
    """Handle the NSPanel Nexus config flow."""

    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> FlowResult:
        errors: dict[str, str] = {}

        if user_input is not None:
            device = user_input[CONF_DEVICE_NAME].strip()
            await self.async_set_unique_id(device)
            self._abort_if_unique_id_configured()
            return self.async_create_entry(
                title=f"NSPanel {device}",
                data={
                    CONF_DEVICE_NAME: device,
                    CONF_TOPIC_PREFIX: user_input.get(CONF_TOPIC_PREFIX, DEFAULT_TOPIC_PREFIX),
                },
            )

        panels = await _discover_panels()
        return self.async_show_form(
            step_id="user",
            data_schema=_schema(panels),
            errors=errors,
            description_placeholders={"panels": _format(panels)},
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return NexusOptionsFlow(config_entry)


class NexusOptionsFlow(config_entries.OptionsFlow):
    """Allow changing the topic prefix later."""

    def __init__(self, config_entry) -> None:
        self.config_entry = config_entry

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> FlowResult:
        if user_input is not None:
            return self.async_create_entry(title="", data=user_input)
        return self.async_show_form(step_id="init", data_schema=_schema([]))


def _schema(panels: list[dict[str, Any]]):
    import voluptuous as vol

    suggestions = {
        vol.Optional(CONF_DEVICE_NAME, default=DEFAULT_DEVICE_NAME): str,
        vol.Optional(CONF_TOPIC_PREFIX, default=DEFAULT_TOPIC_PREFIX): str,
    }
    if panels:
        names = [p.get("name") for p in panels if p.get("name")]
        if names:
            suggestions[vol.Optional(CONF_DEVICE_NAME, default=names[0])] = vol.In(names)
    return vol.Schema(suggestions)


def _format(panels: list[dict[str, Any]]) -> str:
    if not panels:
        return "No panels answered on the LAN. Enter the panel name shown in the Nexus app."
    return "\n".join(
        f"• {p.get('name')}  ({p.get('_ip')})" for p in panels
    )
