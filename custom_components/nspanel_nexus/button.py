"""Button entities: reboot, wake, sleep, firmware check."""
from __future__ import annotations

import logging

from homeassistant.components.button import ButtonEntity

from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusButton(ButtonEntity, NexusEntity):
    def __init__(self, link: PanelLink, object_id: str, name: str,
                 command: str, icon: str | None = None,
                 entity_category: str | None = None) -> None:
        NexusEntity.__init__(self, link, object_id, name)
        ButtonEntity.__init__(self)
        self._command = command
        self._attr_icon = icon
        self._attr_entity_category = entity_category

    async def async_press(self) -> None:
        await self._link.async_command(self._command)


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    link = hass.data["nspanel_nexus"][entry.entry_id]["link"]
    async_add_entities([
        NexusButton(link, "wake", "Wake", "wake_up", icon="mdi:screen-rotation",
                    entity_category="config"),
        NexusButton(link, "sleep", "Sleep", "sleep", icon="mdi:sleep",
                    entity_category="config"),
        NexusButton(link, "reboot", "Reboot", "reboot", icon="mdi:restart",
                    entity_category="config"),
        NexusButton(link, "firmware_check", "Check firmware", "firmware:check",
                    icon="mdi:cloud-download", entity_category="diagnostic"),
        NexusButton(link, "query_config", "Re-read config", "query",
                    icon="mdi:cloud-question", entity_category="diagnostic"),
    ], False)
