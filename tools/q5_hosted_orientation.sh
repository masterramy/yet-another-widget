#!/usr/bin/env bash
# J18: actual system display rotation while real appwidget remains bound.
# Pixel Launcher may choose portrait-only; capability failures are not product PASS.
set -euo pipefail
mkdir -p q1-evidence
bash tools/q1_emulator_smoke_recover.sh
source tools/q1_runtime_identity.sh
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
restore() {
  adb shell wm user-rotation free > q1-evidence/j18-restore-rotation.txt 2>&1 || true
  adb shell settings put system accelerometer_rotation 1 >/dev/null 2>&1 || true
}
trap restore EXIT
capture() {
  local stem="$1" remote="/data/local/tmp/$1.xml" i
  adb shell input keyevent KEYCODE_HOME
  sleep 4
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
  python3 - "q1-evidence/$stem.xml" "$YAW_APP_LABEL" "q1-evidence/$stem.png" <<'PY'
import sys,struct,xml.etree.ElementTree as ET,json
r=ET.parse(sys.argv[1]).getroot()
hosts=[n.attrib for n in r.iter("node") if n.attrib.get("class","").endswith("LauncherAppWidgetHostView") and sys.argv[2] in n.attrib.get("content-desc","")]
if len(hosts)!=1:raise AssertionError(f"expected exactly one configured Launcher host, got {len(hosts)}")
with open(sys.argv[3],"rb") as f:
    hdr=f.read(24)
assert hdr[:8]==b"\x89PNG\r\n\x1a\n"
w,h=struct.unpack(">II",hdr[16:24])
print(json.dumps({"frame":sys.argv[3],"width":w,"height":h,"host_bounds":hosts[0]["bounds"]}))
PY
}
capture j18-portrait-before
adb shell settings put system accelerometer_rotation 0
adb shell wm user-rotation lock 1 > q1-evidence/j18-rotation-command.txt 2>&1
capture j18-landscape-requested
python3 - q1-evidence/j18-landscape-requested.png <<'PY'
import sys,struct
with open(sys.argv[1],"rb") as f: header=f.read(24)
w,h=struct.unpack(">II",header[16:24])
if w<=h:raise SystemExit("J18_HOST_ROTATION_UNSUPPORTED: Pixel Launcher did not actually rotate to landscape; do not claim behavior")
print("J18 actual landscape PNG dimensions",w,h)
PY
adb shell wm user-rotation lock 0
capture j18-portrait-restored
echo "J18 genuine landscape Pixel Launcher host remained bound and rendered, then portrait restored. Native frames require visual review." | tee q1-evidence/j18-result.txt
