"""Sensor entities: illuminance, ip address, uptime, firmware."""
from __future__ import annotations

import logging

from homeassistant.components.sensor import SensorDeviceClass, SensorEntity, SensorStateClass
from homeassistant.core import callback

try:  # HA >= 2022.10, and still present on most versions
    from homeassistant.const import UnitOfLuminance, UnitOfTime

    _UNIT_LUX = UnitOfLuminance.LUX
    _UNIT_SECONDS = UnitOfTime.SECONDS
except ImportError:  # older/newer HA without the enums in const: use plain units
    _UNIT_LUX = "lx"
    _UNIT_SECONDS = "s"
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
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
        entity_category: EntityCategory | None = None,
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
        if self._key == "firmware" and isinstance(data, dict):
            app_ver = data.get("app")
            avail = data.get("available")
            err = data.get("error")
            attrs: dict[str, object] = {}
            if app_ver not in (None, ""):
                attrs["app_version"] = app_ver
            if avail not in (None, ""):
                attrs["available_version"] = avail
            if err not in (None, ""):
                attrs["update_error"] = err
            self._attr_extra_state_attributes = attrs
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'sensor')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([
        NexusSensor(link, "illuminance", "Illuminance",
                    device_class=SensorDeviceClass.ILLUMINANCE,
                    unit=_UNIT_LUX, state_class=SensorStateClass.MEASUREMENT),
        NexusSensor(link, "ip_address", "IP address",
                    icon="mdi:ip-network", entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "uptime", "Uptime",
                    unit=_UNIT_SECONDS,
                    state_class=SensorStateClass.TOTAL_INCREASING,
                    icon="mdi:clock-outline", entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "firmware", "Firmware",
                    icon="mdi:memory", entity_category=EntityCategory.DIAGNOSTIC,
                    json_key="current"),
        # ---- kiosk state -------------------------------------------------
        NexusSensor(link, "kiosk", "Kiosk mode",
                    icon="mdi:tablet-dashboard",
                    entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "screensaver", "Screensaver",
                    icon="mdi:weather-night",
                    entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "day_night", "Day/night",
                    icon="mdi:theme-light-dark",
                    entity_category=EntityCategory.DIAGNOSTIC,
                    json_key="period"),
        # ---- panel health ------------------------------------------------
        NexusSensor(link, "wifi", "Wi-Fi signal",
                    device_class=SensorDeviceClass.SIGNAL_STRENGTH,
                    unit="dBm", state_class=SensorStateClass.MEASUREMENT,
                    json_key="rssi", icon="mdi:wifi",
                    entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "storage", "Storage free",
                    unit="%", state_class=SensorStateClass.MEASUREMENT,
                    json_key="free_pct", icon="mdi:harddisk",
                    entity_category=EntityCategory.DIAGNOSTIC),
        NexusSensor(link, "ram", "RAM free",
                    unit="%", state_class=SensorStateClass.MEASUREMENT,
                    json_key="free_pct", icon="mdi:memory",
                    entity_category=EntityCategory.DIAGNOSTIC),
        # ---- Zigbee ------------------------------------------------------
        NexusSensor(link, "zigbee", "Zigbee permit join",
                    unit=_UNIT_SECONDS, icon="mdi:zigbee",
                    json_key="permit_join",
                    entity_category=EntityCategory.DIAGNOSTIC),
    ], False)
