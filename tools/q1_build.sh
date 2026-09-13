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

# The library's package-info.java contains only a JSR-305 default-nullness annotation.
# It has no runtime behavior and the annotation package is no longer otherwise required,
# so Q1 strips this metadata-only source instead of adding an obsolete javax.annotation dependency.
PACKAGE_INFO='app/src/main/java/net/idik/lib/slimadapter/package-info.java'
test "$(cat "$PACKAGE_INFO")" = $'@javax.annotation.ParametersAreNonnullByDefault\npackage net.idik.lib.slimadapter;'
rm "$PACKAGE_INFO"

# SlimAdapter's historical callback exposes a raw IViewInjector. Kotlin 2 erases generic view
# types on that raw receiver; using a star projection fixes only the first fluent call because
# the self-type becomes unknown again. SlimViewHolder always constructs DefaultViewInjector,
# so retain that concrete self type at the callback boundary without changing runtime behavior.
python3 - <<'PY'
from pathlib import Path
p = Path('app/src/main/java/net/idik/lib/slimadapter/SlimInjector.java')
s = p.read_text()
old = 'void onInject(T data, IViewInjector injector);'
new = 'void onInject(T data, IViewInjector<net.idik.lib.slimadapter.viewinjector.DefaultViewInjector> injector);'
if s.count(old) != 1:
    raise SystemExit('Unexpected SlimInjector signature; fail closed')
p.write_text(s.replace(old, new))
PY
grep -F 'IViewInjector<net.idik.lib.slimadapter.viewinjector.DefaultViewInjector> injector' app/src/main/java/net/idik/lib/slimadapter/SlimInjector.java >/dev/null
echo "SlimAdapter source: $SLIMADAPTER_COMMIT (Q1 metadata/generic bridges applied)"

# Q1-only renderer compatibility overlay. It is fail-closed and modifies only the two
# historical widget renderer files in the runner checkout. Q2 absorbs these edits into
# ordinary source before quiescence/freeze.
python3 tools/q1_widget_compat.py

chmod +x ./gradlew
./gradlew --version
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace --no-daemon

APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
TEST_APK="$(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -type f -name '*.apk' | head -n1)"
test -n "$APK"
test -n "$TEST_APK"
sha256sum "$APK" "$TEST_APK"
ls -l "$APK" "$TEST_APK"
