# Firmware tooling

`unpack_ota.py` unpacks a Sonoff NSPanel Pro OTA zip into per-partition raw
images (system, vendor, oem, boot, trust, uboot) for inspection/patching.

See [../research/FIRMWARE_RESEARCH.md](../research/FIRMWARE_RESEARCH.md) for
feasibility notes. Full custom ROMs are not realistic today; patching official
OTA images is the viable path.
