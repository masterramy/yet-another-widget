#!/usr/bin/env bash
# H9: real Pixel Launcher tap, no synthetic broadcast of ACTION_REFRESH.
set -euo pipefail
mkdir -p q1-evidence
bash tools/q1_emulator_smoke_recover.sh
source tools/q1_runtime_identity.sh
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
TEST_APK="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -s "$TEST_APK"
adb install -r "$TEST_APK" | tee q1-evidence/h9-test-install.txt
grep -Fq Success q1-evidence/h9-test-install.txt
runner="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
run_test() {
  local method="$1" output="q1-evidence/h9-$1.txt"
  adb shell am instrument -w -r -e class "$PACKAGE.Q5HostedTapTest#$method" "$runner" | tee "$output"
  grep -Eq '^OK \([0-9]+ test' "$output"
}
dump_ui() {
  local stem="$1" remote="/data/local/tmp/$1.xml" i
  for i in $(seq 1 8); do
    adb shell uiautomator dump "$remote" >"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
      adb pull "$remote" "q1-evidence/$stem.xml" >>"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
      test -s "q1-evidence/$stem.xml" && return 0
    sleep 2
  done
  return 1
}
run_test stageCalendarRefresh
adb shell input keyevent KEYCODE_HOME
sleep 3
dump_ui h9-real-host-before-tap
adb exec-out screencap -p > q1-evidence/h9-real-host-before-tap.png
run_test verifyUntouchedBeforeRealTap
xy="$(python3 - q1-evidence/h9-real-host-before-tap.xml "$YAW_APP_LABEL" "$PACKAGE" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); label=sys.argv[2];package=sys.argv[3]
hosts=[n for n in root.iter("node") if n.attrib.get("class","").endswith("LauncherAppWidgetHostView") and label in n.attrib.get("content-desc","")]
if len(hosts)!=1:raise SystemExit(f"Expected one real configured host, got {len(hosts)}")
nodes=[n.attrib for n in hosts[0].iter("node") if n.attrib.get("resource-id","")==package+":id/content"]
if len(nodes)!=1:raise SystemExit(f"Expected one actual hosted date content area, found {len(nodes)}")
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",nodes[0]["bounds"])
x1,y1,x2,y2=map(int,m.groups())
print((x1+x2)//2,(y1+y2)//2)
PY
)"
echo "$xy" > q1-evidence/h9-tap-coordinate.txt
adb shell input tap $xy
sleep 3
adb shell input keyevent KEYCODE_HOME
dump_ui h9-real-host-after-tap
adb exec-out screencap -p > q1-evidence/h9-real-host-after-tap.png
run_test verifyRealTapDispatchedRefresh
echo "H9 real configured Pixel Launcher calendar Refresh tap delivered ACTION_REFRESH."
