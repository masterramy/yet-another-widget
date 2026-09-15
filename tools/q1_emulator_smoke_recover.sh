#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
TEST_RUNNER="com.tommasoberlose.anotherwidget.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS="com.tommasoberlose.anotherwidget.Q1PinWidgetTest"

set +e
bash tools/q1_emulator_smoke.sh
rc=$?
set -e
if [ "$rc" -eq 0 ]; then
  exit 0
fi
if [ "$rc" -ne 22 ]; then
  exit "$rc"
fi

echo "== Q1 placement recovery: platform requestPinAppWidget flow =="
# The base smoke has already proved build/install/render/provider discovery and
# Run 44 proved that a real Launcher3 drag lifecycle can begin without producing
# a final host binding under the headless runner. Exercise Android's public pin
# contract instead: the app requests its real MainWidget, Launcher3 presents its
# real confirmation UI, and the harness accepts that UI exactly as a user would.
# This source is generated runner-side and never enters shipping app bytes.
TEST_SOURCE="app/src/androidTest/java/com/tommasoberlose/anotherwidget/Q1PinWidgetTest.kt"
mkdir -p "$(dirname "$TEST_SOURCE")"
cat > "$TEST_SOURCE" <<'KOTLIN'
package com.tommasoberlose.anotherwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tommasoberlose.anotherwidget.ui.widgets.MainWidget
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Q1-only harness for Android's public pinned-widget request contract. */
@RunWith(AndroidJUnit4::class)
class Q1PinWidgetTest {
    @Test
    fun requestPinMainWidget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = AppWidgetManager.getInstance(context)
        assertTrue(
            "Default launcher does not advertise requestPinAppWidget support",
            manager.isRequestPinAppWidgetSupported
        )
        val accepted = manager.requestPinAppWidget(
            ComponentName(context, MainWidget::class.java),
            null,
            null
        )
        assertTrue("Launcher rejected requestPinAppWidget for MainWidget", accepted)
        // Keep instrumentation alive while the shell verifies and accepts the
        // real launcher confirmation surface.
        SystemClock.sleep(30000)
    }
}
KOTLIN

./gradlew --no-daemon :app:assembleDebugAndroidTest | tee q1-evidence/androidtest-pin-rebuild.txt
if [ ! -f "$TEST_APK" ]; then
  echo "Missing Q1 androidTest APK after pin harness rebuild: $TEST_APK" >&2
  exit 30
fi
adb install -r "$TEST_APK" | tee q1-evidence/androidtest-pin-install.txt

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell dumpsys window > q1-evidence/window-before-pin.txt 2>&1 || true
adb exec-out screencap -p > q1-evidence/home-before-pin.png || true

# Launch instrumentation asynchronously because it intentionally remains alive
# while Launcher3's confirmation dialog is on screen.
set +e
adb shell am instrument -w -r -e class "$TEST_CLASS" "$TEST_RUNNER" \
  > q1-evidence/instrumentation-pin.txt 2>&1 &
instr_pid=$!
set -e

confirm_xy=""
confirm_xml=""
for attempt in $(seq 1 20); do
  sleep 1
  remote="/data/local/tmp/q1-pin-${attempt}.xml"
  local_xml="q1-evidence/pin-confirm-${attempt}.xml"
  set +e
  adb shell rm -f "$remote" >/dev/null 2>&1
  adb shell uiautomator dump "$remote" > "q1-evidence/pin-confirm-${attempt}-uiautomator.txt" 2>&1
  dump_rc=$?
  adb pull "$remote" "$local_xml" >> "q1-evidence/pin-confirm-${attempt}-uiautomator.txt" 2>&1
  pull_rc=$?
  set -e
  if [ "$dump_rc" -ne 0 ] || [ "$pull_rc" -ne 0 ] || [ ! -s "$local_xml" ]; then
    continue
  fi

  set +e
  candidate="$(python3 - "$local_xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
choices = []
for node in root.iter('node'):
    package = node.attrib.get('package', '').lower()
    if package not in ('com.android.launcher3', 'com.google.android.apps.nexuslauncher'):
        continue
    label = (node.attrib.get('text', '') or node.attrib.get('content-desc', '')).strip().lower()
    if label == 'add automatically':
        rank = 0
    elif label == 'add':
        rank = 1
    else:
        continue
    match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds', ''))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    choices.append((rank, (x1+x2)//2, (y1+y2)//2, label))
if not choices:
    raise SystemExit(1)
choices.sort()
_, x, y, label = choices[0]
print(x, y, label)
PY
)"
  candidate_rc=$?
  set -e
  if [ "$candidate_rc" -eq 0 ] && [ -n "$candidate" ]; then
    read -r cx cy clabel <<<"$candidate"
    confirm_xy="$cx $cy"
    confirm_xml="$local_xml"
    echo "Launcher pin confirmation found on attempt $attempt: '$clabel' at $confirm_xy" | tee q1-evidence/pin-confirmation.txt
    cp "$local_xml" q1-evidence/pin-confirm.xml
    adb exec-out screencap -p > q1-evidence/pin-confirm.png || true
    adb shell dumpsys window > q1-evidence/window-pin-confirm.txt 2>&1 || true
    break
  fi
done

if [ -z "$confirm_xy" ]; then
  echo "Launcher pin confirmation did not appear" >&2
  adb shell dumpsys appwidget > q1-evidence/appwidget-pin-no-confirm.txt 2>&1 || true
  adb shell dumpsys window > q1-evidence/window-pin-no-confirm.txt 2>&1 || true
  adb exec-out screencap -p > q1-evidence/pin-no-confirm.png || true
  set +e
  wait "$instr_pid"
  set -e
  cat q1-evidence/instrumentation-pin.txt >&2 || true
  exit 31
fi

adb shell input tap $confirm_xy
sleep 8
adb shell dumpsys appwidget > q1-evidence/appwidget-after-pin.txt
adb shell dumpsys window > q1-evidence/window-after-pin.txt 2>&1 || true
adb exec-out screencap -p > q1-evidence/widget-home-pin.png || true
adb logcat -d > q1-evidence/logcat-after-pin.txt 2>&1 || true

# The pin request test sleeps for at most 30 seconds. Finish collecting its
# result, but do not treat instrumentation green as widget-placement proof.
set +e
wait "$instr_pid"
instr_rc=$?
set -e
if [ "$instr_rc" -ne 0 ] || grep -q '^FAILURES!!!' q1-evidence/instrumentation-pin.txt || ! grep -Eq '^OK \([0-9]+ test' q1-evidence/instrumentation-pin.txt; then
  echo "Q1 requestPinAppWidget instrumentation did not complete cleanly" >&2
  cat q1-evidence/instrumentation-pin.txt >&2 || true
  exit 30
fi

# Fail closed on the actual AppWidgetService state. Provider registration alone
# is not enough: a concrete Widgets: record must pair MainWidget with Launcher3.
python3 - q1-evidence/appwidget-after-pin.txt "$PACKAGE" <<'PY'
import re, sys
text = open(sys.argv[1], errors='replace').read()
package = sys.argv[2]
if 'Widgets:' not in text or 'Hosts:' not in text:
    raise SystemExit(2)
section = text.split('Widgets:', 1)[1].split('Hosts:', 1)[0]
blocks = re.split(r'(?m)^\s*\[\d+\]\s+id=\d+\s*$', section)[1:]
for block in blocks:
    if package in block and 'MainWidget' in block and ('pkg:com.android.launcher3' in block or 'pkg:com.google.android.apps.nexuslauncher' in block):
        clean = block.strip()
        print(clean)
        open('q1-evidence/main-widget-binding-pin.txt', 'w').write(clean + '\n')
        raise SystemExit(0)
raise SystemExit(22)
PY

# The confirmation surface must also be gone after acceptance.
set +e
adb shell uiautomator dump /data/local/tmp/q1-pin-post.xml > q1-evidence/pin-post-uiautomator.txt 2>&1
post_dump_rc=$?
adb pull /data/local/tmp/q1-pin-post.xml q1-evidence/pin-post.xml >> q1-evidence/pin-post-uiautomator.txt 2>&1
post_pull_rc=$?
set -e
if [ "$post_dump_rc" -eq 0 ] && [ "$post_pull_rc" -eq 0 ] && [ -s q1-evidence/pin-post.xml ]; then
  if grep -Eqi 'text="(Add automatically|Add)"' q1-evidence/pin-post.xml; then
    echo "Launcher pin confirmation remained open after tap" >&2
    exit 22
  fi
fi

if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-pin.txt; then
  echo "Fatal exception detected after pinned widget placement" >&2
  grep -n -A40 -B5 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-pin.txt >&2 || true
  exit 23
fi

echo "Q1 real Launcher-hosted MainWidget placement GREEN via requestPinAppWidget confirmation."
