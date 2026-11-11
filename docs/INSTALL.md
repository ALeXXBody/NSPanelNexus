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

Nexus does not replace on-panel apps. Install one of:
- seaky NSPanel Pro Tools (reference; enables MQTT control)
- Panel Assistant (HA-managed)
- kiosk-satellite (voice + kiosk)

Then drive it with Nexus over ADB/MQTT.
