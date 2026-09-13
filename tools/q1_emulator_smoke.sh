#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"
ACTIVITY="$PACKAGE/.ui.activities.MainActivity"
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"

test -n "$APK"
mkdir -p q1-evidence

ui_dump() {
  local name="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "q1-evidence/${name}.xml" >/dev/null 2>&1 || true
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
cands=[]
for node in root.iter('node'):
    text=' '.join([node.attrib.get('text',''),node.attrib.get('content-desc',''),node.attrib.get('resource-id','')]).lower()
    m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',node.attrib.get('bounds',''))
    if not m: continue
    x1,y1,x2,y2=map(int,m.groups())
    if ('another widget' in text or 'widgetcell' in text or 'widget_cell' in text) and (x2-x1)>80 and (y2-y1)>80:
        cands.append(((x2-x1)*(y2-y1), (x1+x2)//2, (y1+y2)//2, text))
if not cands:
    raise SystemExit(1)
# Prefer the largest visible candidate after the app row has been expanded.
_,x,y,_=sorted(cands, reverse=True)[0]
print(x,y)
PY
}

echo "== install =="
adb install -r "$APK"
adb shell pm path "$PACKAGE"

echo "== launch settings shell =="
adb logcat -c
adb shell am force-stop "$PACKAGE" || true
adb shell am start -W -n "$ACTIVITY"
sleep 3

adb shell dumpsys package "$PACKAGE" | grep -E 'MainWidget|MainActivity|versionName|targetSdk' || true
adb shell dumpsys activity activities | grep -A4 -B2 "$PACKAGE" || true
adb exec-out screencap -p > q1-evidence/main-activity.png || true
adb logcat -d > q1-evidence/logcat.txt
adb shell dumpsys package "$PACKAGE" > q1-evidence/package.txt
adb shell cmd appwidget help > q1-evidence/appwidget-help.txt 2>&1 || true

if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt; then
  echo "Fatal exception detected for $PACKAGE" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt >&2 || true
  exit 10
fi

echo "== real launcher widget-picker placement =="
adb shell input keyevent KEYCODE_HOME
sleep 2
# Long-press an empty central area of the Pixel/Launcher3 home screen.
adb shell input swipe 540 1250 540 1250 1200
sleep 2
ui_dump launcher-longpress
if ! tap_node q1-evidence/launcher-longpress.xml "widgets"; then
  echo "Launcher widget entry not found" >&2
  exit 20
fi
sleep 3
ui_dump widget-picker

# Pixel Launcher groups widgets by application. Expand Another Widget if such a row is present.
if tap_node q1-evidence/widget-picker.xml "another widget"; then
  sleep 2
  ui_dump widget-picker-expanded
else
  cp q1-evidence/widget-picker.xml q1-evidence/widget-picker-expanded.xml
fi

source_xy="$(find_drag_source q1-evidence/widget-picker-expanded.xml)" || {
  echo "Another Widget drag source not found in launcher widget picker" >&2
  exit 21
}
# Drag the real launcher widget cell to the upper half of the workspace and hold long enough
# for Launcher3 to cross the picker -> workspace transition.
adb shell input swipe $source_xy 540 650 1800
sleep 5
ui_dump post-widget-drag
adb shell dumpsys appwidget > q1-evidence/appwidget-after-placement.txt
adb exec-out screencap -p > q1-evidence/widget-home.png || true

# A true Q1 placement pass requires Launcher3 to have bound an appWidgetId to MainWidget.
if ! grep -A8 -B8 -E "${PACKAGE//./\\.}/.*MainWidget|MainWidget" q1-evidence/appwidget-after-placement.txt | grep -q -E "appWidgetId|hostId|HostId|provider"; then
  echo "Launcher did not bind MainWidget after real picker drag" >&2
  grep -n -A12 -B12 -E "${PACKAGE//./\\.}|MainWidget" q1-evidence/appwidget-after-placement.txt >&2 || true
  exit 22
fi

# Ensure the widget/app did not crash during provider bind/update/render.
adb logcat -d > q1-evidence/logcat-after-widget.txt
if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-widget.txt; then
  echo "Fatal exception detected after widget placement" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-widget.txt >&2 || true
  exit 23
fi

echo "Q1 API36 build/install/launch/real launcher widget-placement smoke PASS."
