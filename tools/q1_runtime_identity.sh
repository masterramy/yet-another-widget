#!/usr/bin/env bash
set -euo pipefail

yaw_read_app_identity() {
  local apk="$1"
  test -s "$apk"
  local badging
  badging="$(aapt dump badging "$apk")"

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
  badging="$(aapt dump badging "$test_apk")"

  YAW_TEST_PACKAGE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"
  YAW_TEST_RUNNER_CLASS="$(sed -n "s/^instrumentation: name='\([^']*\)'.*/\1/p" <<<"$badging" | head -n1)"

  test -n "$YAW_TEST_PACKAGE"
  test -n "$YAW_TEST_RUNNER_CLASS"

  export YAW_TEST_PACKAGE YAW_TEST_RUNNER_CLASS
}
