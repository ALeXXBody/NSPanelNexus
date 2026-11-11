"""MQTT control surface for NSPanel Pro (seaky Pro Tools protocol)."""

from __future__ import annotations

import json
import time
from collections.abc import Callable
from typing import Any

import paho.mqtt.client as mqtt


class NexusMqtt:
    """Thin wrapper around the seaky NSPanel Pro Tools MQTT protocol."""

    def __init__(
        self,
        host: str,
        port: int = 1883,
        username: str | None = None,
        password: str | None = None,
        topic_prefix: str = "nspanelpro",
        device_name: str = "nspanelpro",
    ):
        self.host = host
        self.port = port
        self.topic_prefix = topic_prefix
        self.device_name = device_name
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
        if username:
            self.client.username_pw_set(username, password)
        self._connected = False
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self._message_handlers: dict[str, Callable[[str, Any], None]] = {}

    @property
    def base(self) -> str:
        return f"{self.topic_prefix}/{self.device_name}"

    def _on_connect(self, client, userdata, flags, reason_code, properties=None):
        self._connected = (reason_code == 0)
        # subscribe to config answers and events
        client.subscribe(f"{self.base}/config")
        client.subscribe(f"{self.base}/event/#")
        client.subscribe(f"{self.base}/state/#")

    def _on_disconnect(self, client, userdata, disconnect_flags, reason_code, properties=None):
        self._connected = False

    def connect(self, timeout: float = 10.0) -> bool:
        self.client.connect(self.host, self.port, keepalive=60)
        self.client.loop_start()
        deadline = time.time() + timeout
        while not self._connected and time.time() < deadline:
            time.sleep(0.1)
        return self._connected

    def disconnect(self) -> None:
        self.client.loop_stop()
        self.client.disconnect()

    def publish(self, topic: str, payload: Any, qos: int = 0, retain: bool = False) -> None:
        if not isinstance(payload, str):
            payload = json.dumps(payload)
        self.client.publish(topic, payload, qos=qos, retain=retain)

    # --- device commands (seaky protocol) ---
    def command(self, cmd: str) -> None:
        self.publish(f"{self.base}/command/device", {"command": cmd})

    def wake_up(self) -> None:
        self.command("wake_up")

    def sleep(self) -> None:
        self.command("sleep")

    def reboot(self) -> None:
        # seaky requires 3 consecutive reboot commands
        for _ in range(3):
            self.command("reboot")
            time.sleep(0.2)

    def launcher(self) -> None:
        self.command("launcher")

    def default_app(self) -> None:
        self.command("default_app")

    def adb_enable(self) -> None:
        self.command("adb")

    def switch(self, switch_id: str, state: str) -> None:
        # switch_id: switch_1 / switch_2 ; state: on/off
        self.publish(f"{self.base}/command/{switch_id}", {"state": state})

    # --- config query / update ---
    def config_query(self, filter_keys: list | None = None) -> None:
        payload = {} if not filter_keys else {k: None for k in filter_keys}
        self.publish(f"{self.base}/config/query", payload)

    def config_update(self, updates: dict[str, Any]) -> None:
        self.publish(f"{self.base}/config/update", updates)

    def set_brightness(self, value: int) -> None:
        self.config_update({"brightness": value})

    def set_hostname(self, hostname: str) -> None:
        self.config_update({"hostname": hostname})

    def on_message(self, callback: Callable[[str, Any], None]) -> None:
        def _cb(client, userdata, msg):
            try:
                payload = json.loads(msg.payload.decode())
            except (json.JSONDecodeError, UnicodeDecodeError):
                payload = msg.payload.decode(errors="replace")
            callback(msg.topic, payload)

        self.client.on_message = _cb
        # re-subscribe handled by on_connect; ensure subs
        self.client.subscribe(f"{self.base}/#")
