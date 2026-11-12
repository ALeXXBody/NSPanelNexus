"""Select entities: enumerated firmware settings (currently Zigbee role)."""
from __future__ import annotations

import logging

from homeassistant.components.select import SelectEntity
from homeassistant.core import callback
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusZigbeeRole(SelectEntity, NexusEntity):
    """Zigbee coordinator/router role (stored pref; gateway applies on restart)."""

    def __init__(self, link: PanelLink) -> None:
        NexusEntity.__init__(self, link, "config_zigbee_role", "Zigbee role")
        SelectEntity.__init__(self)
        self._attr_options = ["coordinator", "router"]
        self._attr_icon = "mdi:zigbee"
        self._attr_entity_category = EntityCategory.CONFIG
        self._attr_current_option = None
        self.entity_id = f"select.nspanel_{link.device_name}_zigbee_role"
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_config(self._on_config)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_config(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        if not isinstance(data, dict):
            return
        role = data.get("zigbee_role")
        if role in self._attr_options:
            self._attr_current_option = role
            self.async_write_ha_state()

    async def async_select_option(self, option: str) -> None:
        if option not in (self._attr_options or []):
            return
        await self._link.async_config_update({"zigbee_role": option})
        self._attr_current_option = option
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'select')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([NexusZigbeeRole(link)], False)
