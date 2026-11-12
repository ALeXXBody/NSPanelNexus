"""Binary sensor: presence derived from the proximity event."""
from __future__ import annotations

import logging
from datetime import timedelta

from homeassistant.components.binary_sensor import (
    BinarySensorDeviceClass,
    BinarySensorEntity,
)
from homeassistant.core import callback
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.util import dt as dt_util

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusPresence(BinarySensorEntity, NexusEntity):
    """Presence at the panel: on while proximity keeps triggering."""

    def __init__(self, link: PanelLink, timeout: int = 60) -> None:
        NexusEntity.__init__(self, link, "presence", "Presence")
        BinarySensorEntity.__init__(self)
        self._timeout = timedelta(seconds=timeout)
        self._attr_device_class = BinarySensorDeviceClass.OCCUPANCY
        self._attr_is_on = None
        self._last_trigger = None
        self._unsub = None
        self._expire_unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_event("proximity", self._on_event)

        @callback
        def _expire(now) -> None:
            if (self._last_trigger and
                    dt_util.utcnow() - self._last_trigger > self._timeout):
                self._attr_is_on = False
                self.async_write_ha_state()

        self.async_on_remove(
            async_track_time_interval(self.hass, _expire, self._timeout))
        self.async_on_remove(self._unsub)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_event(self, msg) -> None:
        self._last_trigger = dt_util.utcnow()
        self._attr_is_on = True
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'binary_sensor')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([NexusPresence(link)], False)
