#!/usr/bin/env bash
set -euo pipefail

set +e
bash tools/q1_emulator_smoke_recover.sh
rc=$?
set -e

if [ "$rc" -eq 0 ]; then
  exit 0
fi

# Only known launcher-widget placement failure classes may use the independent
# platform requestPinAppWidget fallback. rc=21 means the hosted picker did not
# expose a draggable preview for the app; rc=22 means picker placement/binding
# did not complete. Any unrelated launch/render/crash error remains RED instead
# of being masked by a second path.
if [ "$rc" -eq 21 ] || [ "$rc" -eq 22 ]; then
  echo "Primary hosted-widget placement returned rc=$rc; trying bounded public pin fallback."
  exec bash tools/q1_pin_probe.sh
fi

echo "Q5 runtime core failed outside the bounded widget-placement fallback class: rc=$rc" >&2
exit "$rc"
