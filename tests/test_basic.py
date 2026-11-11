import tempfile
from pathlib import Path

from nspanel_nexus.config import DeviceConfig


def test_config_roundtrip():
    cfg = DeviceConfig(name="panel1", host="192.168.1.5")
    with tempfile.TemporaryDirectory() as d:
        p = Path(d) / "device.json"
        cfg.save(p)
        loaded = DeviceConfig.load(p)
        assert loaded == cfg
        assert loaded.adb_target == "192.168.1.5:5555"
        assert loaded.device_topic == "nspanelpro/panel1"


def test_ha_discovery():
    from nspanel_nexus.ha import discovery_payload
    p = discovery_payload("panel1", "sensor", "proximity", "Proximity", "t/state", command_topic="t/cmd")
    assert p["unique_id"] == "nexus_panel1_proximity"
    assert p["command_topic"] == "t/cmd"
    assert p["device"]["model"] == "NSPanel Pro"
