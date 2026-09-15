#!/usr/bin/env bash
set -euo pipefail
PACKAGE="com.tommasoberlose.anotherwidget"
RUNNER="com.tommasoberlose.anotherwidget.test/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.tommasoberlose.anotherwidget.PinWidgetRequestTest"
mkdir -p q1-evidence

echo "== Q1 bounded recovery: platform requestPinAppWidget flow =="
adb shell am instrument -w -r -e class "$CLASS" "$RUNNER" | tee q1-evidence/pin-request-instrumentation.txt
# requestPinAppWidget must surface the real Launcher's confirmation UI. Locate its affirmative
# control from live UI rather than assuming coordinates, then let Launcher perform the bind.
sleep 2
adb shell uiautomator dump /data/local/tmp/q1-pin.xml >/dev/null
adb pull /data/local/tmp/q1-pin.xml /tmp/q1-pin.xml >/dev/null
cp /tmp/q1-pin.xml q1-evidence/pin-request-ui.xml
add_xy="$(python3 - /tmp/q1-pin.xml <<'PY'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
for n in root.iter('node'):
    text=(n.attrib.get('text','')+' '+n.attrib.get('content-desc','')).strip().lower()
    if text in {'add','add to home screen','add automatically'} or text.startswith('add '):
        m=re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',n.attrib.get('bounds',''))
        if m:
            a,b,c,d=map(int,m.groups()); print((a+c)//2,(b+d)//2); raise SystemExit(0)
raise SystemExit(1)
PY
)"
echo "Pin confirmation control: $add_xy" | tee q1-evidence/pin-request-confirmation.txt
adb shell input tap $add_xy
sleep 8
adb shell dumpsys appwidget > q1-evidence/appwidget-after-pin-request.txt
adb exec-out screencap -p > q1-evidence/widget-home-pin-request.png || true
python3 - q1-evidence/appwidget-after-pin-request.txt "$PACKAGE" <<'PY'
import re,sys
text=open(sys.argv[1],errors='replace').read(); package=sys.argv[2]
section=text.split('Widgets:',1)[1].split('Hosts:',1)[0] if 'Widgets:' in text and 'Hosts:' in text else ''
for block in re.split(r'(?m)^\s*\[\d+\]\s+id=\d+\s*$',section)[1:]:
    if package in block and 'MainWidget' in block and ('pkg:com.android.launcher3' in block or 'pkg:com.google.android.apps.nexuslauncher' in block):
        print(block.strip())
        open('q1-evidence/main-widget-binding-pin-request.txt','w').write(block.strip()+'\n')
        raise SystemExit(0)
raise SystemExit(22)
PY
echo "Q1 real Launcher-hosted MainWidget placement GREEN via platform pin flow."
