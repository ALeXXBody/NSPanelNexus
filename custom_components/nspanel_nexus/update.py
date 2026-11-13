"""Update entities: Nexus app (APK) and system image channels."""
from __future__ import annotations

import logging

from homeassistant.components.update import (
    UpdateDeviceClass,
    UpdateEntity,
    UpdateEntityFeature,
)
from homeassistant.core import callback

from .const import DOMAIN
from .entity import NexusEntity
from .link import PanelLink

_LOGGER = logging.getLogger(__name__)


class NexusUpdate(UpdateEntity, NexusEntity):
    """One OTA channel of the panel.

    The panel publishes the merged firmware state on `<base>/state/firmware`:

        {"current": "1.0.0", "app": "0.4.68",
         "available": "1.0.1", "app_available": "0.4.69"}

    `available`/`app_available` only appear after a check, so the latest
    version falls back to the installed one (entity = up to date).
    """

    _attr_device_class = UpdateDeviceClass.FIRMWARE
    _attr_supported_features = UpdateEntityFeature.INSTALL

    def __init__(self, link: PanelLink, channel: str, title: str) -> None:
        NexusEntity.__init__(self, link, f"update_{channel}", title)
        UpdateEntity.__init__(self)
        self._channel = channel  # "app" | "system"
        self._attr_title = title
        self._attr_installed_version = None
        self._attr_latest_version = None
        self._unsub = None

    async def async_added_to_hass(self) -> None:
        await super().async_added_to_hass()
        self._unsub = await self._link.async_subscribe_state("firmware", self._on_state)

    async def async_will_remove_from_hass(self) -> None:
        if self._unsub:
            self._unsub()
            self._unsub = None

    @callback
    def _on_state(self, msg) -> None:
        data = self._link.parse_json(msg.payload)
        if not isinstance(data, dict):
            return
        if self._channel == "app":
            installed = data.get("app")
            latest = data.get("app_available") or installed
        else:
            installed = data.get("current")
            latest = data.get("available") or installed
        if installed not in (None, ""):
            self._attr_installed_version = str(installed)
        if latest not in (None, ""):
            self._attr_latest_version = str(latest)
        err = data.get("error")
        self._attr_release_summary = str(err) if err else None
        self.async_write_ha_state()

    async def async_install(
        self, version: str | None = None, backup: bool = False, **kwargs
    ) -> None:
        if self._channel == "app":
            await self._link.async_command("app:update")
        else:
            await self._link.async_command("firmware:install")

    async def async_update(self) -> None:
        # Let the panel re-check both channels when HA asks for a refresh.
        await self._link.async_command(
            "app:check" if self._channel == "app" else "firmware:check"
        )


async def async_setup_entry(hass, entry, async_add_entities) -> None:
    link = hass.data[DOMAIN][entry.entry_id]["link"]
    async_add_entities(
        [
            NexusUpdate(link, "app", "Nexus app"),
            NexusUpdate(link, "system", "System image"),
        ],
        False,
    )
