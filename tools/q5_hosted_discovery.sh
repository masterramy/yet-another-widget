#!/usr/bin/env bash
# Discovery only: a real Pixel Launcher long-press, with no H8/H9/H11 pass claim.
set -euo pipefail
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
test -s "$APP_APK"
source tools/q1_runtime_identity.sh
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
mkdir -p q1-evidence
# RequestPinAppWidget alone can return a *bound but unconfigured* placeholder.
# Use the same actual Launcher picker/drag/configure sequence that passed Q5
# runtime-core (Run282), then independently inspect its visible host view.
bash tools/q1_emulator_smoke_recover.sh
adb shell dumpsys appwidget > q1-evidence/hosted-post-configure-appwidget.txt
adb shell input keyevent KEYCODE_HOME
sleep 4

dump_launcher() {
  local stem="$1" remote="/data/local/tmp/$1.xml" attempt
  for attempt in $(seq 1 8); do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >"q1-evidence/$stem-dump-$attempt.txt" 2>&1 &&
       adb pull "$remote" "q1-evidence/$stem.xml" >>"q1-evidence/$stem-dump-$attempt.txt" 2>&1 &&
       test -s "q1-evidence/$stem.xml"; then return 0; fi
    sleep 2
  done
  echo "Could not dump real Launcher UI at $stem" >&2
  return 1
}

dump_launcher hosted-before-longpress
adb exec-out screencap -p > q1-evidence/hosted-before-longpress.png
xy="$(python3 - q1-evidence/hosted-before-longpress.xml "$YAW_APP_LABEL" <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
label=sys.argv[2]
matches=[]
for node in root.iter("node"):
    a=node.attrib
    if a.get("class","").endswith("LauncherAppWidgetHostView") and label in a.get("content-desc",""):
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            if x2-x1>=100 and y2-y1>=40:
                matches.append(((x1+x2)//2,(y1+y2)//2))
if len(matches)!=1:
    raise SystemExit(f"Expected one actual Launcher host widget, found {len(matches)}")
print(*matches[0])
PY
)"
printf 'host_longpress_center=%s\n' "$xy" > q1-evidence/hosted-input.txt
adb shell input swipe $xy $xy 1250
sleep 3
dump_launcher hosted-after-longpress
adb exec-out screencap -p > q1-evidence/hosted-after-longpress.png
adb shell dumpsys appwidget > q1-evidence/hosted-after-longpress-appwidget.txt
grep -Fq "$PACKAGE/$PACKAGE.ui.widgets.MainWidget" q1-evidence/hosted-after-longpress-appwidget.txt
grep -Fq "pkg:com.google.android.apps.nexuslauncher" q1-evidence/hosted-after-longpress-appwidget.txt
python3 - q1-evidence/hosted-before-longpress.xml q1-evidence/hosted-after-longpress.xml <<'PY' > q1-evidence/hosted-discovery-summary.json
import json,sys,xml.etree.ElementTree as ET
out={}
for stage,path in zip(("before","after"),sys.argv[1:]):
    nodes=[n.attrib for n in ET.parse(path).getroot().iter("node")]
    out[stage]={
      "host_nodes":[{"class":n.get("class"),"bounds":n.get("bounds"),"description":n.get("content-desc")}
                    for n in nodes if n.get("class","").endswith("LauncherAppWidgetHostView")],
      "resize_or_widget_controls":[{"class":n.get("class"),"id":n.get("resource-id"),"description":n.get("content-desc"),"bounds":n.get("bounds")}
                 for n in nodes if any(t in (n.get("resource-id","")+" "+n.get("content-desc","")).lower()
                                      for t in ("resize","remove","widget"))][:35]
    }
out["proven"]="A configured real Launcher host existed; attempted long-press and retained before/after evidence; selection affordance not automatically certified"
out["H8_resize"]="NOT PROVEN: size change and re-render not yet exercised"
out["H9_taps"]="NOT TESTED"
out["H11_remove_readd"]="NOT TESTED"
print(json.dumps(out,ensure_ascii=False,indent=2))
PY
test -s q1-evidence/hosted-after-longpress.png
echo "Real Launcher long-press evidence captured; do not promote H8/H9/H11."
