#!/usr/bin/env bash
set -euo pipefail

APP_APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APP_APK"
source tools/q1_runtime_identity.sh
yaw_read_app_identity "$APP_APK"
PACKAGE="$YAW_PACKAGE"
ACTIVITY="$YAW_ACTIVITY_CLASS"
EVIDENCE_DIR="q1-evidence"
mkdir -p "$EVIDENCE_DIR"

dump_ui() {
  local stem="$1"
  local remote="/data/local/tmp/${stem}.xml"
  local local_xml="$EVIDENCE_DIR/${stem}.xml"
  local ok=0
  for attempt in $(seq 1 8); do
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    set +e
    adb shell uiautomator dump "$remote" >"$EVIDENCE_DIR/${stem}-uiautomator-${attempt}.txt" 2>&1
    local dump_rc=$?
    adb pull "$remote" "$local_xml" >>"$EVIDENCE_DIR/${stem}-uiautomator-${attempt}.txt" 2>&1
    local pull_rc=$?
    set -e
    if [ "$dump_rc" -eq 0 ] && [ "$pull_rc" -eq 0 ] && [ -s "$local_xml" ]; then
      ok=1
      break
    fi
    sleep 1
  done
  if [ "$ok" -ne 1 ]; then
    echo "Could not capture UI hierarchy: $stem" >&2
    return 1
  fi
}

assert_text() {
  local xml="$1"
  local expected="$2"
  python3 - "$xml" "$expected" <<'PY'
import sys, xml.etree.ElementTree as ET
path, expected = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    if expected in node.attrib.get("text", "") or expected in node.attrib.get("content-desc", ""):
        raise SystemExit(0)
print(f"Missing expected UI text: {expected}", file=sys.stderr)
raise SystemExit(1)
PY
}

assert_absent() {
  local xml="$1"
  local forbidden="$2"
  python3 - "$xml" "$forbidden" <<'PY'
import sys, xml.etree.ElementTree as ET
path, forbidden = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    hay = " ".join((node.attrib.get("text",""), node.attrib.get("content-desc",""), node.attrib.get("resource-id","")))
    if forbidden in hay:
        print(f"Forbidden UI surface is visible: {forbidden}", file=sys.stderr)
        raise SystemExit(1)
raise SystemExit(0)
PY
}

tap_id() {
  local xml="$1"
  local suffix="$2"
  local xy
  xy="$(python3 - "$xml" "$suffix" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, suffix = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    rid = node.attrib.get("resource-id", "")
    if rid.endswith("/" + suffix) or rid.endswith(":id/" + suffix):
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            print((x1+x2)//2, (y1+y2)//2)
            raise SystemExit(0)
print(f"Could not find visible resource id: {suffix}", file=sys.stderr)
raise SystemExit(1)
PY
)"
  adb shell input tap $xy
}

get_text_by_id() {
  local xml="$1"
  local suffix="$2"
  python3 - "$xml" "$suffix" <<'PY'
import sys, xml.etree.ElementTree as ET
path, suffix = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
for node in root.iter("node"):
    rid = node.attrib.get("resource-id", "")
    if rid.endswith("/" + suffix) or rid.endswith(":id/" + suffix):
        print(node.attrib.get("text", ""))
        raise SystemExit(0)
print(f"Could not find visible resource id: {suffix}", file=sys.stderr)
raise SystemExit(1)
PY
}

tap_text() {
  local xml="$1"
  local expected="$2"
  local xy
  xy="$(python3 - "$xml" "$expected" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, expected = sys.argv[1], sys.argv[2]
root = ET.parse(path).getroot()
matches = []
for node in root.iter("node"):
    text = node.attrib.get("text", "")
    desc = node.attrib.get("content-desc", "")
    if text == expected or desc == expected:
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds",""))
        if m:
            x1,y1,x2,y2 = map(int,m.groups())
            matches.append(((x1+x2)//2, (y1+y2)//2))
if not matches:
    print(f"Could not find visible text: {expected}", file=sys.stderr)
    raise SystemExit(1)
print(*matches[0])
PY
)"
  adb shell input tap $xy
}

open_settings_from_main() {
  local stem="$1"
  dump_ui "${stem}-main"
  assert_text "$EVIDENCE_DIR/${stem}-main.xml" "$YAW_APP_LABEL"
  adb exec-out screencap -p > "$EVIDENCE_DIR/${stem}-main.png"
  tap_id "$EVIDENCE_DIR/${stem}-main.xml" "action_settings"
  sleep 3
  dump_ui "${stem}-settings"
  assert_text "$EVIDENCE_DIR/${stem}-settings.xml" "Settings"
}

select_theme_from_settings() {
  local settings_xml="$1"
  local option="$2"
  local expected_subtitle="$3"
  local stem="$4"
  tap_id "$settings_xml" "action_change_theme"
  sleep 2
  dump_ui "${stem}-theme-menu"
  assert_text "$EVIDENCE_DIR/${stem}-theme-menu.xml" "Theme"
  assert_text "$EVIDENCE_DIR/${stem}-theme-menu.xml" "$option"
  tap_text "$EVIDENCE_DIR/${stem}-theme-menu.xml" "$option"
  sleep 5
  dump_ui "${stem}-settings"
  assert_text "$EVIDENCE_DIR/${stem}-settings.xml" "Settings"
  assert_text "$EVIDENCE_DIR/${stem}-settings.xml" "$expected_subtitle"
  adb exec-out screencap -p > "$EVIDENCE_DIR/${stem}-settings.png"
}

echo "== Q5 surface/media tranche: real app Settings/About/Back =="

adb logcat -c || true
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$EVIDENCE_DIR/q5-surface-start.txt"
sleep 4

dump_ui "q5-main-surface"
assert_text "$EVIDENCE_DIR/q5-main-surface.xml" "$YAW_APP_LABEL"
assert_text "$EVIDENCE_DIR/q5-main-surface.xml" "Typography"
assert_text "$EVIDENCE_DIR/q5-main-surface.xml" "Layout"
assert_text "$EVIDENCE_DIR/q5-main-surface.xml" "Calendar"
assert_text "$EVIDENCE_DIR/q5-main-surface.xml" "Weather"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-main-surface.png"

tap_id "$EVIDENCE_DIR/q5-main-surface.xml" "action_settings"
sleep 3
dump_ui "q5-settings-top"
assert_text "$EVIDENCE_DIR/q5-settings-top.xml" "Settings"
assert_text "$EVIDENCE_DIR/q5-settings-top.xml" "Refresh widget"
assert_text "$EVIDENCE_DIR/q5-settings-top.xml" "Show widget preview"
assert_text "$EVIDENCE_DIR/q5-settings-top.xml" "Theme"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-settings-top.png"

echo "== Q5 Settings preview + theme state tranche =="

preview_before="$(get_text_by_id "$EVIDENCE_DIR/q5-settings-top.xml" "show_widget_preview_label")"
if [ "$preview_before" != "Visible" ] && [ "$preview_before" != "Hidden" ]; then
  echo "Unexpected preview label before toggle: $preview_before" >&2
  exit 42
fi
tap_id "$EVIDENCE_DIR/q5-settings-top.xml" "action_show_widget_preview"
sleep 2
dump_ui "q5-settings-preview-toggled"
preview_after="$(get_text_by_id "$EVIDENCE_DIR/q5-settings-preview-toggled.xml" "show_widget_preview_label")"
if [ "$preview_before" = "$preview_after" ]; then
  echo "Widget preview label did not change after real Settings toggle" >&2
  exit 42
fi
if [ "$preview_after" != "Visible" ] && [ "$preview_after" != "Hidden" ]; then
  echo "Unexpected preview label after toggle: $preview_after" >&2
  exit 42
fi
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-settings-preview-toggled.png"

# Restore the user's baseline preview preference before continuing.
tap_id "$EVIDENCE_DIR/q5-settings-preview-toggled.xml" "action_show_widget_preview"
sleep 2
dump_ui "q5-settings-preview-restored"
preview_restored="$(get_text_by_id "$EVIDENCE_DIR/q5-settings-preview-restored.xml" "show_widget_preview_label")"
if [ "$preview_restored" != "$preview_before" ]; then
  echo "Widget preview preference did not restore to baseline" >&2
  exit 42
fi

# Prove explicit Dark, explicit Light, and Follow-System transitions through the
# real Settings bottom sheet. System-light is restored before later evidence.
select_theme_from_settings "$EVIDENCE_DIR/q5-settings-preview-restored.xml" "Dark" "Dark" "q5-theme-dark"
tap_id "$EVIDENCE_DIR/q5-theme-dark-settings.xml" "action_back"
sleep 3
dump_ui "q5-theme-dark-main"
assert_text "$EVIDENCE_DIR/q5-theme-dark-main.xml" "$YAW_APP_LABEL"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-theme-dark-main.png"

open_settings_from_main "q5-theme-light-entry"
select_theme_from_settings "$EVIDENCE_DIR/q5-theme-light-entry-settings.xml" "Light" "Light" "q5-theme-light"
tap_id "$EVIDENCE_DIR/q5-theme-light-settings.xml" "action_back"
sleep 3
dump_ui "q5-theme-light-main"
assert_text "$EVIDENCE_DIR/q5-theme-light-main.xml" "$YAW_APP_LABEL"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-theme-light-main.png"

open_settings_from_main "q5-theme-default-entry"
adb shell cmd uimode night yes | tee "$EVIDENCE_DIR/q5-uimode-night-yes.txt"
sleep 2
dump_ui "q5-theme-default-dark-entry-settings"
select_theme_from_settings "$EVIDENCE_DIR/q5-theme-default-dark-entry-settings.xml" "Default" "Follow the system theme" "q5-theme-default-system-dark"
adb shell cmd uimode night no | tee "$EVIDENCE_DIR/q5-uimode-night-no.txt"
sleep 5
dump_ui "q5-theme-default-system-light-settings"
assert_text "$EVIDENCE_DIR/q5-theme-default-system-light-settings.xml" "Settings"
assert_text "$EVIDENCE_DIR/q5-theme-default-system-light-settings.xml" "Follow the system theme"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-theme-default-system-light-settings.png"

for _ in 1 2 3 4; do
  adb shell input swipe 540 1750 540 450 450
  sleep 1
done

dump_ui "q5-settings-about"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "About Yet Another Widget"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "Publisher: Ramy Baheeg"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "App version"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "v1.0.0 (1)"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "Help with translations"
assert_text "$EVIDENCE_DIR/q5-settings-about.xml" "Feedback and feature requests"
assert_absent "$EVIDENCE_DIR/q5-settings-about.xml" "Legal & Privacy"
assert_absent "$EVIDENCE_DIR/q5-settings-about.xml" "action_privacy_policy"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-settings-about.png"

tap_id "$EVIDENCE_DIR/q5-settings-about.xml" "action_back"
sleep 3
dump_ui "q5-main-after-settings-back"
assert_text "$EVIDENCE_DIR/q5-main-after-settings-back.xml" "$YAW_APP_LABEL"
assert_text "$EVIDENCE_DIR/q5-main-after-settings-back.xml" "Typography"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-main-after-settings-back.png"

echo "== Q5 lifecycle resilience tranche =="

assert_main_system_state() {
  local stem="$1"
  adb shell pidof "$PACKAGE" > "$EVIDENCE_DIR/${stem}-pidof.txt" 2>&1 || true
  adb shell dumpsys activity activities > "$EVIDENCE_DIR/${stem}-activities.txt" 2>&1 || true
  adb shell dumpsys window > "$EVIDENCE_DIR/${stem}-window.txt" 2>&1 || true
  test -s "$EVIDENCE_DIR/${stem}-pidof.txt"
  grep -E -q "topResumedActivity=.*${PACKAGE//./\\.}.*MainActivity|mResumedActivity=.*${PACKAGE//./\\.}.*MainActivity|ResumedActivity.*${PACKAGE//./\\.}.*MainActivity" "$EVIDENCE_DIR/${stem}-activities.txt"
  grep -q 'state=RESUMED' "$EVIDENCE_DIR/${stem}-activities.txt"
  grep -E -q "mCurrentFocus=.*${PACKAGE//./\\.}.*MainActivity" "$EVIDENCE_DIR/${stem}-window.txt"
}

# Background/resume + warm re-entry with the live process retained.
pid_before_background="$(adb shell pidof "$PACKAGE" || true)"
test -n "$pid_before_background"
adb shell input keyevent KEYCODE_HOME
sleep 3
adb shell dumpsys window > "$EVIDENCE_DIR/q5-lifecycle-background-window.txt" 2>&1 || true
adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$EVIDENCE_DIR/q5-lifecycle-resume-start.txt"
sleep 4
pid_after_resume="$(adb shell pidof "$PACKAGE" || true)"
test "$pid_after_resume" = "$pid_before_background"
dump_ui "q5-lifecycle-resume"
assert_text "$EVIDENCE_DIR/q5-lifecycle-resume.xml" "$YAW_APP_LABEL"
assert_main_system_state "q5-lifecycle-resume"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-lifecycle-resume.png"

assert_widget_bound() {
  local stem="$1"
  adb shell dumpsys appwidget > "$EVIDENCE_DIR/${stem}-appwidget.txt" 2>&1
  grep -Fq "pkg:com.google.android.apps.nexuslauncher" "$EVIDENCE_DIR/${stem}-appwidget.txt"
  grep -Fq "com.ramybaheeg.yetanotherwidget/com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget" "$EVIDENCE_DIR/${stem}-appwidget.txt"
  grep -Fq "views=android.widget.RemoteViews" "$EVIDENCE_DIR/${stem}-appwidget.txt"
}

wait_widget_bound() {
  local stem="$1"
  local ok=0
  for attempt in $(seq 1 20); do
    adb shell dumpsys appwidget > "$EVIDENCE_DIR/${stem}-appwidget-${attempt}.txt" 2>&1
    if grep -Fq "pkg:com.google.android.apps.nexuslauncher" "$EVIDENCE_DIR/${stem}-appwidget-${attempt}.txt" &&
       grep -Fq "com.ramybaheeg.yetanotherwidget/com.ramybaheeg.yetanotherwidget.ui.widgets.MainWidget" "$EVIDENCE_DIR/${stem}-appwidget-${attempt}.txt" &&
       grep -Fq "views=android.widget.RemoteViews" "$EVIDENCE_DIR/${stem}-appwidget-${attempt}.txt"; then
      cp "$EVIDENCE_DIR/${stem}-appwidget-${attempt}.txt" "$EVIDENCE_DIR/${stem}-appwidget.txt"
      printf 'attempt=%s\n' "$attempt" > "$EVIDENCE_DIR/${stem}-ready-attempt.txt"
      ok=1
      break
    fi
    sleep 3
  done
  if [ "$ok" -ne 1 ]; then
    cp "$EVIDENCE_DIR/${stem}-appwidget-20.txt" "$EVIDENCE_DIR/${stem}-appwidget.txt" 2>/dev/null || true
    echo "Hosted widget binding remained present but RemoteViews never repopulated after bounded reboot wait" >&2
    return 1
  fi
}

# Explicit process-death/cold restart. Before restarting the app, prove that the
# launcher still hosts and renders the exact widget while the app process is dead.
adb shell am force-stop "$PACKAGE"
sleep 2
if adb shell pidof "$PACKAGE" | grep -q .; then
  echo "Target process still alive after force-stop" >&2
  exit 43
fi
adb shell input keyevent KEYCODE_HOME
sleep 4
assert_widget_bound "q5-widget-app-process-dead"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-widget-app-process-dead.png"

adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$EVIDENCE_DIR/q5-lifecycle-cold-restart-start.txt"
sleep 4
dump_ui "q5-lifecycle-cold-restart"
assert_text "$EVIDENCE_DIR/q5-lifecycle-cold-restart.xml" "$YAW_APP_LABEL"
assert_main_system_state "q5-lifecycle-cold-restart"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-lifecycle-cold-restart.png"

# Restart the real Pixel Launcher process and prove that its hosted widget
# binding and rendered RemoteViews survive host-process recreation.
adb shell input keyevent KEYCODE_HOME
sleep 3
launcher_pid_before="$(adb shell pidof com.google.android.apps.nexuslauncher || true)"
test -n "$launcher_pid_before"
adb shell am force-stop com.google.android.apps.nexuslauncher
sleep 2
adb shell input keyevent KEYCODE_HOME
sleep 8
launcher_pid_after="$(adb shell pidof com.google.android.apps.nexuslauncher || true)"
test -n "$launcher_pid_after"
test "$launcher_pid_after" != "$launcher_pid_before"
printf 'before=%s\nafter=%s\n' "$launcher_pid_before" "$launcher_pid_after" > "$EVIDENCE_DIR/q5-launcher-restart-pids.txt"
assert_widget_bound "q5-widget-after-launcher-restart"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-widget-after-launcher-restart.png"

# Full emulator reboot: wait for Android boot completion, return to Launcher,
# then prove the same widget provider/host binding and visible RemoteViews survive.
adb reboot
adb wait-for-device
boot_ok=0
for _ in $(seq 1 90); do
  if adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' | grep -qx '1'; then
    boot_ok=1
    break
  fi
  sleep 2
done
if [ "$boot_ok" -ne 1 ]; then
  echo "Emulator did not complete boot in bounded wait" >&2
  exit 44
fi
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell input keyevent 82 >/dev/null 2>&1 || true
adb shell input keyevent KEYCODE_HOME
sleep 10
adb shell getprop sys.boot_completed > "$EVIDENCE_DIR/q5-reboot-boot-completed.txt"
wait_widget_bound "q5-widget-after-reboot"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-widget-after-reboot.png"

# Fresh install + true first launch from cleared package state. This is last
# because uninstalling correctly removes the previously-bound launcher widget.
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb uninstall "$PACKAGE" | tee "$EVIDENCE_DIR/q5-fresh-uninstall.txt"
grep -Fq "Success" "$EVIDENCE_DIR/q5-fresh-uninstall.txt"
adb install "$APP_APK" | tee "$EVIDENCE_DIR/q5-fresh-install.txt"
grep -Fq "Success" "$EVIDENCE_DIR/q5-fresh-install.txt"
adb shell dumpsys package "$PACKAGE" > "$EVIDENCE_DIR/q5-fresh-package.txt" 2>&1
grep -Fq "versionName=1.0.0" "$EVIDENCE_DIR/q5-fresh-package.txt"
adb shell am start -W -n "$PACKAGE/$ACTIVITY" | tee "$EVIDENCE_DIR/q5-first-launch-start.txt"
sleep 5
dump_ui "q5-first-launch"
assert_text "$EVIDENCE_DIR/q5-first-launch.xml" "$YAW_APP_LABEL"
assert_text "$EVIDENCE_DIR/q5-first-launch.xml" "Typography"
assert_text "$EVIDENCE_DIR/q5-first-launch.xml" "Calendar"
assert_text "$EVIDENCE_DIR/q5-first-launch.xml" "Weather"
assert_main_system_state "q5-first-launch"
adb exec-out screencap -p > "$EVIDENCE_DIR/q5-first-launch.png"

adb shell dumpsys window > "$EVIDENCE_DIR/q5-surface-window.txt" 2>&1 || true
adb logcat -d > "$EVIDENCE_DIR/q5-surface-logcat.txt" 2>&1 || true
if grep -E -q "FATAL EXCEPTION:.*|Process: ${PACKAGE//./\\.}|ANR in ${PACKAGE//./\\.}" "$EVIDENCE_DIR/q5-surface-logcat.txt"; then
  echo "Target app fatal/ANR detected during Q5 surface/media tranche" >&2
  grep -n -A40 -B8 -E "FATAL EXCEPTION:|Process: ${PACKAGE//./\\.}|ANR in ${PACKAGE//./\\.}" "$EVIDENCE_DIR/q5-surface-logcat.txt" >&2 || true
  exit 41
fi

cat > "$EVIDENCE_DIR/q5-surface-media-summary.txt" <<EOF
source_sha=${GITHUB_SHA:-unknown}
package=$PACKAGE
activity=$ACTIVITY
app_label=$YAW_APP_LABEL
main_shell=PASS
settings_shell=PASS
widget_preview_toggle_and_restore=PASS
theme_explicit_dark=PASS
theme_explicit_light=PASS
theme_follow_system_dark_to_light=PASS
about_provenance_publisher_version=PASS
privacy_row_hidden=PASS
settings_back_to_main=PASS
warm_relaunch_background_resume=PASS
process_death_cold_restart=PASS
widget_survives_app_process_death=PASS
widget_survives_launcher_restart=PASS
widget_survives_emulator_reboot=PASS
fresh_install_first_launch=PASS
bounded_app_fatal_anr_scan=PASS
shipping_source_mutated_by_this_test=NO
EOF

echo "Q5 Settings/About/back surface-media tranche GREEN."
