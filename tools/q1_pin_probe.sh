#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"
TEST_PACKAGE="${PACKAGE}.test"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
RUNNER="${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.tommasoberlose.anotherwidget.PinWidgetRequestTest"
mkdir -p q1-evidence

wake_and_unlock() {
  adb shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1 || true
  adb shell svc power stayon true >/dev/null 2>&1 || true
  adb shell locksettings set-disabled true >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
}

stabilize_launcher() {
  echo "== stabilize Launcher3 before pin request =="
  wake_and_unlock
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true

  local deadline=$((SECONDS + 90))
  local stable=0
  while [ "$SECONDS" -lt "$deadline" ]; do
    adb shell dumpsys window > q1-evidence/pin-launcher-window.txt 2>&1 || true

    # API-36 headless images can surface a Quickstep ANR while the target app is
    # otherwise fully resumed/drawn. That is runner infrastructure, not product
    # evidence. Restart only the launcher process and require a healthy HOME
    # focus before proceeding with the real public pin contract.
    if grep -q 'Application Not Responding: com.android.launcher3' q1-evidence/pin-launcher-window.txt; then
      echo "Quickstep ANR detected; restarting Launcher3 test host." | tee -a q1-evidence/pin-launcher-stabilization.txt
      adb shell am force-stop com.android.launcher3 >/dev/null 2>&1 || true
      sleep 2
      wake_and_unlock
      adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
      stable=0
      sleep 2
      continue
    fi

    local focus
    focus="$(grep -m1 'mCurrentFocus' q1-evidence/pin-launcher-window.txt || true)"
    if echo "$focus" | grep -Eqi 'com\.android\.launcher3|com\.google\.android\.apps\.nexuslauncher'; then
      stable=$((stable + 1))
      if [ "$stable" -ge 2 ]; then
        echo "Healthy launcher focus: $focus" | tee -a q1-evidence/pin-launcher-stabilization.txt
        return 0
      fi
    else
      stable=0
      wake_and_unlock
      adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
    fi
    sleep 2
  done

  echo "Launcher3 did not recover to a stable HOME focus" >&2
  adb exec-out screencap -p > q1-evidence/pin-launcher-not-stable.png 2>/dev/null || true
  return 1
}

echo "== Q1 bounded recovery: self-contained platform requestPinAppWidget flow =="

# The primary recovery path may fail before it reaches its runner-local
# androidTest build/install step (for example when a Launcher3 ANR steals window
# focus). Therefore this fallback owns its own complete test precondition.
./gradlew --no-daemon :app:assembleDebugAndroidTest | tee q1-evidence/pin-request-test-rebuild.txt
if [ ! -f "$TEST_APK" ]; then
  echo "Missing androidTest APK after pin-probe rebuild: $TEST_APK" >&2
  exit 30
fi
sha256sum "$TEST_APK" | tee q1-evidence/pin-request-test-apk-sha256.txt
adb install -r "$TEST_APK" | tee q1-evidence/pin-request-test-install.txt
adb shell pm path "$TEST_PACKAGE" | tee q1-evidence/pin-request-test-package.txt
adb shell pm list instrumentation | tee q1-evidence/pin-request-instrumentation-list.txt
if ! grep -Fq "$RUNNER" q1-evidence/pin-request-instrumentation-list.txt; then
  echo "Expected AndroidJUnitRunner instrumentation is not installed: $RUNNER" >&2
  exit 30
fi

stabilize_launcher || exit 32
adb logcat -c || true
adb shell dumpsys appwidget > q1-evidence/appwidget-before-pin-request.txt
adb exec-out screencap -p > q1-evidence/home-before-pin-request.png || true

# requestPinAppWidget must surface the real Launcher's confirmation UI. The
# test only asks the platform for the real MainWidget; Launcher3 owns the bind.
adb shell am instrument -w -r -e class "$CLASS" "$RUNNER" | tee q1-evidence/pin-request-instrumentation.txt
if grep -q '^FAILURES!!!' q1-evidence/pin-request-instrumentation.txt || ! grep -Eq '^OK \([0-9]+ test' q1-evidence/pin-request-instrumentation.txt; then
  echo "Pin-request instrumentation did not complete cleanly" >&2
  exit 30
fi

add_xy=""
for attempt in $(seq 1 20); do
  sleep 1
  remote="/data/local/tmp/q1-pin-${attempt}.xml"
  local_xml="q1-evidence/pin-request-ui-${attempt}.xml"
  set +e
  adb shell rm -f "$remote" >/dev/null 2>&1
  adb shell uiautomator dump "$remote" > "q1-evidence/pin-request-ui-${attempt}-uiautomator.txt" 2>&1
  dump_rc=$?
  adb pull "$remote" "$local_xml" >> "q1-evidence/pin-request-ui-${attempt}-uiautomator.txt" 2>&1
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
    text = (node.attrib.get('text','') + ' ' + node.attrib.get('content-desc','')).strip().lower()
    if text in {'add', 'add to home screen', 'add automatically'}:
        rank = 0
    elif text.startswith('add '):
        rank = 1
    else:
        continue
    match = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.attrib.get('bounds',''))
    if not match:
        continue
    x1,y1,x2,y2 = map(int, match.groups())
    choices.append((rank, (x1+x2)//2, (y1+y2)//2, text))
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
    add_xy="$cx $cy"
    cp "$local_xml" q1-evidence/pin-request-ui.xml
    echo "Launcher pin confirmation: '$clabel' at $add_xy" | tee q1-evidence/pin-request-confirmation.txt
    adb exec-out screencap -p > q1-evidence/pin-request-confirmation.png || true
    break
  fi
done

if [ -z "$add_xy" ]; then
  echo "Real Launcher pin confirmation did not appear" >&2
  adb shell dumpsys window > q1-evidence/window-pin-request-no-confirm.txt 2>&1 || true
  adb shell dumpsys appwidget > q1-evidence/appwidget-pin-request-no-confirm.txt 2>&1 || true
  adb exec-out screencap -p > q1-evidence/pin-request-no-confirm.png || true
  exit 31
fi

adb shell input tap $add_xy
sleep 8
adb shell dumpsys appwidget > q1-evidence/appwidget-after-pin-request.txt
adb shell dumpsys window > q1-evidence/window-after-pin-request.txt 2>&1 || true
adb exec-out screencap -p > q1-evidence/widget-home-pin-request.png || true
adb logcat -d > q1-evidence/logcat-after-pin-request.txt 2>&1 || true

python3 - q1-evidence/appwidget-after-pin-request.txt "$PACKAGE" <<'PY'
import re,sys
text=open(sys.argv[1],errors='replace').read(); package=sys.argv[2]
if 'Widgets:' not in text or 'Hosts:' not in text:
    raise SystemExit(2)
section=text.split('Widgets:',1)[1].split('Hosts:',1)[0]
for block in re.split(r'(?m)^\s*\[\d+\]\s+id=\d+\s*$',section)[1:]:
    if package in block and 'MainWidget' in block and ('pkg:com.android.launcher3' in block or 'pkg:com.google.android.apps.nexuslauncher' in block):
        print(block.strip())
        open('q1-evidence/main-widget-binding-pin-request.txt','w').write(block.strip()+'\n')
        raise SystemExit(0)
raise SystemExit(22)
PY

# Instrumentation success is never enough by itself. Require the Launcher pin UI
# to be gone and reject any target-app crash/ANR after the real host binding.
if grep -E -q 'PinItemRequest|Add to Home screen|Add automatically' q1-evidence/window-after-pin-request.txt; then
  echo "Launcher pin confirmation remained open after affirmative action" >&2
  exit 22
fi
if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}|ANR in ${PACKAGE//./\\.}" q1-evidence/logcat-after-pin-request.txt; then
  echo "Target app fatal/ANR detected after real launcher pin binding" >&2
  grep -n -A40 -B8 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}|ANR in ${PACKAGE//./\\.}" q1-evidence/logcat-after-pin-request.txt >&2 || true
  exit 23
fi

echo "Q1 real Launcher-hosted MainWidget placement GREEN via self-contained platform pin flow."
