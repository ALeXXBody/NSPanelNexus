"""Flash/OTA commands for NSPanel Pro firmware."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import time
from pathlib import Path

import click
from rich.console import Console

from .adb import AdbClient
from .config import DeviceConfig
from .firmware import PARTITIONS, PARTITION_SIZES, sha256

console = Console()


def _load() -> DeviceConfig:
    path = Path.home() / ".config" / "nspanel-nexus" / "device.json"
    if not path.exists():
        console.print("[red]No device configured. Run: nspanel-nexus init[/red]")
        raise SystemExit(1)
    return DeviceConfig.load(path)


def _adb() -> AdbClient:
    cfg = _load()
    c = AdbClient(cfg.adb_target)
    c.connect()
    return c


@click.group()
def firmware():
    """Firmware build / install / OTA commands."""


@firmware.command("dump")
@click.option("-o", "--out", default="panel_dump", type=click.Path(path_type=Path))
@click.option("--parts", default="system,vendor,oem,boot,kernel,recovery,resource,uboot,trust,misc,backup,security", show_default=True)
def dump(out, parts):
    """Dump partitions from the panel (md5-verified)."""
    c = _adb()
    out.mkdir(parents=True, exist_ok=True)
    for name in [p.strip() for p in parts.split(",") if p.strip()]:
        dev = PARTITIONS.get(name)
        size = PARTITION_SIZES.get(name)
        if not dev or not size:
            console.print(f"[red]unknown partition {name}[/red]")
            continue
        console.print(f"[cyan]dumping {name}[/cyan]")
        c.shell(f"dd if={dev} of=/data/nx.dump bs=1048576 2>/dev/null; chmod 644 /data/nx.dump; sync")
        c.pull("/data/nx.dump", out / f"{name}.img")
        c.shell("rm -f /data/nx.dump")
        f = out / f"{name}.img"
        dev_md5 = c.shell(f"md5sum {dev}").stdout.split()[0]
        ok = sha256(f)  # warm check only
        h = _md5(f)
        if h == dev_md5 and f.stat().st_size == size:
            console.print(f"  [green]ok[/green] {name}.img ({size} B, md5 {h[:12]}…)")
        else:
            console.print(f"  [red]MISMATCH[/red] {name}: host {h[:12]} dev {dev_md5[:12]} size {f.stat().st_size}/{size}")


def _md5(path: Path) -> str:
    import hashlib
    h = hashlib.md5()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


@firmware.command("flash")
@click.argument("name")
@click.argument("image", type=click.Path(exists=True, path_type=Path))
@click.option("--backup", default="panel_backup", type=click.Path(path_type=Path),
              help="directory to store a backup of the partition before writing")
def flash(name, image, backup):
    """Write IMAGE to partition NAME (backs up first)."""
    c = _adb()
    dev = PARTITIONS.get(name)
    size = PARTITION_SIZES.get(name)
    if not dev:
        console.print(f"[red]unknown partition {name}[/red]")
        raise SystemExit(1)
    if image.stat().st_size > size:
        console.print(f"[red]image too large: {image.stat().st_size} > {size}[/red]")
        raise SystemExit(1)
    backup.mkdir(parents=True, exist_ok=True)
    bk = backup / f"{name}.img"
    if not bk.exists():
        console.print(f"[cyan]backing up {name} to {bk}[/cyan]")
        c.shell(f"dd if={dev} of=/data/nx.bak bs=1048576 2>/dev/null; chmod 644 /data/nx.bak; sync")
        c.pull("/data/nx.bak", bk)
        c.shell("rm -f /data/nx.bak")
        if _md5(bk) != c.shell(f"md5sum {dev}").stdout.split()[0]:
            console.print("[red]backup md5 mismatch — aborting[/red]")
            raise SystemExit(1)
        console.print("  backup ok")
    console.print(f"[cyan]writing {name}[/cyan]")
    c.push(image, "/data/nx.new")
    c.shell("chmod 644 /data/nx.new; sync")
    # verify the pushed image on device before writing
    dev_sha = c.shell(f"sha256sum /data/nx.new").stdout.split()[0]
    if dev_sha != sha256(image):
        console.print("[red]pushed image hash mismatch — aborting[/red]")
        raise SystemExit(1)
    c.shell(f"dd if=/data/nx.new of={dev} bs=1048576 2>/dev/null; sync; rm -f /data/nx.new")
    readback = c.shell(f"sha256sum {dev} | cut -d' ' -f1").stdout.split()[0]
    if readback != sha256(image):
        console.print(f"[red]readback hash mismatch: {readback}[/red]")
        raise SystemExit(1)
    console.print(f"[green]flashed {name} and verified readback[/green]")


@firmware.command("manifest")
@click.argument("images", nargs=-1, type=click.Path(exists=True, path_type=Path))
@click.option("-v", "--version", default="1.0.0")
@click.option("-o", "--out", default="manifest.json", type=click.Path(path_type=Path))
def manifest(images, version, out):
    """Generate an OTA manifest for the given partition images."""
    m = {"version": version, "images": {}}
    for p in images:
        name = p.stem
        m["images"][name] = {"size": p.stat().st_size, "sha256": sha256(p)}
    out.write_text(json.dumps(m, indent=2))
    console.print(f"wrote {out}")


@firmware.command("serve")
@click.argument("directory", type=click.Path(exists=True, path_type=Path))
@click.option("--port", default=8089, type=int)
def serve(directory, port):
    """Serve an OTA directory (manifest.json + images) over HTTP."""
    os.chdir(directory)
    import http.server
    httpd = http.server.HTTPServer(("0.0.0.0", port), http.server.SimpleHTTPRequestHandler)
    console.print(f"serving {directory} on 0.0.0.0:{port}")
    httpd.serve_forever()
