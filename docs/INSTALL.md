# Installation

## 1. Prerequisites

- Python 3.10+
- `adb` on PATH (Android platform-tools)
- An MQTT broker reachable from both host and panel (Mosquitto, HA Mosquitto add-on, etc.)
- A rooted NSPanel Pro with ADB over TCP enabled (see research/NSPANEL_PRO_HARDWARE.md)

## 2. Install Nexus

```bash
git clone https://github.com/ALeXXBody/NSPanelNexus.git
cd NSPanelNexus
pip install -e .
```

## 3. Configure device

```bash
nspanel-nexus init
# prompts for IP, MQTT broker, credentials
```

Config is stored at `~/.config/nspanel-nexus/device.json`.

## 4. Connect

```bash
nspanel-nexus adb-connect
nspanel-nexus info
```

## 5. Control

```bash
nspanel-nexus mqtt wake
nspanel-nexus mqtt brightness 80
nspanel-nexus mqtt switch switch_1 on
nspanel-nexus mqtt reboot
nspanel-nexus screenshot -o panel.png
```

## On-panel software

The Nexus firmware (this repo's `app/`) IS the on-panel app: dashboard kiosk
(default state, 6-tap exit with optional PIN, Return-to-HA button), MQTT control,
remote desktop, display/voice/Zigbee modules. Install it and no third-party
on-panel apps are needed — Fully Kiosk and NSPanel Pro Tools can be removed.

The seaky protocol references in this repo (`mqtt.py`, `MQTT_COMMANDS.md`) only
describe the wire format Nexus speaks for the legacy CLI-driven setup (panel
running third-party firmware apps). With Nexus firmware installed, that setup
is obsolete.
