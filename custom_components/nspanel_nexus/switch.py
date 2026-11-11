"""Switch entities: screen (wake/sleep) and remote desktop."""
from __future__ import annotations

import logging

from homeassistant.components.switch import SwitchDeviceClass, SwitchEntity
from homeassistant.core import callback

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

    async def async_turn_on(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: True})
        self._attr_is_on = True
        self.async_write_ha_state()

    async def async_turn_off(self, **kwargs) -> None:
        await self._link.async_config_update({self._key: False})
        self._attr_is_on = False
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    link = hass.data["nspanel_nexus"][entry.entry_id]["link"]
    async_add_entities([
        NexusScreenSwitch(link),
        NexusModuleSwitch(link, "remote_desktop", "Remote desktop",
                          icon="mdi:monitor-share"),
        NexusModuleSwitch(link, "voice", "Voice satellite",
                          icon="mdi:microphone-message"),
        NexusModuleSwitch(link, "zigbee", "Zigbee", icon="mdi:zigbee"),
    ], False)
