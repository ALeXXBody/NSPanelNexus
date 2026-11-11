"""MQTT link to one NSPanel Nexus panel."""
from __future__ import annotations

import json
import logging
from collections.abc import Callable
from typing import Any

from homeassistant.components import mqtt
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.json import json_dumps

from .const import DOMAIN

_LOGGER = logging.getLogger(__name__)


class PanelLink:
    """One connection to one panel: subscriptions + command helpers."""

    def __init__(self, hass: HomeAssistant, device_name: str, topic_prefix: str) -> None:
        self.hass = hass
        self.device_name = device_name
        self.topic_prefix = topic_prefix
        self.base = f"{topic_prefix}/{device_name}"
        self.available = True
        self._listeners: set[Callable[[], None]] = set()
        self._unsubs: list[Callable[[], None]] = []

    @property
    def device_info(self) -> DeviceInfo:
        return DeviceInfo(
            identifiers={(DOMAIN, self.device_name)},
            name=f"NSPanel {self.device_name}",
            manufacturer="Sonoff",
            model="NSPanel Pro (Nexus firmware)",
            sw_version="Nexus",
        )

    # ---- lifecycle -------------------------------------------------------

    async def async_start(self) -> None:
        self._unsubs.append(
            await mqtt.async_subscribe(self.hass, f"{self.base}/status", self._on_status, 0)
        )

    async def async_stop(self) -> None:
        for unsub in self._unsubs:
            unsub()
        self._unsubs.clear()
        self._listeners.clear()

    @callback
    def _on_status(self, msg) -> None:
        was = self.available
        self.available = msg.payload == "online"
        if was != self.available:
            for listener in list(self._listeners):
                listener()

    def register_listener(self, listener: Callable[[], None]) -> Callable[[], None]:
        self._listeners.add(listener)
        return lambda: self._listeners.discard(listener)

    # ---- subscriptions ---------------------------------------------------

    async def async_subscribe_state(self, key: str, cb: Callable[[str], None], qos: int = 0):
        return await mqtt.async_subscribe(self.hass, f"{self.base}/state/{key}", cb, qos)

    async def async_subscribe_event(self, kind: str, cb: Callable[[str], None], qos: int = 0):
        return await mqtt.async_subscribe(self.hass, f"{self.base}/event/{kind}", cb, qos)

    # ---- publishing ------------------------------------------------------

    async def async_command(self, command: str, **payload: Any) -> None:
        await mqtt.async_publish(
            self.hass, f"{self.base}/command/device",
            json_dumps({"command": command, **payload}), 0, False,
        )

    async def async_config_update(self, patch: dict[str, Any]) -> None:
        await mqtt.async_publish(
            self.hass, f"{self.base}/config/update", json_dumps(patch), 0, False,
        )

    async def async_config_query(self, keys: list[str] | None = None) -> None:
        payload = {} if not keys else {k: None for k in keys}
        await mqtt.async_publish(self.hass, f"{self.base}/config/query", json_dumps(payload), 0, False)

    def parse_json(self, payload: str) -> Any:
        try:
            return json.loads(payload)
        except (ValueError, TypeError):
            return {}
