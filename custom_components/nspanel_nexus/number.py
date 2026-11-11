"""Number entities: screen brightness."""
from __future__ import annotations

import logging

from homeassistant.components.number import NumberEntity, NumberMode
from homeassistant.core import callback

from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusBrightness(NumberEntity, NexusEntity):
    """Panel screen brightness, 0–255."""

    def __init__(self, link: PanelLink) -> None:
        NexusEntity.__init__(self, link, "brightness", "Brightness")
        NumberEntity.__init__(self)
        self._attr_native_min_value = 0
        self._attr_native_max_value = 255
        self._attr_native_step = 1
        self._attr_mode = NumberMode.SLIDER
        self._attr_icon = "mdi:brightness-6"
        self._attr_native_value = None
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_state("brightness", self._on_state)
        await self._link.async_config_query(["brightness"])

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_state(self, msg) -> None:
        value = self._link.parse_json(msg.payload).get("brightness")
        if value is not None:
            self._attr_native_value = float(value)
            self.async_write_ha_state()

    async def async_set_native_value(self, value: float) -> None:
        await self._link.async_config_update({"brightness": int(value)})
        self._attr_native_value = value
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    link = hass.data["nspanel_nexus"][entry.entry_id]["link"]
    async_add_entities([NexusBrightness(link)], False)
