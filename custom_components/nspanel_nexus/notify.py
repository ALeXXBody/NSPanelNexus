"""Notify entity: send messages to the panel's on-screen toast overlay."""
from __future__ import annotations

import logging

from homeassistant.components.notify import NotifyEntity

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusNotify(NotifyEntity, NexusEntity):
    """`notify.<panel>` shows a toast on the panel."""

    def __init__(self, link: PanelLink) -> None:
        NexusEntity.__init__(self, link, "notify", "Notification")
        NotifyEntity.__init__(self)
        self._attr_icon = "mdi:message-flash"

    async def async_send_message(self, message: str, title: str | None = None) -> None:
        text = f"{title}: {message}" if title else message
        await self._link.async_command("notify:toast", message=text)


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([NexusNotify(link)], False)
