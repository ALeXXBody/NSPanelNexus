# NSPanel Pro — Firmware Research

> Can we build a *proper* (Android system) firmware for the NSPanel Pro, instead of
> just an APK? Short answer: **not from scratch**, but **yes for modified/patched
> firmware** built on top of Sonoff's OTA images. Full custom ROMs are not
> realistic today because Sonoff does not ship a BSP/device tree and the device
> has licensing tied to Rockchip vendor storage.

## Hardware

| Model | SoC | Display | Storage/RAM |
|---|---|---|---|
| NSPanel Pro 86 | Rockchip **PX30** (Cortex-A35) | 480×480 3.96" | 8 GB eMMC / 2 GB |
| NSPanel Pro 120 | Rockchip **RK3326S** (Cortex-A35) | 750×1334 4.7" | 8 GB (32 GB on Gen2) / 2 GB |

- OS: **Android 8.1 Oreo (AOSP)**, API 27, `arm64-v8a` + `armeabi-v7a`.
- Build fingerprint style: `px30_evb` / `rk3326` (see `adb shell getprop`).
- Radios: Wi-Fi, BT, Zigbee (Silicon Labs ZStack via `/vendor/bin/siliconlabs_host`).
- Sensors: proximity, ambient light, GPIO.
- Boot: Rockchip loader → U-Boot → kernel → Android; recovery partition present.

## What Sonoff ships

- Firmware as **OTA zip** files (`*-ota.zip`) hosted on `global-otadl2bsy.coolkit.cc`
  (channels `nspanel-pro-ver86` / `nspanel-pro-ver120`). Both **full ROM** and
  **diff** (incremental) packages exist.
- Installed via the on-device "Software update" or seaky's root tool (which calls
  the same updater after pushing `system_update.zip`).
- Versions: stable track currently ~4.6.x; community recommends ≤1.9/2.x for
  modders because newer kernels lose proximity threshold + screen-off touch.

## Licensing / identity (the hard part)

Device identity and the eWeLink licence are stored in **Rockchip vendor storage**
(`/dev/vendor_storage`, accessed by slot IDs). The eWeLink dev-app uses its own
`ioctl` library to read/write these. This is why:

- Enabling developer mode is **permanent** and voids warranty.
- Factory reset restores the recovery-partition firmware **and** keeps licence
  data (unless you hit the "rabbit hole" recovery QR screen).
- A fully clean AOSP build would need to reproduce/provision this storage, which
  is undocumented and device-unique.

## Available tooling for firmware work

| Tool | Purpose |
|---|---|
| `afptool` / `afptool-rs` | Unpack/repack Rockchip SSFW/RKFW/RKAF firmware images (supports PX30/RK3326) |
| `rkdeveloptool` / RKDevTool (Windows) | Flash partitions over USB (maskrom/loader mode) |
| `rkcrc` | Add Rockchip CRC to kernel/resource images |
| `img_unpack` / `img_repack` | Split `update.img` into per-partition images |
| `magisk` / Xposed | Patch `boot.img`/`system` on rooted devices |
| `adb` + `mount -o rw,remount` | Runtime system/vendor patching (seaky approach) |

## Realistic approaches

### 1. Patch Sonoff's firmware (recommended, feasible)
Start from an official OTA zip, unpack with `afptool`/`img_unpack`, modify
`system`/`vendor`/`boot`, repack, flash via root tool or RKDevTool. This keeps
Rockchip drivers, Zigbee stack, and licence storage intact. Used by seaky for
root tool and by maxlyth for the Thread-mesh PoC.

### 2. Runtime modification (what Nexus does today)
Remount `/system`/`/vendor` read-write on a rooted device and push changes.
No reflash risk, but changes are lost on factory reset/OTA.

### 3. Full custom AOSP build (not realistic)
Rockchip publishes Android SDKs for PX30/RK3326 (see `rockchip-android` GitHub
org and Radxa/Firefly trees), and AOSP can be built for `rk3326` with a device
tree. But there is **no public device tree for NSPanel Pro** (panel, touch,
sensors, Zigbee, audio). Reconstructing it from the official firmware's
`device/rockchip/` and `vendor/` trees is a large reverse-engineering effort,
and you still need to provision vendor storage. No community custom ROM exists
today (no LineageOS device tree, no TWRP build for this panel).

### 4. Hybrid: AOSP + Sonoff vendor blobs
Build AOSP for `rk3326` using Rockchip's SDK, then inject Sonoff's
`vendor`, `boot`, and resource partitions. Theoretically possible but untested
and high-risk (brick → maskrom recovery via OTG).

## Prior art / references

- `seaky/nspanel_pro_roottool_apk` — installs official OTA zips on rooted units;
  documents firmware version behaviour.
- `seaky/nspanel_pro_tools_apk` issue #1 — firmware download URLs and diff vs
  full ROM details.
- `maxlyth/ha-paneld` — built a **PoC Thread mesh radio firmware** for NSPanel
  Pro (not released publicly).
- `afptool-rs` (suyulin) — modern Rust unpacker for Rockchip images.
- Rockchip open source: https://opensource.rock-chips.com, `rockchip-android`
  GitHub org (device trees, RKTools, TWRP).
- Radxa/96Boards AOSP build guides for RK3399/RK3326.
- Blakadder NSPanel Pro sideload guide (ADB, OTG recovery).
- eWeLink forum — firmware release notes and OTA channels.

## Risks

- **Brick / licence loss**: wrong flash can destroy vendor storage → device
  enters factory/recovery mode; recovery requires OTG + keyboard (blakadder
  "rabbit hole" procedure) and may lose the eWeLink licence.
- **OTA incompatibility**: rooted units cannot receive official OTAs; manual
  diff application required.
- **Warranty void** on any modification.
- **No community safety net**: no TWRP, no custom ROM, no device tree.

## Recommendation for Nexus

Do **not** attempt a full custom ROM now. Instead:

1. Keep Nexus as the control plane (ADB + MQTT) — current scope.
2. Add a `firmware/` module that can **inspect and patch** official OTA zips
   (unpack with afptool, show partitions, apply small patches like disabling
   OTA checks or pre-installing apps) and re-pack for the root tool.
3. Track maxlyth's Thread-mesh work and Rockchip SDK developments; revisit a
   full AOSP build only if a community device tree emerges.

## Confirmed OTA zip layout (sample: 120P V4.0.12 full ROM, 865 MB)

Standard **Android block-based OTA** (not Rockchip `update.img`):

```
META-INF/com/android/metadata
META-INF/com/google/android/update-binary
META-INF/com/google/android/updater-script
system.transfer.list / system.new.dat.br / system.patch.dat
vendor.transfer.list / vendor.new.dat.br / vendor.patch.dat
oem.transfer.list    / oem.new.dat.br    / oem.patch.dat
boot.img
trust.img
uboot.img
file_contexts.bin
META-INF/com/android/otacert
```

- `*.new.dat.br` are **brotli**-compressed block images; convert with
  `brotli -d` then `sdat2img` (transfer.list + .dat → raw .img).
- Firmware work is at the **Android partition level** (system/vendor/oem/boot),
  not Rockchip update.img. `afptool` is only needed for maskrom flashing; for
  OTA-style updates the standard Android updater-script flow applies.
- This makes "patch Sonoff's firmware" approach #1 much easier: unpack OTA zip,
  brotli-decompress + sdat2img, mount/edit, re-compress + re-pack zip with a
  modified updater-script.

## Open questions

- Whether `/dev/vendor_storage` can be backed up/restored safely.
- Feasibility of replacing only the `system` partition with a generic AOSP
  `system.img` while keeping Sonoff's `vendor`/`boot`.
- Whether the updater verifies partition hashes/signatures (otacert present) —
  likely yes, so repacked OTAs may fail signature checks unless the device is
  rooted and the root tool bypasses verification.

## Live device findings (test panel, FW 3.0.0, PX30, 86P)

Inspected via ADB root. Key bloat and resource consumers:

- `/system/app/kz_app` — **151 MB** APK (odex included). Package
  `com.eWeLinkControlPanel` (Sonoff control panel, "eWeLinkControlPannel" v3.0.0).
  This is the main Sonoff bloat that makes the device sluggish.
- `/system/app/WebView` — 328 MB (needed for HA companion / Fully Kiosk; keep).
- `/system/app/factoryApp` — `com.eWeLinkNSPro.dev` (Sonoff dev/factory app).
- `/system/app/RkDeviceTest` — `com.rockchip.devicetest`.
- `/system/priv-app/StressTest` — `com.cghs.stresstest`.
- `/system/priv-app/DeviceTest` — `com.DeviceTest`.
- `/system/app/ThomassRoom` — `com.android.gl2jni` (GL test).
- `/oem/bundled_persist-app/CoolKit_22101317` — `com.smatek.test` (CoolKit test app).
- `/vendor/app/Rk4kVideoPlayer`, `RkApkinstaller`.

Running processes consuming RAM at idle include `com.rockchip.devicetest`,
`com.cghs.stresstest`, `com.smatek.test`, plus webview sandboxes.

`/system` partition is **100% full** (1.3 GB used of 1.4 GB); `/data` has 2.4 GB
free. System is `userdebug` and `su` is available, so remounting read-write and
removing bloat in place is possible.

### Debloat approach (implemented)

`scripts/debloat_nspanel_pro.sh <ip>` remounts `/system`/`/vendor`/`/oem`
read-write, removes the bloat directories above (optionally keeping the Sonoff
control panel via `--keep-sonoff`), disables test packages, syncs, and reboots.
This is the practical "firmware" improvement available without a full reflash:
it frees ~200+ MB on `/system` and removes background test services.

### Why not a full clean firmware

The Sonoff OTA is a standard Android block-based OTA; we could unpack it,
remove bloat, and repack, but signature verification (`otacert`) will reject
repacked images on stock devices. Rooted devices bypass this via the root tool,
so in-place debloat on a rooted panel is the reliable path today.
