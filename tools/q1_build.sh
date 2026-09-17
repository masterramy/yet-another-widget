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

# Q2 absorbed the abandoned SlimAdapter bridge into ordinary shipping source.
# The vendored snapshot is pinned to the same upstream AndroidX migration commit
# previously fetched by this script. package-info.java remains intentionally omitted
# because it only carried an obsolete JSR-305 default-nullness annotation.
SLIM_ROOT='app/src/main/java/net/idik/lib/slimadapter'
test -d "$SLIM_ROOT"
test "$(find "$SLIM_ROOT" -type f -name '*.java' | wc -l | tr -d ' ')" = "15"
test ! -e "$SLIM_ROOT/package-info.java"
grep -F 'IViewInjector<net.idik.lib.slimadapter.viewinjector.DefaultViewInjector> injector' "$SLIM_ROOT/SlimInjector.java" >/dev/null
echo "SlimAdapter source: vendored from $SLIMADAPTER_COMMIT (Q2 metadata/generic bridges absorbed)"

chmod +x ./gradlew
./gradlew --version

# Q2 dependency-security contract. CVE-2022-2390 is fixed in play-services-basement 18.0.2.
# Inspect the resolved runtime graph rather than assuming a top-level location version implies
# a safe transitive floor. Fail closed if the resolved coordinate cannot be determined.
BASEMENT_INSIGHT="$(./gradlew :app:dependencyInsight --dependency com.google.android.gms:play-services-basement --configuration debugRuntimeClasspath --no-daemon)"
printf '%s\n' "$BASEMENT_INSIGHT"
BASEMENT_VERSION="$(printf '%s\n' "$BASEMENT_INSIGHT" | sed -nE 's/^com\.google\.android\.gms:play-services-basement:([0-9]+\.[0-9]+\.[0-9]+).*$/\1/p' | head -n1)"
test -n "$BASEMENT_VERSION"
python3 - "$BASEMENT_VERSION" <<'PY'
import sys
version = tuple(int(part) for part in sys.argv[1].split('.'))
minimum = (18, 0, 2)
if version < minimum:
    raise SystemExit(f"Resolved play-services-basement {sys.argv[1]} is below patched floor 18.0.2")
print(f"Resolved play-services-basement {sys.argv[1]} satisfies patched floor >= 18.0.2")
PY

./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --no-daemon

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
TEST_APK="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APK"
test -n "$TEST_APK"
sha256sum "$APK" "$TEST_APK"
ls -l "$APK" "$TEST_APK"
