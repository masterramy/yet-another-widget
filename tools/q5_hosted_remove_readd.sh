#!/usr/bin/env bash
# H11: coherent real Launcher pointer drag to Remove, followed by picker re-add.
# This is NOT AppWidgetManager.deleteAppWidgetId or an artificial test host.
set -euo pipefail
APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
source tools/q1_runtime_identity.sh
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
mkdir -p q1-evidence
dump_ui() {
  local stem="$1" remote="/data/local/tmp/$1.xml" i
  for i in $(seq 1 8); do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
       adb pull "$remote" "q1-evidence/$stem.xml" >>"q1-evidence/$stem-dump-$i.txt" 2>&1 &&
       test -s "q1-evidence/$stem.xml"; then return 0; fi
    sleep 2
  done
  echo "H11 UI dump failed: $stem" >&2; return 1
}
host_center() {
  python3 - "$1" "$YAW_APP_LABEL" <<'PY'
import re,sys,xml.etree.ElementTree as ET
matches=[]
for n in ET.parse(sys.argv[1]).getroot().iter("node"):
    a=n.attrib
    if a.get("class","").endswith("LauncherAppWidgetHostView") and sys.argv[2] in a.get("content-desc",""):
        m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",a.get("bounds",""))
        if m:
            x1,y1,x2,y2=map(int,m.groups())
            matches.append(((x1+x2)//2,(y1+y2)//2))
if len(matches)!=1: raise AssertionError(f"Expected one configured host view; found {len(matches)}")
print(*matches[0])
PY
}
no_visible_host() {
  python3 - "$1" "$YAW_APP_LABEL" <<'PY'
import sys,xml.etree.ElementTree as ET
found=[n.attrib for n in ET.parse(sys.argv[1]).getroot().iter("node")
       if n.attrib.get("class","").endswith("LauncherAppWidgetHostView")
       and sys.argv[2] in n.attrib.get("content-desc","")]
if found:raise AssertionError(f"Widget remains on Launcher after Remove drag: {found}")
PY
}
adb shell input keyevent KEYCODE_HOME
sleep 3
dump_ui h11-before-remove
center="$(host_center q1-evidence/h11-before-remove.xml)"
adb exec-out screencap -p > q1-evidence/h11-before-remove.png
read -r sx sy <<< "$center"
runner="$PACKAGE.test/androidx.test.runner.AndroidJUnitRunner"
adb shell pm list instrumentation > q1-evidence/h11-instrumentation-list.txt
grep -Fq "$runner" q1-evidence/h11-instrumentation-list.txt
# Real Pixel Launcher Remove drop target at top; verify disappearance, don't
# infer pass from the drag instrumentation alone.
printf 'source=%s\ndrag_target=540 135\n' "$center" > q1-evidence/h11-remove-input.txt
adb shell am instrument -w -r \
  -e class "$PACKAGE.LauncherWidgetDragTest" \
  -e sourceX "$sx" -e sourceY "$sy" \
  -e edgeY 135 -e targetX 540 -e targetY 135 \
  "$runner" | tee q1-evidence/h11-real-remove-drag-instrumentation.txt
grep -Eq '^OK \([0-9]+ test' q1-evidence/h11-real-remove-drag-instrumentation.txt
sleep 4
adb shell input keyevent KEYCODE_HOME
sleep 2
dump_ui h11-after-remove
adb exec-out screencap -p > q1-evidence/h11-after-remove.png
adb shell dumpsys appwidget > q1-evidence/h11-after-remove-appwidget.txt
no_visible_host q1-evidence/h11-after-remove.xml
# Re-add through the tested actual Pixel Launcher picker/drag/configure path.
bash tools/q1_emulator_smoke_recover.sh
adb shell input keyevent KEYCODE_HOME
sleep 3
dump_ui h11-after-readd
adb exec-out screencap -p > q1-evidence/h11-after-readd.png
adb shell dumpsys appwidget > q1-evidence/h11-after-readd-appwidget.txt
host_center q1-evidence/h11-after-readd.xml > q1-evidence/h11-readded-center.txt
grep -Fq "pkg:com.google.android.apps.nexuslauncher" q1-evidence/h11-after-readd-appwidget.txt
grep -Fq "$PACKAGE/$PACKAGE.ui.widgets.MainWidget" q1-evidence/h11-after-readd-appwidget.txt
grep -Fq "views=android.widget.RemoteViews" q1-evidence/h11-after-readd-appwidget.txt
cat > q1-evidence/h11-real-remove-readd-summary.txt <<'EOF'
method=coherent_global_pointer_drag_real_Launcher_remove_then_picker_readd
before_configured_host=PASS
after_drag_no_visible_host=PASS
after_readd_configured_host=PASS
after_readd_remote_views=PASS
H11_exact_emulator=PASS
H9_real_taps=NOT_TESTED
H16_live_glance=NOT_TESTED
EOF
echo "H11 real Pixel Launcher remove and re-add PASS."
