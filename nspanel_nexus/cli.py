"""CLI entry point for NSPanel Nexus."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import click
from rich.console import Console
from rich.table import Table

from .adb import AdbClient
from .config import DeviceConfig
from .firmware_cli import firmware as firmware_group
from .mqtt import NexusMqtt
from .screenshot import capture

console = Console()
CONFIG_PATH = Path.home() / ".config" / "nspanel-nexus" / "device.json"


def load_config() -> DeviceConfig:
    if not CONFIG_PATH.exists():
        console.print("[red]No device configured. Run: nspanel-nexus init[/red]")
        sys.exit(1)
    return DeviceConfig.load(CONFIG_PATH)


@click.group()
@click.version_option()
def main():
    """NSPanel Nexus — unified control tool for Sonoff NSPanel Pro."""


@main.command()
@click.option("--name", default="nspanelpro")
@click.option("--host", prompt="Panel IP address")
@click.option("--adb-port", default=5555, type=int)
@click.option("--mqtt-host", default="localhost")
@click.option("--mqtt-port", default=1883, type=int)
@click.option("--mqtt-user", default=None)
@click.option("--mqtt-pass", default=None, hide_input=True)
def init(name, host, adb_port, mqtt_host, mqtt_port, mqtt_user, mqtt_pass):
    """Create a device configuration."""
    cfg = DeviceConfig(
        name=name, host=host, adb_port=adb_port,
        mqtt_host=mqtt_host, mqtt_port=mqtt_port,
        mqtt_username=mqtt_user, mqtt_password=mqtt_pass,
    )
    cfg.save(CONFIG_PATH)
    console.print(f"[green]Saved config to {CONFIG_PATH}[/green]")


@main.command()
def adb_connect():
    """Connect to panel over ADB TCP."""
    cfg = load_config()
    c = AdbClient(cfg.adb_target)
    r = c.connect()
    console.print(r.stdout or r.stderr)


@main.command()
@click.argument("command")
def shell(command):
    """Run a shell command on the panel."""
    cfg = load_config()
    c = AdbClient(cfg.adb_target)
    r = c.shell(command)
    console.print(r.stdout)
    if r.stderr:
        console.print(f"[red]{r.stderr}[/red]")


@main.command()
@click.argument("apk", type=click.Path(exists=True, path_type=Path))
def install(apk):
    """Install an APK on the panel."""
    cfg = load_config()
    c = AdbClient(cfg.adb_target)
    r = c.install(apk)
    console.print(r.stdout or r.stderr)


@main.command()
@click.option("-o", "--output", default="screen.png", type=click.Path(path_type=Path))
def screenshot(output):
    """Capture a screenshot from the panel."""
    cfg = load_config()
    c = AdbClient(cfg.adb_target)
    ok = capture(c, output)
    console.print("[green]saved" if ok else "[red]failed", output)


@main.group()
def mqtt():
    """MQTT control commands."""


def _mqtt() -> NexusMqtt:
    cfg = load_config()
    m = NexusMqtt(
        host=cfg.mqtt_host, port=cfg.mqtt_port,
        username=cfg.mqtt_username, password=cfg.mqtt_password,
        topic_prefix=cfg.mqtt_topic_prefix, device_name=cfg.name,
    )
    if not m.connect():
        console.print("[red]MQTT connect failed[/red]")
        sys.exit(1)
    return m


@mqtt.command("wake")
def mqtt_wake():
    m = _mqtt(); m.wake_up(); console.print("wake_up sent"); m.disconnect()


@mqtt.command("sleep")
def mqtt_sleep():
    m = _mqtt(); m.sleep(); console.print("sleep sent"); m.disconnect()


@mqtt.command("reboot")
def mqtt_reboot():
    m = _mqtt(); m.reboot(); console.print("reboot sent (x3)"); m.disconnect()


@mqtt.command("launcher")
def mqtt_launcher():
    m = _mqtt(); m.launcher(); console.print("launcher sent"); m.disconnect()


@mqtt.command("brightness")
@click.argument("value", type=int)
def mqtt_brightness(value):
    m = _mqtt(); m.set_brightness(value); console.print(f"brightness={value} sent"); m.disconnect()


@mqtt.command("switch")
@click.argument("sid")
@click.argument("state")
def mqtt_switch(sid, state):
    m = _mqtt(); m.switch(sid, state); console.print(f"{sid}={state} sent"); m.disconnect()


@mqtt.command("query")
@click.option("--keys", default=None, help="Comma-separated config keys")
def mqtt_query(keys):
    m = _mqtt()
    ks = [k.strip() for k in keys.split(",")] if keys else None
    m.config_query(ks)
    console.print("query sent")
    m.disconnect()


@mqtt.command("update")
@click.argument("jsonstr")
def mqtt_update(jsonstr):
    m = _mqtt()
    m.config_update(json.loads(jsonstr))
    console.print("update sent")
    m.disconnect()


@main.command()
def info():
    """Print device config and ADB status."""
    cfg = load_config()
    c = AdbClient(cfg.adb_target)
    table = Table(title="NSPanel Nexus device")
    table.add_column("Field"); table.add_column("Value")
    table.add_row("name", cfg.name)
    table.add_row("host", cfg.host)
    table.add_row("adb_target", cfg.adb_target)
    table.add_row("mqtt", f"{cfg.mqtt_host}:{cfg.mqtt_port}")
    table.add_row("adb_connected", str(c.is_connected()))
    console.print(table)


main.add_command(firmware_group)

if __name__ == "__main__":
    main()
