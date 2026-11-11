"""Configuration model for a NSPanel Pro device."""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel, Field


class DeviceConfig(BaseModel):
    """Connection details for a single NSPanel Pro."""

    name: str = Field(default="nspanelpro", description="Friendly device name / MQTT device id")
    host: str = Field(..., description="Panel IP address (ADB + MQTT target)")
    adb_port: int = Field(default=5555, description="ADB over TCP port")
    mqtt_host: str = Field(default="localhost", description="MQTT broker host")
    mqtt_port: int = Field(default=1883, description="MQTT broker port")
    mqtt_username: str | None = None
    mqtt_password: str | None = None
    mqtt_topic_prefix: str = Field(default="nspanelpro", description="MQTT topic prefix used by Pro Tools")
    ha_topic_prefix: str = Field(default="homeassistant", description="Home Assistant discovery topic prefix")

    @property
    def adb_target(self) -> str:
        return f"{self.host}:{self.adb_port}"

    @property
    def device_topic(self) -> str:
        return f"{self.mqtt_topic_prefix}/{self.name}"

    def save(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(self.model_dump_json(indent=2))

    @classmethod
    def load(cls, path: Path) -> DeviceConfig:
        return cls.model_validate(json.loads(path.read_text()))
