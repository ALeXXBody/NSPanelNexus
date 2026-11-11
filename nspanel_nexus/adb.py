"""ADB helpers for talking to a NSPanel Pro over TCP."""

from __future__ import annotations

import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path


@dataclass
class AdbResult:
    returncode: int
    stdout: str
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


class AdbClient:
    def __init__(self, target: str):
        self.target = target

    def _run(self, *args: str, timeout: int = 30) -> AdbResult:
        if shutil.which("adb") is None:
            raise RuntimeError("adb not found on PATH")
        cmd = ["adb", "-s", self.target, *args]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout, check=False)
        return AdbResult(proc.returncode, proc.stdout, proc.stderr)

    def connect(self) -> AdbResult:
        # connect is a global adb command, not device-scoped
        proc = subprocess.run(
            ["adb", "connect", self.target], capture_output=True, text=True, timeout=30, check=False
        )
        return AdbResult(proc.returncode, proc.stdout, proc.stderr)

    def disconnect(self) -> AdbResult:
        proc = subprocess.run(
            ["adb", "disconnect", self.target], capture_output=True, text=True, timeout=30, check=False
        )
        return AdbResult(proc.returncode, proc.stdout, proc.stderr)

    def shell(self, command: str, timeout: int = 30) -> AdbResult:
        return self._run("shell", command, timeout=timeout, check=False)

    def install(self, apk: Path, reinstall: bool = True) -> AdbResult:
        args = ["install"]
        if reinstall:
            args.append("-r")
        args.append(str(apk))
        return self._run(*args, timeout=300)

    def push(self, local: Path, remote: str) -> AdbResult:
        return self._run("push", str(local), remote, timeout=300)

    def pull(self, remote: str, local: Path) -> AdbResult:
        return self._run("pull", remote, str(local), timeout=300)

    def screenshot(self, remote: str = "/sdcard/nexus_screen.png") -> AdbResult:
        return self.shell(f"screencap -p {remote}")

    def pull_screenshot(self, local: Path, remote: str = "/sdcard/nexus_screen.png") -> AdbResult:
        return self.pull(remote, local)

    def keyevent(self, code: int) -> AdbResult:
        return self.shell(f"input keyevent {code}")

    def home(self) -> AdbResult:
        return self.keyevent(3)

    def power(self) -> AdbResult:
        return self.keyevent(26)

    def devices(self) -> list[str]:
        proc = subprocess.run(["adb", "devices", "-l"], capture_output=True, text=True, timeout=30, check=False)
        lines = [ln for ln in proc.stdout.splitlines() if "\tdevice" in ln or " device " in ln]
        return lines

    def is_connected(self) -> bool:
        r = self.shell("echo ok", timeout=10)
        return r.ok and "ok" in r.stdout
