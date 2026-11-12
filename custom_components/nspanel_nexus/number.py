"""Number entities: screen brightness."""
from __future__ import annotations

import logging

from homeassistant.components.number import NumberEntity, NumberMode
from homeassistant.core import callback
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
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
        self._unsub_cfg = await self._link.async_subscribe_config(self._on_config)
        await self._link.async_config_query(["brightness"])

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None
        unsub_cfg = getattr(self, "_unsub_cfg", None)
        if unsub_cfg:
            unsub_cfg()
            self._unsub_cfg = None

    @callback
    def _on_state(self, msg) -> None:
        value = self._link.parse_json(msg.payload).get("brightness")
        if value is not None:
            try:
                self._attr_native_value = int(float(value))
            except (TypeError, ValueError):
                return
            self.async_write_ha_state()

    @callback
    def _on_config(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        if isinstance(data, dict) and data.get("brightness") is not None:
            try:
                self._attr_native_value = int(float(data["brightness"]))
                self.async_write_ha_state()
            except (TypeError, ValueError):
                pass

    async def async_set_native_value(self, value: float) -> None:
        ivalue = int(value)
        await self._link.async_config_update({"brightness": ivalue})
        self._attr_native_value = ivalue
        self.async_write_ha_state()


# (config key, name, min, max, step, icon, unit)
CONFIG_NUMBERS: list[tuple[str, str, float, float, float, str | None, str | None]] = [
    ("mqtt_port", "MQTT port", 1, 65535, 1, "mdi:server-network", None),
    ("remote_desktop_port", "Remote desktop port", 1, 65535, 1, "mdi:monitor-share", None),
    ("light_below", "Light below threshold", 0, 100000, 1, "mdi:brightness-6", "lx"),
    ("light_above", "Light above threshold", 0, 100000, 1, "mdi:brightness-7", "lx"),
    ("proximity_delta", "Proximity delta", 0, 1000, 1, "mdi:motion-sensor", None),
    ("brightness_on_light_below", "Brightness when dark", -1, 255, 1, "mdi:brightness-4", None),
    ("brightness_on_light_above", "Brightness when bright", -1, 255, 1, "mdi:brightness-5", None),
]


class NexusConfigNumber(NumberEntity, NexusEntity):
    """One firmware numeric setting, synced via the panel's config topic."""

    def __init__(self, link: PanelLink, key: str, name: str, native_min: float,
                 native_max: float, step: float = 1,
                 icon: str | None = None, unit: str | None = None) -> None:
        NexusEntity.__init__(self, link, f"config_{key}", name)
        NumberEntity.__init__(self)
        self._key = key
        self._attr_native_min_value = native_min
        self._attr_native_max_value = native_max
        self._attr_native_step = step
        self._attr_mode = NumberMode.SLIDER
        self._attr_icon = icon
        self._attr_native_unit_of_measurement = unit
        self._attr_entity_category = EntityCategory.DIAGNOSTIC
        self._attr_native_value = None
        self.entity_id = f"number.nspanel_{link.device_name}_{key}"
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
        if not isinstance(data, dict) or self._key not in data:
            return
        try:
            self._attr_native_value = int(float(data[self._key]))
            self.async_write_ha_state()
        except (TypeError, ValueError):
            pass

    async def async_set_native_value(self, value: float) -> None:
        ivalue = int(value)
        await self._link.async_config_update({self._key: ivalue})
        self._attr_native_value = ivalue
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'number')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities(
        [NexusBrightness(link)] + [
            NexusConfigNumber(link, key, name, lo, hi, step, icon, unit)
            for key, name, lo, hi, step, icon, unit in CONFIG_NUMBERS
        ],
        False,
    )
