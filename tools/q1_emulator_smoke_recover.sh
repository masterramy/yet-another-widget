#!/usr/bin/env bash
set -euo pipefail

APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
TEST_CLASS="com.tommasoberlose.anotherwidget.LauncherWidgetDragTest"
test -n "$APP_APK"
source tools/q1_runtime_identity.sh
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
APP_LABEL="$YAW_APP_LABEL"

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

echo "== Q1 placement recovery: UiAutomation coherent Launcher pointer drag =="
# The base smoke has already proved build/install/render/provider discovery.
# Runs 40-43 also proved that one-shot draganddrop and separate shell motionevent
# processes do not establish Launcher3's widget drag lifecycle reliably. Build
# and install a Q1-only instrumentation helper, then inject one coherent pointer
# stream from a single UiAutomation process. Shipping app source is untouched.
TEST_SOURCE="app/src/androidTest/java/com/tommasoberlose/anotherwidget/LauncherWidgetDragTest.kt"
mkdir -p "$(dirname "$TEST_SOURCE")"
cat > "$TEST_SOURCE" <<'KOTLIN'
package com.tommasoberlose.anotherwidget

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Q1-only launcher harness. It injects one coherent touchscreen pointer stream globally. */
@RunWith(AndroidJUnit4::class)
class LauncherWidgetDragTest {
    @Test
    fun injectWidgetDrag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()

        fun coordinate(name: String): Float = requireNotNull(args.getString(name)) {
            "Missing instrumentation argument: $name"
        }.toFloat()

        val sourceX = coordinate("sourceX")
        val sourceY = coordinate("sourceY")
        val edgeY = coordinate("edgeY")
        val targetX = coordinate("targetX")
        val targetY = coordinate("targetY")
        val ui = instrumentation.uiAutomation
        val downTime = SystemClock.uptimeMillis()

        fun inject(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                x,
                y,
                0
            )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("UiAutomation rejected MotionEvent action=$action x=$x y=$y", ui.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }

        fun movePath(fromX: Float, fromY: Float, toX: Float, toY: Float, steps: Int, delayMs: Long) {
            for (step in 1..steps) {
                val fraction = step.toFloat() / steps.toFloat()
                inject(
                    MotionEvent.ACTION_MOVE,
                    fromX + (toX - fromX) * fraction,
                    fromY + (toY - fromY) * fraction
                )
                SystemClock.sleep(delayMs)
            }
        }

        inject(MotionEvent.ACTION_DOWN, sourceX, sourceY)
        // Hold well past Launcher3's widget-preview long-press threshold.
        SystemClock.sleep(1600)

        // Cross the picker-to-workspace boundary while the same pointer remains down.
        movePath(sourceX, sourceY, targetX, edgeY, steps = 24, delayMs = 30)
        SystemClock.sleep(800)

        // Once HOME is exposed, move into the known-empty 4x1 row and release there.
        movePath(targetX, edgeY, targetX, targetY, steps = 12, delayMs = 35)
        SystemClock.sleep(300)
        inject(MotionEvent.ACTION_UP, targetX, targetY)
        SystemClock.sleep(1000)
    }
}
KOTLIN

# Rebuild only the Q1 test APK after adding the harness source. This mutation is
# runner-local and does not alter shipping app/product source bytes.
./gradlew --no-daemon :app:assembleDebugAndroidTest | tee q1-evidence/androidtest-rebuild.txt
if [ ! -f "$TEST_APK" ]; then
  echo "Missing Q1 androidTest APK after harness rebuild: $TEST_APK" >&2
  exit 30
fi
yaw_read_test_identity "$TEST_APK"
TEST_RUNNER="$YAW_TEST_PACKAGE/$YAW_TEST_RUNNER_CLASS"
adb install -r "$TEST_APK" | tee q1-evidence/androidtest-install.txt

adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input swipe 540 1250 540 1250 1600
sleep 3

rm -f /tmp/q1-ui.xml
adb shell uiautomator dump /data/local/tmp/q1-recovery.xml >/dev/null
adb pull /data/local/tmp/q1-recovery.xml /tmp/q1-ui.xml >/dev/null
widgets_xy="$(python3 - /tmp/q1-ui.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    hay=' '.join((n.attrib.get('text',''),n.attrib.get('content-desc',''),n.attrib.get('resource-id',''))).lower()
    if 'widgets' in hay:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
adb shell input tap $widgets_xy
sleep 3

adb shell uiautomator dump /data/local/tmp/q1-picker.xml >/dev/null
adb pull /data/local/tmp/q1-picker.xml /tmp/q1-picker.xml >/dev/null
another_xy="$(python3 - /tmp/q1-picker.xml "$APP_LABEL" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
label=sys.argv[2].strip().lower()
for n in root.iter('node'):
    hay=' '.join((n.attrib.get('text',''),n.attrib.get('content-desc',''))).lower()
    if label in hay:
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
adb shell input tap $another_xy
sleep 2

adb shell uiautomator dump /data/local/tmp/q1-expanded.xml >/dev/null
adb pull /data/local/tmp/q1-expanded.xml /tmp/q1-expanded.xml >/dev/null
source_xy="$(python3 - /tmp/q1-expanded.xml "$APP_LABEL" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
label=sys.argv[2].strip().lower()
for cell in root.iter('node'):
    if f'{label} widget' not in cell.attrib.get('content-desc','').lower():
        continue
    for n in cell.iter('node'):
        if n.attrib.get('resource-id','').endswith('/widget_preview_container'):
            m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
            if m:
                a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
echo "Recovery drag source: $source_xy" | tee q1-evidence/widget-drag-recovery.txt
read -r sx sy <<<"$source_xy"
echo "Recovery transition waypoint: 540 220" | tee -a q1-evidence/widget-drag-recovery.txt
echo "Recovery drag target: 540 700" | tee -a q1-evidence/widget-drag-recovery.txt

set +e
adb shell am instrument -w -r \
  -e class "$TEST_CLASS" \
  -e sourceX "$sx" \
  -e sourceY "$sy" \
  -e edgeY 220 \
  -e targetX 540 \
  -e targetY 700 \
  "$TEST_RUNNER" | tee q1-evidence/instrumentation-drag.txt
instr_rc=${PIPESTATUS[0]}
set -e
if [ "$instr_rc" -ne 0 ] || grep -q '^FAILURES!!!' q1-evidence/instrumentation-drag.txt || ! grep -Eq '^OK \([0-9]+ test' q1-evidence/instrumentation-drag.txt; then
  echo "Q1 UiAutomation drag instrumentation did not complete cleanly" >&2
  exit 30
fi

sleep 8
adb shell dumpsys appwidget > q1-evidence/appwidget-after-recovery.txt
adb shell dumpsys window > q1-evidence/window-after-recovery.txt 2>&1 || true
adb exec-out screencap -p > q1-evidence/widget-home-recovery.png || true
adb logcat -d > q1-evidence/logcat-after-recovery.txt 2>&1 || true

python3 - q1-evidence/appwidget-after-recovery.txt "$PACKAGE" <<'PY'
import re,sys
text=open(sys.argv[1],errors='replace').read(); package=sys.argv[2]
if 'Widgets:' not in text or 'Hosts:' not in text: raise SystemExit(2)
section=text.split('Widgets:',1)[1].split('Hosts:',1)[0]
for block in re.split(r'(?m)^\s*\[\d+\]\s+id=\d+\s*$',section)[1:]:
    if package in block and 'MainWidget' in block and ('pkg:com.android.launcher3' in block or 'pkg:com.google.android.apps.nexuslauncher' in block):
        print(block.strip())
        open('q1-evidence/main-widget-binding-recovery.txt','w').write(block.strip()+'\n')
        raise SystemExit(0)
raise SystemExit(22)
PY

if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}" q1-evidence/logcat-after-recovery.txt; then
  echo "Fatal exception detected after recovery widget placement" >&2
  exit 23
fi

echo "Q1 real Launcher-hosted MainWidget placement GREEN via UiAutomation recovery drag."
