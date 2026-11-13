"""Text entities: every string setting of the Nexus firmware, editable from HA."""
from __future__ import annotations

import logging

from homeassistant.components.text import TextEntity, TextMode
from homeassistant.core import callback
from homeassistant.helpers.entity import EntityCategory

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)

# Secrets are masked as "<set>" by the panel — never write that back.
_MASKED = "<set>"
_SECRET_KEYS = {"mqtt_pass", "ha_token", "remote_desktop_token"}

# (config key, name, mode, icon)
FIELDS: list[tuple[str, str, TextMode, str | None]] = [
    ("mqtt_host", "MQTT host", TextMode.TEXT, "mdi:server-network"),
    ("mqtt_user", "MQTT user", TextMode.TEXT, "mdi:account"),
    ("mqtt_pass", "MQTT password", TextMode.PASSWORD, "mdi:key"),
    ("topic_prefix", "Topic prefix", TextMode.TEXT, "mdi:format-list-bulleted"),
    ("ha_topic_prefix", "HA discovery prefix", TextMode.TEXT, "mdi:home-assistant"),
    ("ha_url", "Home Assistant URL", TextMode.TEXT, "mdi:home-assistant"),
    ("ha_token", "HA long-lived token", TextMode.PASSWORD, "mdi:key-chain"),
    ("dashboard_url", "Dashboard URL", TextMode.TEXT, "mdi:view-dashboard"),
    ("remote_desktop_token", "Remote desktop token", TextMode.PASSWORD, "mdi:key-chain"),
    ("nexus_manifest_url", "Firmware manifest URL", TextMode.TEXT, "mdi:cloud-download"),
    ("zigbee_topic_base", "Zigbee topic base", TextMode.TEXT, "mdi:zigbee"),
    ("screen_on_weekday_begin", "Screen on weekday from", TextMode.TEXT, "mdi:clock-start"),
    ("screen_on_weekday_end", "Screen on weekday until", TextMode.TEXT, "mdi:clock-end"),
    ("screen_on_weekend_begin", "Screen on weekend from", TextMode.TEXT, "mdi:clock-start"),
    ("screen_on_weekend_end", "Screen on weekend until", TextMode.TEXT, "mdi:clock-end"),
    ("night_start", "Night mode from", TextMode.TEXT, "mdi:weather-night"),
    ("night_end", "Night mode until", TextMode.TEXT, "mdi:weather-sunset-up"),
]


class NexusText(TextEntity, NexusEntity):
    """One firmware string setting, synced via the panel's config topic."""

    def __init__(self, link: PanelLink, key: str, name: str,
                 mode: TextMode = TextMode.TEXT, icon: str | None = None) -> None:
        NexusEntity.__init__(self, link, f"config_{key}", name)
        TextEntity.__init__(self)
        self._key = key
        self._attr_mode = mode
        self._attr_icon = icon
        self._attr_entity_category = EntityCategory.CONFIG
        self._attr_native_value = None
        self.entity_id = f"text.nspanel_{link.device_name}_{key}"
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
        value = data[self._key]
        if value is None:
            return
        text = str(value)
        # Never adopt the masked placeholder as the real value.
        if self._key in _SECRET_KEYS and text in (_MASKED, ""):
            return
        self._attr_native_value = text
        self.async_write_ha_state()

    async def async_set_value(self, value: str) -> None:
        if self._key in _SECRET_KEYS and value in ("", _MASKED):
            return
        await self._link.async_config_update({self._key: value})
        if self._key not in _SECRET_KEYS:
            self._attr_native_value = value
            self.async_write_ha_state()


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    import logging
    logging.getLogger(__name__).info('%s platform setup for entry', 'text')
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities([NexusText(link, k, n, m, i) for k, n, m, i in FIELDS], False)
