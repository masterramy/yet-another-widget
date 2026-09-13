#!/usr/bin/env bash
set -euo pipefail

EXPECTED_BASE="0a2874375d6a93d2dacea7658ab9b616937c0852"
SLIMADAPTER_COMMIT="3e00f876906019ac224e29159ff1a777c4a47d4b"

echo "== Yet Another Widget Q1 feasibility build =="
echo "HEAD: $(git rev-parse HEAD)"
echo "BASE: $EXPECTED_BASE"

git merge-base --is-ancestor "$EXPECTED_BASE" HEAD

git diff --check "$EXPECTED_BASE"..HEAD

if git ls-files | grep -Eq '(^|/)(google-services\.json|apikey\.properties|.*\.(jks|keystore))$'; then
  echo "Credential/signing artifact detected in tracked source" >&2
  exit 2
fi

if grep -RIn --exclude-dir=.git --exclude='*.md' -E 'FirebaseCrashlytics|io\.realm|BuildConfig\.GOOGLE_API_KEY' app/src/main app/build.gradle build.gradle; then
  echo "Removed private/legacy dependency reference remains" >&2
  exit 3
fi

# Q1-only deterministic source bridge for the abandoned SlimAdapter dependency.
# Pin the library to its upstream AndroidX migration commit; Q2 will either vendor or replace it before freeze.
TMP_SLIM="$(mktemp -d)"
trap 'rm -rf "$TMP_SLIM"' EXIT
git -C "$TMP_SLIM" init -q
git -C "$TMP_SLIM" remote add origin https://github.com/linisme/SlimAdapter.git
git -C "$TMP_SLIM" fetch -q --depth 1 origin "$SLIMADAPTER_COMMIT"
git -C "$TMP_SLIM" checkout -q --detach FETCH_HEAD
test "$(git -C "$TMP_SLIM" rev-parse HEAD)" = "$SLIMADAPTER_COMMIT"
rm -rf app/src/main/java/net/idik/lib/slimadapter
mkdir -p app/src/main/java/net/idik/lib
cp -R "$TMP_SLIM/slimadapter/src/main/java/net/idik/lib/slimadapter" app/src/main/java/net/idik/lib/
echo "SlimAdapter source: $SLIMADAPTER_COMMIT"

chmod +x ./gradlew
./gradlew --version
./gradlew :app:assembleDebug --stacktrace --no-daemon

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APK"
sha256sum "$APK"
ls -l "$APK"
