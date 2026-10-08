#!/usr/bin/env bash
set -euo pipefail

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APK"
source tools/q1_runtime_identity.sh
yaw_read_app_identity "$APK"
PACKAGE="$YAW_PACKAGE"
ACTIVITY="$YAW_ACTIVITY_CLASS"
EVIDENCE_DIR="q5-locale-evidence"
mkdir -p "$EVIDENCE_DIR"

wait_boot() {
  adb wait-for-device
  local ok=0
  for _ in $(seq 1 120); do
    if adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -qx '1'; then ok=1; break; fi
    sleep 2
  done
  test "$ok" = 1
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell input keyevent 82 >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 3
}

dump_ui() {
  local stem="$1"
  local remote="/data/local/tmp/${stem}.xml"
  for attempt in $(seq 1 8); do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >"$EVIDENCE_DIR/${stem}-dump-${attempt}.txt" 2>&1 &&
       adb pull "$remote" "$EVIDENCE_DIR/${stem}.xml" >>"$EVIDENCE_DIR/${stem}-dump-${attempt}.txt" 2>&1 &&
       test -s "$EVIDENCE_DIR/${stem}.xml"; then
      return 0
    fi
    sleep 1
  done
  return 1
}

assert_text() {
  python3 - "$1" "$2" <<'PY'
import sys, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot(); expected=sys.argv[2]
for n in root.iter("node"):
    if expected in n.attrib.get("text","") or expected in n.attrib.get("content-desc",""):
        raise SystemExit(0)
print("missing expected text:", expected, file=sys.stderr); raise SystemExit(1)
PY
}

assert_preview_content() {
  python3 - "$1" <<'PY'
import sys,xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
visible=[]
for n in root.iter("node"):
    rid=n.attrib.get("resource-id","")
    if rid.endswith("/date") or rid.endswith("/next_event"):
        if n.attrib.get("text","").strip() and n.attrib.get("bounds","") not in ("","[0,0][0,0]"):
            visible.append((rid,n.attrib["text"]))
if not visible:
    raise SystemExit("Widget preview has no nonblank date/event content in real rendered hierarchy: "+sys.argv[1])
PY
}
expected_string() {
  local qualifier="$1" name="$2"
  python3 - "$qualifier" "$name" <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
qual,name=sys.argv[1:3]
paths=[]
if qual:
    paths.append(Path("app/src/main/res")/f"values-{qual}"/"strings.xml")
paths.append(Path("app/src/main/res/values/strings.xml"))
for p in paths:
    if not p.exists(): continue
    root=ET.parse(p).getroot()
    for node in root.findall("string"):
        if node.attrib.get("name")==name:
            print("".join(node.itertext()).replace("\\'","'"))
            raise SystemExit(0)
raise SystemExit(f"missing resource {name} for {qual}")
PY
}

launch_capture() {
  local stem="$1" expected="$2"
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  local started=0
  for attempt in $(seq 1 30); do
    set +e
    adb shell am start -W -n "$PACKAGE/$ACTIVITY" >"$EVIDENCE_DIR/${stem}-start-${attempt}.txt" 2>&1
    local start_rc=$?
    set -e
    if [ "$start_rc" -eq 0 ] &&
       grep -Eq 'Status: ok|LaunchState=' "$EVIDENCE_DIR/${stem}-start-${attempt}.txt"; then
      cp "$EVIDENCE_DIR/${stem}-start-${attempt}.txt" "$EVIDENCE_DIR/${stem}-start.txt"
      started=1
      break
    fi
    sleep 2
  done
  if [ "$started" -ne 1 ]; then
    echo "MainActivity did not become launchable after bounded app-locale transition wait" >&2
    return 1
  fi
  sleep 5
  dump_ui "$stem"
  assert_text "$EVIDENCE_DIR/${stem}.xml" "$YAW_APP_LABEL"
  assert_text "$EVIDENCE_DIR/${stem}.xml" "$expected"
  adb exec-out screencap -p >"$EVIDENCE_DIR/${stem}-initial.png"
  local preview_ready=0
  for sample in $(seq 0 8); do
    if [ "$sample" -gt 0 ]; then
      sleep 3
      dump_ui "${stem}-preview-retry-${sample}"
      cp "$EVIDENCE_DIR/${stem}-preview-retry-${sample}.xml" "$EVIDENCE_DIR/${stem}.xml"
    fi
    if assert_preview_content "$EVIDENCE_DIR/${stem}.xml"; then
      printf 'sample=%s elapsed_after_first_snapshot_seconds=%s\n' "$sample" "$((sample * 3))" >"$EVIDENCE_DIR/${stem}-preview-ready.txt"
      preview_ready=1
      break
    fi
  done
  adb exec-out screencap -p >"$EVIDENCE_DIR/${stem}.png"
  if [ "$preview_ready" -ne 1 ]; then
    adb logcat -d > "$EVIDENCE_DIR/${stem}-preview-failure-logcat.txt" 2>&1 || true
    echo "Preview remained blank after bounded 24-second retry: $stem" >&2
    return 1
  fi
  adb shell dumpsys activity activities >"$EVIDENCE_DIR/${stem}-activities.txt" 2>&1 || true
  adb shell dumpsys window >"$EVIDENCE_DIR/${stem}-window.txt" 2>&1 || true
  grep -E -q "ResumedActivity:.*${PACKAGE//./\\.}.*MainActivity|topResumedActivity=.*${PACKAGE//./\\.}.*MainActivity" "$EVIDENCE_DIR/${stem}-activities.txt"
}

set_locale_and_capture() {
  local tag="$1" qualifier="$2" stem="$3"
  adb shell cmd locale set-app-locales "$PACKAGE" --user 0 --locales "$tag" >"$EVIDENCE_DIR/${stem}-set-locale.txt" 2>&1

  local actual=""
  local applied=0
  for _ in $(seq 1 30); do
    actual="$(adb shell cmd locale get-app-locales "$PACKAGE" --user 0 2>/dev/null | tr -d '\r' || true)"
    if grep -Fq "$tag" <<<"$actual"; then
      applied=1
      break
    fi
    sleep 1
  done
  printf 'requested=%s\nactual=%s\n' "$tag" "$actual" >"$EVIDENCE_DIR/${stem}-locale.txt"
  if [ "$applied" -ne 1 ]; then
    echo "Per-app locale did not converge to requested tag: $tag" >&2
    return 1
  fi

  expected="$(expected_string "$qualifier" typography_settings_title)"
  launch_capture "$stem" "$expected"
}

wait_boot
adb install -r "$APK" | tee "$EVIDENCE_DIR/install.txt"
grep -Fq Success "$EVIDENCE_DIR/install.txt"
adb shell dumpsys package "$PACKAGE" >"$EVIDENCE_DIR/package.txt" 2>&1
grep -Fq "versionName=1.0.0" "$EVIDENCE_DIR/package.txt"
grep -Fq "targetSdk=36" "$EVIDENCE_DIR/package.txt"

# Default English plus every shipped translation resource directory.
set_locale_and_capture "en-US" "" "locale-en"
set_locale_and_capture "da-DK" "da" "locale-da"
set_locale_and_capture "de-DE" "de" "locale-de"
set_locale_and_capture "es-ES" "es" "locale-es"
set_locale_and_capture "fr-FR" "fr" "locale-fr"
set_locale_and_capture "id-ID" "in" "locale-in"
set_locale_and_capture "it-IT" "it" "locale-it"
set_locale_and_capture "pl-PL" "pl" "locale-pl"
set_locale_and_capture "pt-PT" "pt" "locale-pt"
set_locale_and_capture "ru-RU" "ru" "locale-ru"
set_locale_and_capture "sk-SK" "sk" "locale-sk"
set_locale_and_capture "zh-CN" "zh-rCN" "locale-zh-cn"

# Unsupported Arabic uses default strings but must exercise RTL layout resilience.
set_locale_and_capture "ar-EG" "" "locale-rtl-ar"

# Restore English before display/accessibility boundaries.
set_locale_and_capture "en-US" "" "locale-en-restored"
adb shell settings put system font_scale 1.0
sleep 2
launch_capture "font-100" "$(expected_string "" typography_settings_title)"

adb shell settings put system font_scale 2.0
sleep 3
launch_capture "font-200" "$(expected_string "" typography_settings_title)"

# At 200% text, bottom rows can be only partially visible in a single hierarchy
# snapshot. Collect bounded scroll states and evaluate each control at its
# largest fully exposed bounds instead of treating viewport clipping as a 15px
# touch target.
for step in $(seq 1 8); do
  adb shell input swipe 540 1750 540 650 350
  sleep 1
  dump_ui "font-200-scroll-${step}"
done
adb shell wm density >"$EVIDENCE_DIR/wm-density.txt"

# Fail closed on main navigation semantics and minimum 44dp-equivalent touch rows.
python3 - "$EVIDENCE_DIR/wm-density.txt" "$EVIDENCE_DIR" <<'PY'
import glob,re,sys,xml.etree.ElementTree as ET
density_text=open(sys.argv[1],errors="replace").read()
evidence_dir=sys.argv[2]
m=re.search(r"(Override|Physical) density:\s*(\d+)", density_text)
density=int(m.group(2)) if m else 420
min_px=44*density/160
required={"action_typography","action_general_settings","action_show_clock","action_show_events","action_show_weather","action_show_glance","action_tab_default_app","action_settings"}
best={}
for path in sorted(glob.glob(f"{evidence_dir}/font-200*.xml")):
    root=ET.parse(path).getroot()
    for n in root.iter("node"):
        rid=n.attrib.get("resource-id","")
        suffix=rid.rsplit("/",1)[-1]
        if suffix not in required:
            continue
        b=re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",n.attrib.get("bounds",""))
        if not b:
            continue
        x1,y1,x2,y2=map(int,b.groups())
        candidate=(x2-x1,y2-y1,n.attrib.get("clickable"),n.attrib.get("focusable"),path)
        previous=best.get(suffix)
        if previous is None or candidate[1] > previous[1]:
            best[suffix]=candidate
missing=required-set(best)
if missing:
    raise SystemExit(f"missing semantic controls across scroll states: {sorted(missing)}")
out=[]
for suffix in sorted(required):
    width,height,clickable,focusable,path=best[suffix]
    if width < min_px or height < min_px:
        raise SystemExit(f"touch target below 44dp for {suffix}: {width}x{height}px at density {density} in {path}")
    if clickable!="true" or focusable!="true":
        raise SystemExit(f"missing clickable/focusable semantics for {suffix} in {path}")
    out.append(f"{suffix}=({width},{height}) source={path}")
open(f"{evidence_dir}/accessibility-main-controls.txt","w").write("\n".join(out)+"\n")
PY

adb logcat -d >"$EVIDENCE_DIR/final-logcat.txt" 2>&1 || true
if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}|ANR in ${PACKAGE//./\\.}" "$EVIDENCE_DIR/final-logcat.txt"; then
  echo "target app fatal/ANR detected during locale/display matrix" >&2
  exit 40
fi

# Restore normal display/app-locale state.
adb shell settings put system font_scale 1.0
adb shell cmd locale set-app-locales "$PACKAGE" --user 0 --locales "" >/dev/null 2>&1 || true
cat >"$EVIDENCE_DIR/summary.txt" <<EOF
source_sha=${GITHUB_SHA:-unknown}
default_english=PASS
da=PASS
de=PASS
es=PASS
fr=PASS
in=PASS
it=PASS
pl=PASS
pt=PASS
ru=PASS
sk=PASS
zh_rCN=PASS
rtl_ar_fallback_resilience=PASS
font_scale_100=PASS
font_scale_200=PASS
main_navigation_touch_targets_44dp=PASS
main_navigation_clickable_focusable_semantics=PASS
shipping_source_mutated_by_this_test=NO
EOF
