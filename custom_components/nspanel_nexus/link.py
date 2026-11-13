"""MQTT link to one NSPanel Nexus panel."""
from __future__ import annotations

import asyncio
import json
import logging
from collections.abc import Callable
from typing import Any

from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceInfo

from .const import DOMAIN, topic_safe

_LOGGER = logging.getLogger(__name__)


class PanelLink:
    """One connection to one panel: subscriptions + command helpers."""

    def __init__(self, hass: HomeAssistant, device_name: str, topic_prefix: str) -> None:
        self.hass = hass
        self.device_name = topic_safe(device_name)
        self.topic_prefix = topic_prefix
        self.base = f"{topic_prefix}/{self.device_name}"
        self.available = True
        self._listeners: set[Callable[[], None]] = set()
        self._unsubs: list[Callable[[], None]] = []
        # last known config (secrets already masked by the panel)
        self.config: dict[str, Any] = {}
        self._config_waiters: list[asyncio.Event] = []

    @property
    def device_info(self) -> DeviceInfo:
        return DeviceInfo(
            identifiers={(DOMAIN, f"{self.topic_prefix}/{self.device_name}")},
            name=f"NSPanel {self.device_name}",
            manufacturer="Sonoff",
            model="NSPanel Pro (Nexus firmware)",
            sw_version="Nexus",
        )

    # ---- lifecycle -------------------------------------------------------

    @property
    def mqtt(self):
        from homeassistant.components import mqtt

        return mqtt

    async def async_start(self) -> None:
        self._unsubs.append(
            await self.mqtt.async_subscribe(self.hass, f"{self.base}/status", self._on_status, 0)
        )
        # config answers (retained, so this also primes `async_get_config`)
        self._unsubs.append(
            await self.async_subscribe_config(self._on_config, 0)
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

    # ---- config cache ----------------------------------------------------

    @callback
    def _on_config(self, msg) -> None:
        """Merge a config answer (full object or patch) and wake waiters."""
        payload = self.parse_json(msg.payload)
        if not isinstance(payload, dict):
            return
        # The panel answers config/query with either {"config": {...}} or a
        # bare object depending on the app version — accept both.
        inner = payload.get("config")
        if isinstance(inner, dict):
            payload = inner
        self.config.update(payload)
        waiters, self._config_waiters = self._config_waiters, []
        for event in waiters:
            event.set()

    async def async_get_config(self, timeout: float = 5.0) -> dict[str, Any]:
        """Return the last known config, asking the panel once if we have none."""
        if self.config:
            return dict(self.config)
        event = asyncio.Event()
        self._config_waiters.append(event)
        try:
            await self.async_config_query()
        except Exception:  # noqa: BLE001
            _LOGGER.debug("config query failed for %s", self.device_name)
        try:
            await asyncio.wait_for(event.wait(), timeout)
        except TimeoutError:
            _LOGGER.debug("config answer timed out for %s", self.device_name)
        finally:
            if event in self._config_waiters:
                self._config_waiters.remove(event)
        return dict(self.config)

    # ---- subscriptions ---------------------------------------------------

    async def async_subscribe_state(self, key: str, cb: Callable[[str], None], qos: int = 0):
        return await self.mqtt.async_subscribe(self.hass, f"{self.base}/state/{key}", cb, qos)

    async def async_subscribe_event(self, kind: str, cb: Callable[[str], None], qos: int = 0):
        return await self.mqtt.async_subscribe(self.hass, f"{self.base}/event/{kind}", cb, qos)

    async def async_subscribe_config(self, cb: Callable[[str], None], qos: int = 0):
        return await self.mqtt.async_subscribe(self.hass, f"{self.base}/config", cb, qos)

    # ---- publishing ------------------------------------------------------

    async def async_command(self, command: str, **payload: Any) -> None:
        await self.mqtt.async_publish(
            self.hass, f"{self.base}/command/device",
            json.dumps({"command": command, **payload}), 0, False,
        )

    async def async_config_update(self, patch: dict[str, Any]) -> None:
        await self.mqtt.async_publish(
            self.hass, f"{self.base}/config/update", json.dumps(patch), 0, False,
        )

    async def async_config_query(self, keys: list[str] | None = None) -> None:
        payload = {} if not keys else {k: None for k in keys}
        await self.mqtt.async_publish(self.hass, f"{self.base}/config/query", json.dumps(payload), 0, False)

    def parse_json(self, payload: str) -> Any:
        try:
            return json.loads(payload)
        except (ValueError, TypeError):
            return {}
