"""Firmware build/install/OTA for NSPanel Pro."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from pathlib import Path

from .adb import AdbClient

# Partition map of the NSPanel Pro (86P, PX30), read from /dev/block/by-name
PARTITIONS = {
    "uboot": "/dev/block/mmcblk1p1",
    "trust": "/dev/block/mmcblk1p2",
    "misc": "/dev/block/mmcblk1p3",
    "resource": "/dev/block/mmcblk1p4",
    "kernel": "/dev/block/mmcblk1p5",
    "boot": "/dev/block/mmcblk1p6",
    "recovery": "/dev/block/mmcblk1p7",
    "backup": "/dev/block/mmcblk1p8",
    "security": "/dev/block/mmcblk1p9",
    "cache": "/dev/block/mmcblk1p10",
    "system": "/dev/block/mmcblk1p11",
    "metadata": "/dev/block/mmcblk1p12",
    "vendor": "/dev/block/mmcblk1p13",
    "oem": "/dev/block/mmcblk1p14",
    "frp": "/dev/block/mmcblk1p15",
    "smatek": "/dev/block/mmcblk1p16",
    "keypart": "/dev/block/mmcblk1p17",
    "STSN": "/dev/block/mmcblk1p18",
    "stback": "/dev/block/mmcblk1p19",
    "userdata": "/dev/block/mmcblk1p20",
}

# Sizes in bytes (from /proc/partitions, KB * 1024)
PARTITION_SIZES = {
    "uboot": 4194304,
    "trust": 4194304,
    "misc": 4194304,
    "resource": 16777216,
    "kernel": 33554432,
    "boot": 33554432,
    "recovery": 67108864,
    "backup": 117440512,
    "security": 4194304,
    "cache": 402653184,
    "system": 1610612736,
    "metadata": 16777216,
    "vendor": 402653184,
    "oem": 268435456,
    "frp": 524288,
    "smatek": 16777216,
    "keypart": 262144,
    "STSN": 262144,
    "stback": 1073741824,
    "userdata": 3731922944,
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def dump_partition(client: AdbClient, name: str, out: Path, verify: bool = True) -> bool:
    """Dump a partition to a local file via adb exec-out, truncated to its real size."""
    dev = PARTITIONS.get(name)
    if not dev:
        raise ValueError(f"unknown partition {name}")
    size = PARTITION_SIZES.get(name)
    if not size:
        raise ValueError(f"unknown partition size {name}")
    proc = subprocess.Popen(
        ["adb", "-s", client.target, "exec-out",
         f"su -c 'dd if={dev} bs=1048576 2>/dev/null'"],
        stdout=subprocess.PIPE,
    )
    with open(out, "wb") as f:
        remaining = size
        while remaining > 0:
            chunk = proc.stdout.read(min(1024 * 1024, remaining))
            if not chunk:
                break
            f.write(chunk)
            remaining -= len(chunk)
    proc.wait()
    if remaining > 0:
        print(f"warning: {name} dump short by {remaining} bytes", file=sys.stderr)
        return False
    if verify:
        dev_md5 = client.shell(f"md5sum {dev}").stdout.split()[0]
        # md5 of the whole file
        h = hashlib.md5()
        with open(out, "rb") as f:
            for chunk in iter(lambda: f.read(1024 * 1024), b""):
                h.update(chunk)
        if h.hexdigest() != dev_md5:
            print(f"warning: {name} md5 mismatch (host {h.hexdigest()} != dev {dev_md5})",
                  file=sys.stderr)
            return False
    return True


def make_manifest(images: dict[str, Path], version: str, out: Path) -> None:
    """Write an OTA manifest.json for a set of partition images."""
    manifest = {
        "version": version,
        "images": {
            name: {"size": p.stat().st_size, "sha256": sha256(p)}
            for name, p in images.items()
        },
    }
    out.write_text(json.dumps(manifest, indent=2))


def make_single_manifest(image: Path, version: str, url: str, out: Path) -> None:
    """Write an OTA manifest for one partition image (the firmware release format)."""
    manifest = {
        "version": version,
        "url": url,
        "size": image.stat().st_size,
        "sha256": sha256(image),
    }
    out.write_text(json.dumps(manifest, indent=2))
