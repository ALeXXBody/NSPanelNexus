"""Screenshot capture via ADB."""

from __future__ import annotations

from pathlib import Path

from .adb import AdbClient


def capture(client: AdbClient, local: Path, remote: str = "/sdcard/nexus_screen.png") -> bool:
    r = client.screenshot(remote)
    if not r.ok:
        return False
    r2 = client.pull_screenshot(local, remote)
    return r2.ok
