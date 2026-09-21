#!/usr/bin/env bash
set -euo pipefail

set +e
bash tools/q1_emulator_smoke_recover.sh
rc=$?
set -e

if [ "$rc" -eq 0 ]; then
  exit 0
fi

# Only the known launcher-widget placement failure class may use the independent
# platform requestPinAppWidget fallback. Any unrelated launch/render/crash error
# remains RED instead of being masked by a second path.
if [ "$rc" -eq 22 ]; then
  echo "Primary hosted-widget placement returned rc=22; trying bounded public pin fallback."
  exec bash tools/q1_pin_probe.sh
fi

echo "Q5 runtime core failed outside the bounded widget-placement fallback class: rc=$rc" >&2
exit "$rc"
