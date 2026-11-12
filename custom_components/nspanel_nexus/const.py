"""NSPanel Nexus — Home Assistant integration for a Nexus panel."""
from __future__ import annotations

import re

DOMAIN = "nspanel_nexus"

CONF_DEVICE_NAME = "device_name"
CONF_TOPIC_PREFIX = "topic_prefix"

DEFAULT_DEVICE_NAME = "nspanelpro"
DEFAULT_TOPIC_PREFIX = "nspanelpro"

STATE_ONLINE = "online"

PLATFORMS = ["event", "sensor", "binary_sensor", "switch", "number", "button", "text", "select"]


def topic_safe(name: str) -> str:
    """Same sanitisation the panel app uses for its MQTT topic base."""
    return re.sub(r"[^A-Za-z0-9_-]+", "_", name).strip("_ ") or "nspanel"
