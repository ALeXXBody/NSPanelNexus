#!/usr/bin/env bash
# HIGH PRIORITY gate (ledger rule 11): everything implemented or changed
# must be properly tested BEFORE any release.
#
# Usage: scripts/pre_release_check.sh
# Exit 0 = safe to bump/release. Any failure = fix first, no exceptions.
#
# Covers everything automatable locally. Live-panel verification (ledger
# rule 3) still applies on top for panel-behaviour changes.
set -u
set -o pipefail
cd "$(dirname "$0")/.."

PASS=0
FAIL=0
step() { # step "name" command...
    local name="$1"; shift
    echo "=== $name ==="
    if "$@" 2>&1 | tail -5; then
        echo "--- PASS: $name"; PASS=$((PASS + 1))
    else
        echo "--- FAIL: $name"; FAIL=$((FAIL + 1))
    fi
}

step "ruff (HA integration + tests)" ruff check custom_components tests
step "pytest (20 regression tests)" python3 -m pytest tests/ -q
step "node --check (sidebar panel.js)" node --check custom_components/nspanel_nexus/www/nspanel-nexus-panel.js
step "services.yaml parses" python3 -c "import yaml;yaml.safe_load(open('custom_components/nspanel_nexus/services.yaml'))"
step "strings.json parses" python3 -c "import json;json.load(open('custom_components/nspanel_nexus/strings.json'))"
step "manifest+hacs valid" python3 -c "import json;json.load(open('custom_components/nspanel_nexus/manifest.json'));json.load(open('hacs.json'))"
step "kotlin compiles" /opt/gradle-8.7/bin/gradle -p app :app:compileReleaseKotlin --no-daemon -q

echo
echo "PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -ne 0 ]; then
    echo "GATE RED — do NOT bump, release, or install. Fix the failures above."
    exit 1
fi
echo "GATE GREEN — safe to bump/release."
