#!/usr/bin/env bash
set -euo pipefail

yaw_read_app_identity() {
  local apk="$1"
  test -s "$apk"
  local badging
  local aapt_bin="${AAPT:-}"
  if [ -z "$aapt_bin" ]; then
    aapt_bin="$(command -v aapt 2>/dev/null || true)"
  fi
  if [ -z "$aapt_bin" ] && [ -n "${ANDROID_HOME:-}" ]; then
    aapt_bin="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -z "$aapt_bin" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    aapt_bin="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  test -n "$aapt_bin" && test -x "$aapt_bin"
  badging="$("$aapt_bin" dump badging "$apk")"

  YAW_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"
  YAW_ACTIVITY_CLASS="$(sed -n "s/^launchable-activity: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"
  YAW_APP_LABEL="$(sed -n "s/^application-label:'\(.*\)'$/\1/p" <<<"$badging" | head -n1)"

  test -n "$YAW_PACKAGE"
  test -n "$YAW_ACTIVITY_CLASS"
  test -n "$YAW_APP_LABEL"

  export YAW_PACKAGE YAW_ACTIVITY_CLASS YAW_APP_LABEL
}

yaw_read_test_identity() {
  local test_apk="$1"
  test -s "$test_apk"
  local badging
  local aapt_bin="${AAPT:-}"
  if [ -z "$aapt_bin" ]; then
    aapt_bin="$(command -v aapt 2>/dev/null || true)"
  fi
  if [ -z "$aapt_bin" ] && [ -n "${ANDROID_HOME:-}" ]; then
    aapt_bin="$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  if [ -z "$aapt_bin" ] && [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    aapt_bin="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt -perm -111 2>/dev/null | sort -V | tail -n1 || true)"
  fi
  test -n "$aapt_bin" && test -x "$aapt_bin"
  badging="$("$aapt_bin" dump badging "$test_apk")"

  YAW_TEST_PACKAGE="$(sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" <<<"$badging" | head -n1)"
  YAW_TEST_RUNNER_CLASS="$(sed -n "s/^instrumentation: name='\\([^']*\\)'.*/\\1/p" <<<"$badging" | head -n1)"

  # aapt "dump badging" is not consistent about emitting instrumentation
  # metadata for modern androidTest APKs. If the packaged test APK has no
  # badging instrumentation line, read android:name from the instrumentation
  # element in its binary AndroidManifest.xml. This remains fail-closed on the
  # exact built test artifact; it does not hard-code a runner or manufacture
  # placement success.
  if [ -z "$YAW_TEST_RUNNER_CLASS" ]; then
    local manifest_tree
    manifest_tree="$("$aapt_bin" dump xmltree "$test_apk" AndroidManifest.xml)"
    YAW_TEST_RUNNER_CLASS="$(awk '
      /^[[:space:]]*E: instrumentation([[:space:]]|$)/ { in_instrumentation=1; next }
      in_instrumentation && /^[[:space:]]*E:/ { exit }
      in_instrumentation && /android:name\\(0x01010003\\)=/ {
        line=$0
        sub(/^.*android:name\\(0x01010003\\)="/, "", line)
        sub(/".*$/, "", line)
        print line
        exit
      }
    ' <<<"$manifest_tree")"
  fi

  if [ -z "$YAW_TEST_PACKAGE" ] || [ -z "$YAW_TEST_RUNNER_CLASS" ]; then
    echo "Could not read packaged androidTest identity: package='$YAW_TEST_PACKAGE' runner='$YAW_TEST_RUNNER_CLASS'" >&2
    return 1
  fi

  export YAW_TEST_PACKAGE YAW_TEST_RUNNER_CLASS
}
