# Feature Matrix — NSPanel Nexus vs. source projects

Legend: ✅ supported · 🟡 partial / planned · ❌ not applicable · ➕ Nexus-new

| Capability | seaky Pro Tools | kiosk-satellite | Panel Assistant | Nexus (host tool) |
|---|---|---|---|---|
| Runs on panel (Android) | ✅ | ✅ | ✅ | ❌ (host-side) |
| ADB connect/install/shell | ➕ (enables) | — | ✅ | ✅ |
| Screenshot capture | ✅ | — | — | ✅ |
| MQTT device commands | ✅ | — | — | ✅ |
| MQTT config query/update | ✅ | — | — | ✅ |
| HA MQTT discovery | ✅ | — | ✅ | helper |
| Proximity (threshold) | ✅ | detects quirk | — | ✅ (via MQTT) |
| Wake-on-wave/gesture | ✅ | ✅ | — | config only |
| Brightness control | ✅ | — | — | ✅ |
| Zigbee packages | ✅ | — | — | push via ADB |
| OTA re-flash | roottool | — | ✅ | ✅ (push) |
| Voice satellite | — | ✅ | ✅ | — |
| Bluetooth proxy | — | — | ✅ | — |
| Fleet management | — | — | — | 🟡 planned |
| Config backup/restore | — | — | ✅ | 🟡 planned |
| Dashboard provisioning | — | — | — | 🟡 planned |

Nexus is a **host-side control plane**, not a replacement for the on-panel apps.
It complements them: it drives them over ADB/MQTT and unifies their protocols.
