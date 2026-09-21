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

  YAW_TEST_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"
  YAW_TEST_RUNNER_CLASS="$(sed -n "s/^instrumentation: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"

  test -n "$YAW_TEST_PACKAGE"
  test -n "$YAW_TEST_RUNNER_CLASS"

  export YAW_TEST_PACKAGE YAW_TEST_RUNNER_CLASS
}
