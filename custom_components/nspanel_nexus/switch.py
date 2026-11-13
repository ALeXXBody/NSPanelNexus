"""Switch entities: screen (wake/sleep) and remote desktop."""
from __future__ import annotations

import logging

from homeassistant.components.switch import SwitchDeviceClass, SwitchEntity
from homeassistant.core import callback
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusScreenSwitch(SwitchEntity, NexusEntity):
    """On = panel awake, off = panel asleep."""

    def __init__(self, link: PanelLink) -> None:
        NexusEntity.__init__(self, link, "screen", "Screen")
        SwitchEntity.__init__(self)
        self._attr_device_class = SwitchDeviceClass.SWITCH
        self._attr_is_on = None
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_state("screen", self._on_state)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_state(self, msg) -> None:
        self._attr_is_on = self._link.parse_json(msg.payload).get("screen") == "on"
        self.async_write_ha_state()

    async def async_turn_on(self, **kwargs) -> None:
        await self._link.async_command("wake_up")
        self._attr_is_on = True
        self.async_write_ha_state()

    async def async_turn_off(self, **kwargs) -> None:
        await self._link.async_command("sleep")
        self._attr_is_on = False
        self.async_write_ha_state()


class NexusModuleSwitch(SwitchEntity, NexusEntity):
    """Turn one panel module on or off remotely."""

    def __init__(self, link: PanelLink, module: str, name: str, icon: str | None = None) -> None:
        NexusEntity.__init__(self, link, f"module_{module}", name)
        SwitchEntity.__init__(self)
        self._module = module
        self._key = f"module.{module}.enabled"
        self._attr_icon = icon
        self._attr_is_on = None
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_state("modules", self._on_modules)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_modules(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        if isinstance(data, dict) and self._module in data:
            self._attr_is_on = bool(data[self._module])
            self.async_write_ha_state()

    async def async_turn_on(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: True})
        self._attr_is_on = True
        self.async_write_ha_state()

    async def async_turn_off(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: False})
        self._attr_is_on = False
        self.async_write_ha_state()


class NexusConfigSwitch(SwitchEntity, NexusEntity):
    """One boolean firmware setting, synced via the config topic."""

    def __init__(self, link: PanelLink, key: str, name: str,
                 icon: str | None = None) -> None:
        NexusEntity.__init__(self, link, f"config_{key}", name)
        SwitchEntity.__init__(self)
        self._key = key
        self._attr_icon = icon
        self._attr_entity_category = EntityCategory.CONFIG
        self._attr_is_on = None
        self.entity_id = f"switch.nspanel_{link.device_name}_{key}"
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
        if isinstance(data, dict) and self._key in data:
            value = data[self._key]
            self._attr_is_on = (
                value if isinstance(value, bool)
                else str(value).lower() in ("1", "true", "on", "yes")
            )
            self.async_write_ha_state()

    async def async_turn_on(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: True})
        self._attr_is_on = True
        self.async_write_ha_state()

    async def async_turn_off(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: False})
        self._attr_is_on = False
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'switch')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([
        NexusScreenSwitch(link),
        NexusConfigSwitch(link, "screensaver_enabled", "Screensaver",
                          icon="mdi:weather-night"),
        NexusModuleSwitch(link, "remote_desktop", "Remote desktop",
                          icon="mdi:monitor-share"),
        NexusModuleSwitch(link, "voice", "Voice satellite",
                          icon="mdi:microphone-message"),
        NexusModuleSwitch(link, "zigbee", "Zigbee", icon="mdi:zigbee"),
        # NOTE: no switch for the mqtt module on purpose — turning it off
        # remotely would silence the panel until someone re-enables it on
        # the touchscreen.
        NexusModuleSwitch(link, "sensors", "Sensors",
                          icon="mdi:motion-sensor"),
        NexusModuleSwitch(link, "display", "Display",
                          icon="mdi:monitor"),
        NexusModuleSwitch(link, "dashboard", "Dashboard",
                          icon="mdi:view-dashboard"),
        NexusModuleSwitch(link, "firmware", "Firmware",
                          icon="mdi:cloud-download"),
        NexusModuleSwitch(link, "fleet", "Fleet",
                          icon="mdi:radar"),
    ], False)
