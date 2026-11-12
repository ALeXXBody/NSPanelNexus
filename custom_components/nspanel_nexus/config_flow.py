"""Config flow: add a Nexus panel (LAN discovery, mDNS or by name)."""
from __future__ import annotations

import asyncio
import json
import logging
import re
import socket
from typing import Any

import voluptuous as vol
from homeassistant.config_entries import ConfigFlow, OptionsFlow
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


def topic_safe(name: str) -> str:
    """Same sanitisation the panel app uses for its MQTT topic base."""
    return re.sub(r"[^A-Za-z0-9_-]+", "_", name).strip("_ ") or "nspanel"


async def _discover_udp() -> list[dict[str, Any]]:
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


def _schema(panels: list[dict[str, Any]]):
    options: dict[Any, Any] = {
        vol.Optional(CONF_DEVICE_NAME, default=DEFAULT_DEVICE_NAME): str,
        vol.Optional(CONF_TOPIC_PREFIX, default=DEFAULT_TOPIC_PREFIX): str,
    }
    if panels:
        names = [p.get("name") for p in panels if p.get("name")]
        if names:
            options[vol.Optional(CONF_DEVICE_NAME, default=names[0])] = vol.In(names)
    return vol.Schema(options)


def _format(panels: list[dict[str, Any]]) -> str:
    if not panels:
        return "No panels answered on the LAN. Enter the panel name shown in the Nexus app."
    return "\n".join(f"• {p.get('name')}  ({p.get('_ip')})" for p in panels)


class NexusConfigFlow(ConfigFlow, domain=DOMAIN):
    """Handle the NSPanel Nexus config flow."""

    VERSION = 1

    def __init__(self) -> None:
        self._discovered: dict[str, Any] = {}

    # ---- mDNS (HA shows "New device discovered") -------------------------

    async def async_step_zeroconf(self, discovery_info) -> FlowResult:
        props = {}
        for k, v in getattr(discovery_info, "properties", {}).items():
            key = k.decode() if isinstance(k, bytes) else k
            val = v.decode() if isinstance(v, bytes) else v
            props[key] = val
        name = props.get("name") or (discovery_info.name or "").split(".")[0]
        topic = props.get("topic") or ""
        prefix = topic.split("/")[0] if topic else DEFAULT_TOPIC_PREFIX
        device = topic.split("/", 1)[1] if "/" in topic else topic_safe(name)

        await self.async_set_unique_id(device)
        self._abort_if_unique_id_configured()
        self._discovered = {
            CONF_DEVICE_NAME: device,
            CONF_TOPIC_PREFIX: prefix,
            "friendly": name,
        }
        self.context["title_placeholders"] = {"name": name}
        return await self.async_step_zeroconf_confirm()

    async def async_step_zeroconf_confirm(
        self, user_input: dict[str, Any] | None = None
    ) -> FlowResult:
        if user_input is not None:
            device = topic_safe(user_input[CONF_DEVICE_NAME])
            return self.async_create_entry(
                title=f"NSPanel {device}",
                data={
                    CONF_DEVICE_NAME: device,
                    CONF_TOPIC_PREFIX: user_input.get(CONF_TOPIC_PREFIX, DEFAULT_TOPIC_PREFIX),
                },
            )
        return self.async_show_form(
            step_id="zeroconf_confirm",
            data_schema=vol.Schema({
                vol.Required(CONF_DEVICE_NAME, default=self._discovered[CONF_DEVICE_NAME]): str,
                vol.Required(CONF_TOPIC_PREFIX, default=self._discovered[CONF_TOPIC_PREFIX]): str,
            }),
            description_placeholders={"name": self._discovered.get("friendly", "")},
        )

    # ---- manual add ------------------------------------------------------

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> FlowResult:
        errors: dict[str, str] = {}
        if user_input is not None:
            device = topic_safe(user_input[CONF_DEVICE_NAME])
            await self.async_set_unique_id(device)
            self._abort_if_unique_id_configured()
            return self.async_create_entry(
                title=f"NSPanel {device}",
                data={
                    CONF_DEVICE_NAME: device,
                    CONF_TOPIC_PREFIX: user_input.get(CONF_TOPIC_PREFIX, DEFAULT_TOPIC_PREFIX),
                },
            )
        panels = await _discover_udp()
        names = [p.get("name") for p in panels if p.get("name")]
        default = names[0] if names else DEFAULT_DEVICE_NAME
        return self.async_show_form(
            step_id="user",
            data_schema=vol.Schema({
                # free text: typed names always work, even when LAN discovery
                # answered — the discovered list goes in the description
                vol.Required(CONF_DEVICE_NAME, default=default): str,
                vol.Required(CONF_TOPIC_PREFIX, default=DEFAULT_TOPIC_PREFIX): str,
            }),
            errors=errors,
            description_placeholders={"panels": _format(panels)},
        )

    @staticmethod
    @callback
    def async_get_options_flow(config_entry):
        return NexusOptionsFlow(config_entry)


class NexusOptionsFlow(OptionsFlow):
    """Allow changing the topic prefix later."""

    def __init__(self, config_entry) -> None:
        self.config_entry = config_entry

    async def async_step_init(self, user_input: dict[str, Any] | None = None) -> FlowResult:
        if user_input is not None:
            user_input = dict(user_input)
            user_input[CONF_DEVICE_NAME] = topic_safe(user_input[CONF_DEVICE_NAME])
            return self.async_create_entry(title="", data=user_input)
        return self.async_show_form(
            step_id="init",
            data_schema=vol.Schema({
                vol.Required(CONF_DEVICE_NAME, default=self.config_entry.data[CONF_DEVICE_NAME]): str,
                vol.Required(CONF_TOPIC_PREFIX, default=self.config_entry.data.get(CONF_TOPIC_PREFIX, DEFAULT_TOPIC_PREFIX)): str,
            }),
        )
