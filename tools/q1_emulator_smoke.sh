#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"
ACTIVITY="$PACKAGE/.ui.activities.MainActivity"
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"

test -n "$APK"

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

mkdir -p q1-evidence
adb exec-out screencap -p > q1-evidence/main-activity.png || true
adb logcat -d > q1-evidence/logcat.txt
adb shell dumpsys package "$PACKAGE" > q1-evidence/package.txt
adb shell cmd appwidget help > q1-evidence/appwidget-help.txt 2>&1 || true

if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt; then
  echo "Fatal exception detected for $PACKAGE" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat.txt >&2 || true
  exit 10
fi

echo "Q1 launch smoke PASS (widget-host placement remains a separate evidence item)."
