#!/usr/bin/env bash
# Bump the Nexus version everywhere (single source — run this, not sed).
# Usage: bump_version.sh 0.4.35
set -eu
cd "$(dirname "$0")/.."
VER="${1:-}"
[ -z "$VER" ] && { echo "usage: bump_version.sh <version>"; exit 1; }
case "$VER" in 0.*.*) ;; *) echo "version must look like 0.4.35"; exit 1;; esac

python3 - "$VER" <<'PYEOF'
import json, pathlib, sys
ver = sys.argv[1]
for f in ["custom_components/nspanel_nexus/manifest.json", "hacs.json"]:
    d = json.load(open(f)); d["version"] = ver
    json.dump(d, open(f, "w"), indent=2); open(f, "a").write("\n")
    print(f, "->", ver)
p = pathlib.Path("custom_components/nspanel_nexus/www/nspanel-nexus-panel.js")
import re
s = p.read_text()
s2, n = re.subn(r'v0\.\d+\.\d+', f"v{ver}", s)
assert n >= 1, "no version string found in panel.js"
p.write_text(s2)
print(f"panel.js fallback -> v{ver} ({n} occurrence(s))")
PYEOF

# Android versionName (versionCode bumps by hand only when needed)
sed -i -E "s/versionName = \"0\.[0-9]+\.[0-9]+\"/versionName = \"$VER\"/" app/app/build.gradle.kts
grep -n "versionName" app/app/build.gradle.kts

echo "=== verify ==="
python3 -c "import json; print('manifest:', json.load(open('custom_components/nspanel_nexus/manifest.json'))['version'])"
python3 -c "import json; print('hacs:', json.load(open('hacs.json'))['version'])"
grep -o 'v0\.[0-9.]*' custom_components/nspanel_nexus/www/nspanel-nexus-panel.js | sort -u
