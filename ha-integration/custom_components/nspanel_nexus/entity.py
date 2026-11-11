"""Entity base for NSPanel Nexus."""
from __future__ import annotations

from homeassistant.core import callback
from homeassistant.helpers.entity import Entity

from .link import PanelLink


class NexusEntity(Entity):
    """Base: device info, availability from the panel's status topic."""

    _attr_has_entity_name = True
    _attr_should_poll = False

    def __init__(self, link: PanelLink, object_id: str, name: str) -> None:
        self._link = link
        self._attr_name = name
        self._attr_unique_id = f"nexus_{link.device_name}_{object_id}"
        self._attr_device_info = link.device_info
        self._attr_available = link.available

    async def async_added_to_hass(self) -> None:
        self.async_on_remove(self._link.register_listener(self._refresh_availability))

    @callback
    def _refresh_availability(self) -> None:
        self._attr_available = self._link.available
        self.async_write_ha_state()
