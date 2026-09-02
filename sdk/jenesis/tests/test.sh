#!/usr/bin/env bash
#
# Happy-path test for the POSIX SDK scripts (sdk/jenesis/bin/jenesis*).
# Exercises jenesis-init -> jenesis-version -> jenesis-validate -> jenesis against a
# freshly-staged SDK at <repo>/sdk/jenesis/{lib,sources}/. Exits 0 only when every
# check passes.
#
# Prerequisite: the SDK must have been staged before this script runs
# (the matching GitHub Actions job builds Jenesis with `stage` and copies
# the produced jar + sources jar into sdk/jenesis/lib and sdk/jenesis/sources).

set -eu

# This script lives at <repo>/sdk/tests/, so its parent is the SDK home.
SDK_HOME="$(cd "$(dirname "$0")/.." && pwd -P)"

SOURCES_JAR=""
for candidate in "${SDK_HOME}/sources"/*-sources.jar; do
    if [ -f "$candidate" ]; then
        SOURCES_JAR="$candidate"
        break
    fi
done
if [ -z "$SOURCES_JAR" ]; then
    echo "sdk-tests: no sources jar at ${SDK_HOME}/sources - stage the SDK first" >&2
    exit 1
fi

JAR_NAME="$(basename "$SOURCES_JAR" .jar)"
VERSION="${JAR_NAME#build.jenesis-}"
VERSION="${VERSION%-sources}"
echo "sdk-tests: SDK version ${VERSION}"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
PROJ="$TMPDIR/proj"
mkdir -p "$PROJ"

dump_and_fail() {
    echo "  fail: $1" >&2
    [ -n "${2:-}" ] && printf '%s\n' "$2" >&2
    exit 1
}

# [1/9] jenesis-version on a fresh directory: exit 1, reports missing build/jenesis
echo "[1/9] jenesis-version on fresh directory"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected exit 1, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "sdk is at version ${VERSION}" || dump_and_fail "missing 'sdk is at version' line" "$OUT"
printf '%s' "$OUT" | grep -qF "no build/jenesis found" || dump_and_fail "missing 'no build/jenesis found' line" "$OUT"
echo "  ok"

# [2/9] jenesis-init: populates build/jenesis and writes jenesis.version
echo "[2/9] jenesis-init"
"${SDK_HOME}/bin/jenesis-init" "$PROJ" >/dev/null
[ -d "$PROJ/build/jenesis" ] || dump_and_fail "build/jenesis not created"
[ -f "$PROJ/build/jenesis/jenesis.version" ] || dump_and_fail "jenesis.version not written"
RECORDED="$(cat "$PROJ/build/jenesis/jenesis.version")"
[ "$RECORDED" = "$VERSION" ] || dump_and_fail "jenesis.version was '$RECORDED', expected '$VERSION'"
echo "  ok"

# [3/9] jenesis-version on initialised project: exit 0, reports matching version
echo "[3/9] jenesis-version on initialised project"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "expected exit 0, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "build/jenesis is at version ${VERSION}" || dump_and_fail "missing matching-version line" "$OUT"
echo "  ok"

# [4/9] jenesis-validate: reports zero drift against the bundled sources
echo "[4/9] jenesis-validate"
OUT="$("${SDK_HOME}/bin/jenesis-validate" "$PROJ" 2>&1)"
printf '%s' "$OUT" | grep -qF "0 differs, 0 missing, 0 additional" || dump_and_fail "drift reported against bundled sources" "$OUT"
echo "  ok"

# [5/9] jenesis: the launcher resolves the engine from the SDK and dispatches to it;
# `help` is a print-only goal, so it runs offline once a project descriptor exists.
echo "[5/9] jenesis help"
mkdir -p "$PROJ/sources"
printf 'module sdktest {}\n' > "$PROJ/sources/module-info.java"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis" help 2>&1)" || dump_and_fail "jenesis help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis help did not print the usage banner" "$OUT"
echo "  ok"

# [6/9] jenesis: a project stamped with the SDK's own version dispatches to jenesis-run
echo "[6/9] jenesis on a matching stamp"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis" help 2>&1)" || dump_and_fail "jenesis help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "matching stamp did not run the installed version" "$OUT"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis-run" help 2>&1)" || dump_and_fail "jenesis-run help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis-run did not print the usage banner" "$OUT"
echo "  ok"

# [7/9] jenesis: a stamp naming a version that is not installed fails, naming both fixes
echo "[7/9] jenesis refuses an engine the vendored sources do not match"
PATCHED="$TMPDIR/patched"
cp -r "$PROJ" "$PATCHED"
echo "// local patch" >> "$PATCHED/build/jenesis/Platform.java"
set +e
OUT="$(cd "$PATCHED" && "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "expected the fallback to still build, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "does not match the sources" || dump_and_fail "a patched tree still used the installed engine" "$OUT"
echo "  ok"

# [8/9] jenesis: a stamp naming a version that is not installed falls back to the vendored sources
echo "[8/9] jenesis on an uninstalled stamp"
UNSTAMPED="$TMPDIR/unstamped"
cp -r "$PROJ" "$UNSTAMPED"
printf '0.0.0-ABSENT' > "$UNSTAMPED/build/jenesis/jenesis.version"
set +e
OUT="$(cd "$UNSTAMPED" && SDKMAN_DIR="$TMPDIR/no-sdkman" PATH="/usr/bin:/bin" "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "expected the fallback to build from the vendored sources, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "no installed Jenesis matches" || dump_and_fail "did not fall back to the vendored sources" "$OUT"
echo "  ok"

# [9/9] jenesis: a broken SDKMAN never aborts the launcher, it reaches the same fallback
echo "[9/9] jenesis with a broken SDKMAN"
BROKEN="$TMPDIR/broken-sdkman/bin"
mkdir -p "$BROKEN"
printf 'set -eu\nexit 1\n' > "$BROKEN/sdkman-init.sh"
set +e
OUT="$(cd "$UNSTAMPED" && SDKMAN_DIR="$TMPDIR/broken-sdkman" PATH="/usr/bin:/bin" "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "a broken SDKMAN did not reach the fallback, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "no installed Jenesis matches" || dump_and_fail "broken SDKMAN did not reach the fallback" "$OUT"
echo "  ok"

echo "sdk-tests: all checks passed"
