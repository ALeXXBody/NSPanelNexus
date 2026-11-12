# NSPanel Pro firmware

A clean Android build for the Sonoff NSPanel Pro (86P, PX30, Android 8.1) with
every Sonoff app removed and the minimal KISS Launcher baked in as the default
home. Built from the panel's own system partition — same kernel, same vendor,
same Zigbee stack — so **all hardware functions are preserved**.

## What changed vs. stock

| Removed | Why |
|---|---|
| `com.eWeLinkControlPanel` (kz_app, **151 MB**) | Sonoff control panel — was also the default launcher |
| `com.eWeLinkNSPro.dev` (factoryApp) | Sonoff dev/factory app |
| `com.rockchip.devicetest`, `com.DeviceTest`, `com.cghs.stresstest` | factory test apps that ran in the background |
| `com.smatek.test` (`/oem/bundled_persist-app/CoolKit_…`) | CoolKit hardware-test app |
| `com.android.gl2jni` (ThomassRoom) | GL test app |
| `Lightning` browser, `Music` player | not needed on a wall panel |
| `frp` remote-access service | Sonoff backdoor in `rootsudaemon.sh` |

| Added | Why |
|---|---|
| **KISS Launcher** (`/system/priv-app/Kiss`) | the panel's only launcher after removing the Sonoff app — 1.2 MB, minimal, fast |

Result: `/system` drops from **100 % full** to **~76 %**, and the background
test services are gone. All hardware functions (display, touch, proximity,
light, Zigbee, Wi-Fi, BT, speaker, relays) keep working — they are started by
`/system/bin/rootsudaemon.sh` and `/vendor`, not by the Sonoff app.

## Verified on the live panel

- Panel rebooted after the change: KISS desktop appears (screenshot verified).
- Nexus app running (launcher + dashboard kiosk + MQTT + remote desktop). Fully Kiosk and NSPanel Pro Tools were removed from the panel — Nexus replaces both, so no third-party on-panel apps are needed.
- Zigbee stack (`mosquitto` + `zgateway`) still running.
- No bloat processes remain.

## Safety net

Every partition of the panel has been dumped and **md5-verified** against the
device (see `parts/`):

| Partition | Size | Purpose |
|---|---|---|
| `system_stock.img` | 1.5 GB | original Sonoff firmware — full rollback |
| `system.img` (clean) | 1.5 GB | the clean firmware |
| `vendor.img`, `oem.img` | 384 MB / 256 MB | hardware blobs + Zigbee stack |
| `boot.img`, `kernel.img`, `recovery.img`, `resource.img`, `uboot.img`, `trust.img` | small | bootloader chain — untouched |
| `backup.img`, `misc.img`, `security.img` | small | unchanged |

In addition the panel has Sonoff's own emergency recovery (power-cycle 5×)
which restores the recovery partition — a last-resort path independent of
anything we do.

## Install / rollback

Install and rollback both use the same command, just with a different image:

```bash
nspanel-nexus firmware flash system  /path/to/system.img
```

The command
1. dumps the current partition to `<backup dir>/system.img` first (md5-verified),
2. pushes the new image to `/data`, verifies sha256 on the device,
3. writes it, then **reads it back and verifies the hash** before declaring success.

Nothing is written if any verification fails.

## OTA updates (Nexus channel)

The panel's recovery enforces Sonoff's signing key, so a stock-OTA path is not
possible without that key. Nexus therefore ships its **own** OTA channel:

- `scripts/nexus-updater.sh` runs on the panel, fetches a `manifest.json`,
  verifies size + sha256, backs up the current system, writes the new one,
  and reboots.
- The manifest/image are served by `nspanel-nexus firmware serve <dir>` (or any
  HTTP server, e.g. GitHub releases).

```bash
# on the panel (as root)
nexus-updater.sh http://<host>:8089/manifest.json
```

Rollback is the same flow pointing at the stock image's manifest.
