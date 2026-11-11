"""Home Assistant MQTT discovery helpers for NSPanel Pro."""

from __future__ import annotations

import json
from typing import Any


def discovery_payload(
    device_name: str,
    component: str,
    object_id: str,
    name: str,
    state_topic: str,
    command_topic: str | None = None,
    **extra: Any,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "name": name,
        "unique_id": f"nexus_{device_name}_{object_id}",
        "state_topic": state_topic,
        "device": {
            "identifiers": [f"nspanelpro_{device_name}"],
            "name": device_name,
            "manufacturer": "Sonoff",
            "model": "NSPanel Pro",
        },
    }
    if command_topic:
        payload["command_topic"] = command_topic
    payload.update(extra)
    return payload


def publish_discovery(client, ha_prefix: str, device_name: str, component: str, object_id: str, payload: dict[str, Any]) -> None:
    topic = f"{ha_prefix}/{component}/nexus_{device_name}_{object_id}/config"
    client.publish(topic, json.dumps(payload), retain=True)
