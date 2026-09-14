#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.tommasoberlose.anotherwidget"

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

echo "== Q1 placement recovery: Launcher-native long drag =="
# The preceding fail-closed smoke proved build/install/render/provider discovery and
# classified exit 22 at the picker gesture boundary. Retry only that invalidated
# surface with Android's single-process draganddrop injector, moving far enough
# toward the top of HOME for Launcher3 to transition out of its RecyclerView.
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
another_xy="$(python3 - /tmp/q1-picker.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    hay=' '.join((n.attrib.get('text',''),n.attrib.get('content-desc',''))).lower()
    if 'another widget' in hay:
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
source_xy="$(python3 - /tmp/q1-expanded.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for cell in root.iter('node'):
    if 'another widget widget' not in cell.attrib.get('content-desc','').lower():
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
adb shell input draganddrop "$sx" "$sy" 540 220 2500
sleep 10
adb shell dumpsys appwidget > q1-evidence/appwidget-after-recovery.txt
adb exec-out screencap -p > q1-evidence/widget-home-recovery.png || true

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

echo "Q1 real Launcher-hosted MainWidget placement GREEN via recovery drag."
