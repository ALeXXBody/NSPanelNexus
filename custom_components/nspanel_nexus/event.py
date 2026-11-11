"""Event entities: touch, proximity, light."""
from __future__ import annotations

import logging

from homeassistant.components.event import EventEntity
from homeassistant.core import callback

from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusEvent(EventEntity, NexusEntity):
    """One panel event channel as a HA event entity."""

    _attr_should_poll = False

    def __init__(self, link: PanelLink, kind: str, event_types: list[str], name: str) -> None:
        NexusEntity.__init__(self, link, f"event_{kind}", name)
        EventEntity.__init__(self)
        self._kind = kind
        self._attr_event_types = event_types
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_event(self._kind, self._on_event)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_event(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        event_type = data.get("event_type") if isinstance(data, dict) else None
        if not event_type:
            return
        self._trigger_event(event_type)
        self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'event')
    link = hass.data["nspanel_nexus"][entry.entry_id]["link"]
    async_add_entities([
        NexusEvent(
            link, "touch",
            ["tap", "swipe_up", "swipe_down", "swipe_left", "swipe_right"],
            "Touch",
        ),
        NexusEvent(link, "proximity", ["triggered"], "Proximity"),
        NexusEvent(
            link, "light",
            ["light_above", "light_below", "light_normal"], "Light event",
        ),
    ], False)
