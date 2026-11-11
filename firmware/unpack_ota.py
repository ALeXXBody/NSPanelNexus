#!/usr/bin/env python3
"""Unpack a Sonoff NSPanel Pro OTA zip into per-partition raw images.

Usage:
    python firmware/unpack_ota.py path/to/*-ota.zip outdir/

Produces outdir/<partition>.img (system, vendor, oem, boot, trust, uboot) plus
the transfer lists. Requires brotli (pip install brotli).
"""
from __future__ import annotations

import os
import sys
import zipfile
from pathlib import Path

import brotli

BLOCK_SIZE = 4096


def sdat2img(transfer_list: Path, new_dat: Path, out_img: Path) -> None:
    """Convert an Android .new.dat (raw or brotli) + transfer.list to a raw .img."""
    # decompress if needed
    raw = new_dat.read_bytes()
    if str(new_dat).endswith(".br"):
        raw = brotli.decompress(raw)

    lines = transfer_list.read_text().splitlines()
    # parse header
    # skip version + block size lines until commands
    # find first command line
    cmds = []
    for line in lines:
        line = line.strip()
        if not line:
            continue
        parts = line.split()
        if parts[0] in ("new", "zero", "erase", "diff"):
            cmds.append(parts)
        # ignore header/counts
    # determine total blocks
    maxblk = 0
    for c in cmds:
        if len(c) >= 3:
            try:
                maxblk = max(maxblk, int(c[2]))
            except ValueError:
                pass
    out_img.parent.mkdir(parents=True, exist_ok=True)
    with open(out_img, "wb") as f:
        f.truncate(maxblk * BLOCK_SIZE)
    with open(out_img, "r+b") as f:
        data_off = 0
        for c in cmds:
            cmd = c[0]
            if cmd == "new":
                start, end = int(c[1]), int(c[2])
                length = (end - start) * BLOCK_SIZE
                chunk = raw[data_off:data_off + length]
                data_off += length
                f.seek(start * BLOCK_SIZE)
                f.write(chunk)
            elif cmd in ("zero", "erase"):
                start, end = int(c[1]), int(c[2])
                f.seek(start * BLOCK_SIZE)
                f.write(b"\x00" * ((end - start) * BLOCK_SIZE))
            # diff not handled (full images only)
    print(f"  wrote {out_img} ({maxblk} blocks)")


def main():
    if len(sys.argv) != 3:
        print("usage: unpack_ota.py <ota.zip> <outdir>")
        sys.exit(1)
    zip_path = Path(sys.argv[1])
    outdir = Path(sys.argv[2])
    outdir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path) as z:
        namelist = z.namelist()
        # extract simple files
        for name in namelist:
            if name.endswith((".img", ".bin", ".list", ".br", ".dat")):
                target = outdir / os.path.basename(name)
                with z.open(name) as src, open(target, "wb") as dst:
                    dst.write(src.read())
                print("extracted", name, "->", target.name)
        # convert partitions
        for part in ("system", "vendor", "oem"):
            tl = outdir / f"{part}.transfer.list"
            nd = outdir / f"{part}.new.dat.br"
            if not nd.exists():
                nd = outdir / f"{part}.new.dat"
            if tl.exists() and nd.exists():
                sdat2img(tl, nd, outdir / f"{part}.img")
    print("done")


if __name__ == "__main__":
    main()
