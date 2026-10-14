#!/usr/bin/env bash
# E7 scoped genuine OS date-change probe, real configured Pixel Launcher widget.
# Never infer delivery/visual PASS from DateHelper formatting alone.
set -euo pipefail
mkdir -p q1-evidence
bash tools/q1_emulator_smoke_recover.sh
source tools/q1_runtime_identity.sh
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
adb root > q1-evidence/e7-adb-root.txt 2>&1 || true
adb wait-for-device
adb shell settings put global auto_time 0
adb shell settings put global auto_time_zone 0
adb shell setprop persist.sys.timezone UTC
sleep 3
cleanup() {
  adb shell settings put global auto_time 1 >/dev/null 2>&1 || true
  adb shell settings put global auto_time_zone 1 >/dev/null 2>&1 || true
}
trap cleanup EXIT
time_set() {
  local stamp="$1" unix expected actual method
  unix="$(python3 - "$stamp" <<'PY'
from datetime import datetime,timezone
import sys
print(int(datetime.strptime(sys.argv[1],"%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc).timestamp()))
PY
)"
  expected="$unix"
  if adb shell cmd alarm set-time "$((unix*1000))" >q1-evidence/e7-time-command.txt 2>&1; then
    method="cmd alarm set-time"
  elif adb shell date -u -s "@$unix" >q1-evidence/e7-time-command.txt 2>&1; then
    method="date -u -s"
  else
    echo "E7_CAPABILITY_UNSUPPORTED: cannot change Android OS clock in this emulator" > q1-evidence/e7-result.txt
    cat q1-evidence/e7-time-command.txt >&2
    return 70
  fi
  sleep 3
  actual="$(adb shell date -u +%s | tr -d '\r')"
  test "$actual" -ge "$expected"
  test "$actual" -le "$((expected+45))"
  printf 'requested=%s\nactual_epoch=%s\nmethod=%s\n' "$stamp" "$actual" "$method" >> q1-evidence/e7-clock-change.txt
}
capture() {
  local stem="$1" remote="/data/local/tmp/$1.xml" i
  adb shell input keyevent KEYCODE_HOME
  sleep 9
  for i in $(seq 1 8); do
    adb shell uiautomator dump "$remote" >"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
      adb pull "$remote" "q1-evidence/$stem.xml" >>"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
      test -s "q1-evidence/$stem.xml" && break
    sleep 2
  done
  test -s "q1-evidence/$stem.xml"
  adb exec-out screencap -p > "q1-evidence/$stem.png"
  adb shell dumpsys appwidget > "q1-evidence/$stem-appwidget.txt"
  grep -Fq "pkg:com.google.android.apps.nexuslauncher" "q1-evidence/$stem-appwidget.txt"
  grep -Fq "$PACKAGE/$PACKAGE.ui.widgets.MainWidget" "q1-evidence/$stem-appwidget.txt"
  grep -Fq "views=android.widget.RemoteViews" "q1-evidence/$stem-appwidget.txt"
  python3 - "q1-evidence/$stem.xml" "$YAW_APP_LABEL" <<'PY'
import sys,xml.etree.ElementTree as ET
r=ET.parse(sys.argv[1]).getroot()
hosts=[n.attrib for n in r.iter("node") if n.attrib.get("class","").endswith("LauncherAppWidgetHostView") and sys.argv[2] in n.attrib.get("content-desc","")]
if len(hosts)!=1:raise AssertionError(f"Expected one configured actual Pixel Launcher host; found {len(hosts)}")
print(hosts[0]["bounds"])
PY
}
time_set '2026-12-31T23:58:00Z'
capture e7-before-midnight
time_set '2027-01-01T00:02:00Z'
capture e7-after-midnight
# Additional E7/E8 leap boundary: ONE OS clock set, followed by natural time passage.
# No second set-time command or synthetic DATE_CHANGED broadcast after the
# before frame. This differentiates true midnight tick from TIME_SET repaint.
time_set '2028-02-29T23:59:45Z'
capture e7-natural-leap-before
sleep 16
actual="$(adb shell date -u +%s | tr -d '\r')"
threshold="$(python3 - <<'PY'
from datetime import datetime,timezone
print(int(datetime(2028,3,1,tzinfo=timezone.utc).timestamp()))
PY
)"
test "$actual" -ge "$threshold"
test "$actual" -lt "$((threshold+90))"
capture e7-natural-leap-after
printf 'natural_tick_crossed_leap_midnight=true\nactual_utc_epoch=%s\nmanual_set_between_frames=false\n' "$actual" > q1-evidence/e7-natural-midnight-summary.txt
echo "E7 OS TIME_SET New Year rollover and natural Feb29-to-Mar1 midnight tick: screenshots and bound host captured. Native host text inspection remains REQUIRED." | tee q1-evidence/e7-result.txt
