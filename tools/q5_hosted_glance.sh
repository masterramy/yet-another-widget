#!/usr/bin/env bash
# H16: prove production custom Glance state through the actual Launcher host.
set -euo pipefail
mkdir -p q1-evidence
bash tools/q1_emulator_smoke_recover.sh
source tools/q1_runtime_identity.sh
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
TEST_APK="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -s "$TEST_APK"
adb install -r "$TEST_APK" | tee q1-evidence/h16-test-install.txt
grep -Fq Success q1-evidence/h16-test-install.txt
runner="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
test_run() {
  local name="$1" logfile="q1-evidence/h16-$1.txt"
  adb shell am instrument -w -r -e class "$PACKAGE.Q5HostedGlanceTest#$name" "$runner" | tee "$logfile"
  grep -Eq '^OK \([0-9]+ test' "$logfile"
}
capture() {
  local stem="$1" remote="/data/local/tmp/$1.xml" i
  adb shell input keyevent KEYCODE_HOME
  sleep 3
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
}
test_run stageNoGlance
capture h16-glance-before
test_run stageCustomNote
capture h16-glance-after
test_run assertFixtureStillActive
test_run stageLeftAligned
capture h6-hosted-left
test_run stageRightAligned
capture h6-hosted-right
test_run stageHostedClock
capture h7-hosted-clock
test_run stageVisibleNotificationBeforeRevocation
capture h16-notification-before-revoke
test_run revokeNotificationAndRefreshHostedState
capture h16-notification-after-revoke
python3 - q1-evidence/h16-glance-before.xml q1-evidence/h16-glance-after.xml "$YAW_APP_LABEL" <<'PY' > q1-evidence/h16-host-summary.json
import sys,json,xml.etree.ElementTree as ET
def find(path):
    r=ET.parse(path).getroot()
    hosts=[n.attrib for n in r.iter("node") if n.attrib.get("class","").endswith("LauncherAppWidgetHostView") and sys.argv[3] in n.attrib.get("content-desc","")]
    if len(hosts)!=1:raise AssertionError(f"{path}: expected exactly one real Launcher host, found {len(hosts)}")
    return hosts[0]["bounds"]
print(json.dumps({"before_host_bounds":find(sys.argv[1]),"after_host_bounds":find(sys.argv[2]),
 "production_provider_and_bitmap_text":"ASSERTED_BY_Q5HostedGlanceTest",
 "actual_Launcher_RemoteViews":"ASSERTED_BY_APPWIDGET_DUMPS",
 "actual_screenshot":"CAPTURED_BEFORE_AND_AFTER","native_image_visual_review":"REQUIRED_BEFORE_FINAL_H16_PASS"},indent=2))
PY
echo "H16 real-host before/after captured; native visual comparison still required."
