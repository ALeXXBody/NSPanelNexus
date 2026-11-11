"""Sensor entities: illuminance, ip address, uptime, firmware."""
from __future__ import annotations

import logging

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity, SensorStateClass
from homeassistant.const import LIGHT_LUX
from homeassistant.core import callback
from homeassistant.helpers.entity import EntityCategory

from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusSensor(SensorEntity, NexusEntity):
    """A value the panel publishes on <base>/state/<key>."""

    def __init__(
        self, link: PanelLink, key: str, name: str,
        device_class: SensorDeviceClass | None = None,
        unit: str | None = None,
        state_class: SensorStateClass | None = None,
        json_key: str | None = None,
        icon: str | None = None,
        entity_category: str | None = None,
    ) -> None:
        NexusEntity.__init__(self, link, f"sensor_{key}", name)
        SensorEntity.__init__(self)
        self._key = key
        self._json_key = json_key or key
        self._attr_native_unit_of_measurement = unit
        self._attr_device_class = device_class
        self._attr_state_class = state_class
        self._attr_icon = icon
        self._attr_entity_category = entity_category
        self._attr_native_value = None
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_state(self._key, self._on_state)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_state(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        value = data.get(self._json_key) if isinstance(data, dict) else None
        if value is None:
            value = msg.payload
        self._attr_native_value = value
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'sensor')
    link = hass.data["nspanel_nexus"][entry.entry_id]["link"]
    async_add_entities([
        NexusSensor(link, "illuminance", "Illuminance",
                    device_class=SensorDeviceClass.ILLUMINANCE,
                    unit=LIGHT_LUX, state_class=SensorStateClass.MEASUREMENT),
        NexusSensor(link, "ip_address", "IP address",
                    icon="mdi:ip-network", entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "uptime", "Uptime",
                    unit="s",
                    state_class=SensorStateClass.TOTAL_INCREASING,
                    icon="mdi:clock-outline", entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "firmware", "Firmware",
                    icon="mdi:memory", entity_category=EntityCategory.DIAGNOSTIC,
                    json_key="current"),
    ], False)
