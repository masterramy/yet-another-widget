#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"
ACTIVITY="$PACKAGE/.ui.activities.MainActivity"
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"

test -n "$APK"
rm -rf q1-evidence
mkdir -p q1-evidence

wake_and_unlock() {
  adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  sleep 1
}

wait_for_launcher_ready() {
  echo "== wait for stable Android launcher/SystemUI =="
  adb wait-for-device
  wake_and_unlock
  local deadline=$((SECONDS + 300))
  local focus=""
  while [ "$SECONDS" -lt "$deadline" ]; do
    local boot
    boot="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [ "$boot" = "1" ]; then
      wake_and_unlock
      adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
      sleep 2
      focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus' || true)"
      if echo "$focus" | grep -Eqi 'launcher|nexuslauncher'; then
        adb shell am wait-for-broadcast-idle >/dev/null 2>&1 || true
        sleep 5
        wake_and_unlock
        adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
        sleep 2
        focus="$(adb shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus' || true)"
        if echo "$focus" | grep -Eqi 'launcher|nexuslauncher'; then
          echo "Launcher ready: $focus"
          return 0
        fi
      fi
    fi
    sleep 3
  done
  echo "Launcher/SystemUI never reached a stable HOME focus" >&2
  adb shell dumpsys window > q1-evidence/window-not-ready.txt 2>&1 || true
  adb shell dumpsys power > q1-evidence/power-not-ready.txt 2>&1 || true
  adb exec-out screencap -p > q1-evidence/system-not-ready.png 2>/dev/null || true
  return 1
}

ui_dump() {
  local name="$1"
  local remote="/data/local/tmp/${name}.xml"
  set +e
  adb shell rm -f "$remote" >/dev/null 2>&1
  adb shell uiautomator dump "$remote" >"q1-evidence/${name}-uiautomator.txt" 2>&1
  local rc=$?
  adb pull "$remote" "q1-evidence/${name}.xml" >>"q1-evidence/${name}-uiautomator.txt" 2>&1
  local pull_rc=$?
  adb exec-out screencap -p > "q1-evidence/${name}.png" 2>>"q1-evidence/${name}-uiautomator.txt"
  set -e
  if [ "$rc" -ne 0 ] || [ "$pull_rc" -ne 0 ] || [ ! -s "q1-evidence/${name}.xml" ]; then
    echo "UI dump failed for ${name} (uiautomator=$rc pull=$pull_rc)" >&2
    cat "q1-evidence/${name}-uiautomator.txt" >&2 || true
    return 1
  fi
}

capture_launch_diagnostics() {
  adb logcat -d > q1-evidence/logcat.txt 2>&1 || true
  adb shell dumpsys activity activities > q1-evidence/activities.txt 2>&1 || true
  adb shell dumpsys window > q1-evidence/window.txt 2>&1 || true
  adb shell dumpsys package "$PACKAGE" > q1-evidence/package.txt 2>&1 || true
  adb shell pidof "$PACKAGE" > q1-evidence/pidof.txt 2>&1 || true
  adb shell ps -A > q1-evidence/ps.txt 2>&1 || true
  {
    echo "current_focus:"
    grep -m1 'mCurrentFocus' q1-evidence/window.txt || true
    echo "resumed_activity:"
    grep -m2 -E 'mResumedActivity|topResumedActivity|ResumedActivity' q1-evidence/activities.txt || true
    echo "package_activity_matches:"
    grep -n -A5 -B3 "$PACKAGE" q1-evidence/activities.txt | head -n 120 || true
    echo "pidof:"
    cat q1-evidence/pidof.txt || true
  } > q1-evidence/launch-summary.txt
}

app_shell_visible_from_system_state() {
  [ -s q1-evidence/pidof.txt ] || return 1
  [ -s q1-evidence/main-activity-ui.png ] || return 1
  grep -E -q "topResumedActivity=.*${PACKAGE//./\\.}.*MainActivity|mResumedActivity=.*${PACKAGE//./\\.}.*MainActivity|ResumedActivity.*${PACKAGE//./\\.}.*MainActivity" q1-evidence/activities.txt || return 1
  grep -q 'state=RESUMED' q1-evidence/activities.txt || return 1
  grep -q 'reportedDrawn=true' q1-evidence/activities.txt || return 1
  grep -E -q 'nowVisible=true|mVisibleRequested=true mVisible=true' q1-evidence/activities.txt || return 1
  grep -E -q "mCurrentFocus=.*${PACKAGE//./\\.}.*MainActivity" q1-evidence/window.txt || return 1
  if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt; then
    return 1
  fi
  return 0
}

node_center() {
  local xml="$1"
  local needle="$2"
  python3 - "$xml" "$needle" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, needle = sys.argv[1], sys.argv[2].lower()
try:
    root = ET.parse(path).getroot()
except Exception:
    raise SystemExit(2)
for node in root.iter('node'):
    hay = ' '.join([node.attrib.get('text',''), node.attrib.get('content-desc',''), node.attrib.get('resource-id','')]).lower()
    if needle in hay:
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
raise SystemExit(1)
PY
}

tap_node() {
  local xml="$1" needle="$2"
  local xy
  xy="$(node_center "$xml" "$needle")" || return 1
  adb shell input tap $xy
}

find_drag_source() {
  local xml="$1"
  python3 - "$xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for cell in root.iter('node'):
    desc = cell.attrib.get('content-desc', '').lower()
    klass = cell.attrib.get('class', '').lower()
    if 'another widget widget' not in desc or 'widgetcell' not in klass:
        continue
    for node in cell.iter('node'):
        if node.attrib.get('resource-id', '').endswith('/widget_preview_container'):
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
            if m:
                x1,y1,x2,y2 = map(int,m.groups())
                print((x1+x2)//2, (y1+y2)//2)
                raise SystemExit(0)
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', cell.attrib.get('bounds',''))
    if m:
        x1,y1,x2,y2 = map(int,m.groups())
        print((x1+x2)//2, (y1+y2)//2)
        raise SystemExit(0)
raise SystemExit(1)
PY
}

main_widget_bound_to_launcher() {
  local dump="$1"
  python3 - "$dump" "$PACKAGE" <<'PY'
import re, sys
path, package = sys.argv[1:]
text = open(path, errors='replace').read()
if 'Widgets:' not in text or 'Hosts:' not in text:
    raise SystemExit(2)
section = text.split('Widgets:', 1)[1].split('Hosts:', 1)[0]
blocks = re.split(r'(?m)^\s*Widget \[\d+\]:\s*$', section)[1:]
for block in blocks:
    if package in block and 'MainWidget' in block and ('pkg:com.android.launcher3' in block or 'pkg:com.google.android.apps.nexuslauncher' in block):
        print(block.strip())
        raise SystemExit(0)
raise SystemExit(1)
PY
}

wait_for_launcher_ready || exit 18

echo "== install =="
adb install -r "$APK"
adb shell pm path "$PACKAGE"

echo "== launch settings shell =="
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY"
sleep 7
main_ui_dump_ok=1
if ! ui_dump main-activity-ui; then
  main_ui_dump_ok=0
fi
capture_launch_diagnostics

if [ "$main_ui_dump_ok" -eq 1 ]; then
  if ! grep -qi 'Another Widget' q1-evidence/main-activity-ui.xml; then
    echo "MainActivity did not render the expected app shell" >&2
    cat q1-evidence/launch-summary.txt >&2 || true

    if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt; then
      echo "Classification: app process fatal during/after launch" >&2
      grep -n -A50 -B8 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt >&2 || true
      exit 26
    fi

    if [ ! -s q1-evidence/pidof.txt ]; then
      echo "Classification: app process absent after successful am start; inspect lifecycle/activity logs" >&2
      grep -n -E "ActivityTaskManager|ActivityManager|am_finish_activity|am_destroy_activity|Force finishing|${PACKAGE//./\\.}" q1-evidence/logcat.txt | tail -n 160 >&2 || true
      exit 27
    fi

    if ! grep -q "$PACKAGE" q1-evidence/activities.txt; then
      echo "Classification: app process alive but no package activity remains in activity task state" >&2
      grep -n -E "ActivityTaskManager|ActivityManager|am_finish_activity|am_destroy_activity|${PACKAGE//./\\.}" q1-evidence/logcat.txt | tail -n 160 >&2 || true
      exit 28
    fi

    echo "Classification: app process/activity state exists but launcher owns visible focus; inspect window/activity routing evidence" >&2
    exit 29
  fi
else
  if app_shell_visible_from_system_state; then
    echo "UiAutomator XML unavailable; accepting fail-closed rendered foreground MainActivity evidence."
  else
    echo "MainActivity UI XML unavailable and system-state fallback did not prove a rendered foreground shell" >&2
    cat q1-evidence/launch-summary.txt >&2 || true
    exit 24
  fi
fi

adb shell dumpsys package "$PACKAGE" | grep -E 'MainWidget|MainActivity|versionName|targetSdk' || true
adb shell dumpsys activity activities | grep -A4 -B2 "$PACKAGE" || true
cp q1-evidence/main-activity-ui.png q1-evidence/main-activity.png
adb shell cmd appwidget help > q1-evidence/appwidget-help.txt 2>&1 || true

if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt; then
  echo "Fatal exception detected for $PACKAGE" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt >&2 || true
  exit 10
fi

echo "== real launcher widget-picker placement =="
wait_for_launcher_ready || exit 18
adb exec-out screencap -p > q1-evidence/launcher-home.png || true
# Long-press an empty central area of the Pixel/Launcher3 home screen.
adb shell input swipe 540 1250 540 1250 1600
sleep 3
if ! ui_dump launcher-longpress; then
  exit 19
fi
if ! tap_node q1-evidence/launcher-longpress.xml "widgets"; then
  echo "Launcher widget entry not found" >&2
  grep -o 'text="[^"]*"\|content-desc="[^"]*"\|resource-id="[^"]*"' q1-evidence/launcher-longpress.xml >&2 || true
  exit 20
fi
sleep 3
ui_dump widget-picker || exit 19

if tap_node q1-evidence/widget-picker.xml "another widget"; then
  sleep 2
  ui_dump widget-picker-expanded || exit 19
else
  cp q1-evidence/widget-picker.xml q1-evidence/widget-picker-expanded.xml
fi

source_xy="$(find_drag_source q1-evidence/widget-picker-expanded.xml)" || {
  echo "Another Widget preview drag source not found in launcher widget picker" >&2
  exit 21
}
echo "Another Widget preview drag source: $source_xy" | tee q1-evidence/widget-drag.txt
# Android's draganddrop command deliberately holds for the platform long-press timeout
# before moving, unlike a plain swipe (which Launcher3 interprets as list scrolling).
adb shell input draganddrop $source_xy 540 650 1800
sleep 8
ui_dump post-widget-drag || true
adb shell dumpsys appwidget > q1-evidence/appwidget-after-placement.txt
adb shell dumpsys window > q1-evidence/window-after-placement.txt 2>&1 || true
adb exec-out screencap -p > q1-evidence/widget-home.png || true

if [ -s q1-evidence/post-widget-drag.xml ] && grep -q 'primary_widgets_list_view' q1-evidence/post-widget-drag.xml; then
  echo "Launcher widget picker remained open after drag-and-drop" >&2
  exit 22
fi

if ! main_widget_bound_to_launcher q1-evidence/appwidget-after-placement.txt > q1-evidence/main-widget-binding.txt; then
  echo "Launcher did not bind MainWidget after real picker drag" >&2
  grep -n -A16 -B16 -E "${PACKAGE//./\\.}|MainWidget|Widgets:|Hosts:" q1-evidence/appwidget-after-placement.txt >&2 || true
  exit 22
fi
cat q1-evidence/main-widget-binding.txt

adb logcat -d > q1-evidence/logcat-after-widget.txt
if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-widget.txt; then
  echo "Fatal exception detected after widget placement" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-widget.txt >&2 || true
  exit 23
fi

echo "Q1 API36 build/install/visible-shell/real launcher widget-placement smoke PASS."
