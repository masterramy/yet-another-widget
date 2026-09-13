#!/usr/bin/env bash
set -euo pipefail

EXPECTED_BASE="0a2874375d6a93d2dacea7658ab9b616937c0852"

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

chmod +x ./gradlew
./gradlew --version
./gradlew :app:assembleDebug --stacktrace --no-daemon

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APK"
sha256sum "$APK"
ls -l "$APK"
