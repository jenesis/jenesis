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

# [1/11] jenesis-version on a fresh directory: exit 1, reports missing build/jenesis
echo "[1/11] jenesis-version on fresh directory"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected exit 1, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "sdk is at version ${VERSION}" || dump_and_fail "missing 'sdk is at version' line" "$OUT"
printf '%s' "$OUT" | grep -qF "no build/jenesis found" || dump_and_fail "missing 'no build/jenesis found' line" "$OUT"
echo "  ok"

# [2/11] jenesis-init: populates build/jenesis and writes jenesis.version
echo "[2/11] jenesis-init"
"${SDK_HOME}/bin/jenesis-init" "$PROJ" >/dev/null
[ -d "$PROJ/build/jenesis" ] || dump_and_fail "build/jenesis not created"
[ -f "$PROJ/build/jenesis/jenesis.version" ] || dump_and_fail "jenesis.version not written"
RECORDED="$(cat "$PROJ/build/jenesis/jenesis.version")"
[ "$RECORDED" = "$VERSION" ] || dump_and_fail "jenesis.version was '$RECORDED', expected '$VERSION'"
echo "  ok"

# [3/11] jenesis-version on initialised project: exit 0, reports matching version
echo "[3/11] jenesis-version on initialised project"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "expected exit 0, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "build/jenesis is at version ${VERSION}" || dump_and_fail "missing matching-version line" "$OUT"
echo "  ok"

# [4/11] jenesis-validate: reports zero drift against the bundled sources
echo "[4/11] jenesis-validate"
OUT="$("${SDK_HOME}/bin/jenesis-validate" "$PROJ" 2>&1)"
printf '%s' "$OUT" | grep -qF "0 differs, 0 missing, 0 additional" || dump_and_fail "drift reported against bundled sources" "$OUT"
echo "  ok"

# [5/11] jenesis: the launcher resolves the engine from the SDK and dispatches to it;
# `help` is a print-only goal, so it runs offline once a project descriptor exists.
echo "[5/11] jenesis help"
mkdir -p "$PROJ/sources"
printf 'module sdktest {}\n' > "$PROJ/sources/module-info.java"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis" help 2>&1)" || dump_and_fail "jenesis help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis help did not print the usage banner" "$OUT"
echo "  ok"

# [6/11] jenesis: a project stamped with the SDK's own version dispatches to jenesis-make
echo "[6/11] jenesis on a matching stamp"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis" help 2>&1)" || dump_and_fail "jenesis help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "matching stamp did not run the installed version" "$OUT"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis-make" help 2>&1)" || dump_and_fail "jenesis-make help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis-make did not print the usage banner" "$OUT"
echo "  ok"

# [7/11] jenesis: a patched vendored tree is refused, never run and never built from
echo "[7/11] jenesis refuses an engine the vendored sources do not match"
PATCHED="$TMPDIR/patched"
cp -r "$PROJ" "$PATCHED"
echo "// local patch" >> "$PATCHED/build/jenesis/Platform.java"
set +e
OUT="$(cd "$PATCHED" && "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "does not match the sources" || dump_and_fail "the refusal did not name the mismatch" "$OUT"
printf '%s' "$OUT" | grep -qF "refusing to run" || dump_and_fail "a patched tree was not refused" "$OUT"
if printf '%s' "$OUT" | grep -qF "a Java build tool"; then dump_and_fail "a patched tree still ran the build" "$OUT"; fi
printf '%s' "$OUT" | grep -qF ". jenesis-switch" || dump_and_fail "the refusal did not point at jenesis-switch" "$OUT"
printf '%s' "$OUT" | grep -qF "jenesis-make [selectors]" || dump_and_fail "the refusal did not point at jenesis-make" "$OUT"
printf '%s' "$OUT" | grep -qF "builds this project as a standard build" || dump_and_fail "the refusal did not offer the released engine" "$OUT"
printf '%s' "$OUT" | grep -qF "Neither of these executes the vendored build code" || dump_and_fail "the refusal did not separate the trusted routes" "$OUT"
printf '%s' "$OUT" | grep -qF "java build/jenesis/Make.java [selectors]" || dump_and_fail "the refusal did not point at source mode" "$OUT"
printf '%s' "$OUT" | grep -qF "compiles the engine into .jenesis/classes" || dump_and_fail "the refusal did not say what source mode costs" "$OUT"
printf '%s' "$OUT" | grep -qF "the project root at $PATCHED" || dump_and_fail "the refusal did not name the project root" "$OUT"
printf '%s' "$OUT" | grep -qF "Make.java is" || dump_and_fail "the refusal did not name the usual entry point" "$OUT"
printf '%s' "$OUT" | grep -qF "may drive it from one of its own" || dump_and_fail "the refusal did not allow for a custom entry point" "$OUT"
printf '%s' "$OUT" | grep -qF "that command executes unreviewed code" || dump_and_fail "the refusal did not warn about the vendored route" "$OUT"
printf '%s' "$OUT" | grep -qF "run builds from sources you" || dump_and_fail "the refusal did not warn about untrusted sources" "$OUT"
echo "  ok"

# [8/11] jenesis: a stamp naming a version that cannot be installed is refused, not built
echo "[8/11] jenesis on an uninstalled stamp"
UNSTAMPED="$TMPDIR/unstamped"
cp -r "$PROJ" "$UNSTAMPED"
printf '0.0.0-ABSENT' > "$UNSTAMPED/build/jenesis/jenesis.version"
set +e
OUT="$(cd "$UNSTAMPED" && SDKMAN_DIR="$TMPDIR/no-sdkman" PATH="/usr/bin:/bin" "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse an uninstallable stamp, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "no installed Jenesis matches" || dump_and_fail "the refusal did not name the missing version" "$OUT"
printf '%s' "$OUT" | grep -qF "refusing to run" || dump_and_fail "an uninstalled stamp was not refused" "$OUT"
echo "  ok"

# [9/11] jenesis: a broken SDKMAN never aborts the launcher, it reaches the same refusal
echo "[9/11] jenesis with a broken SDKMAN"
BROKEN="$TMPDIR/broken-sdkman/bin"
mkdir -p "$BROKEN"
printf 'set -eu\nexit 1\n' > "$BROKEN/sdkman-init.sh"
set +e
OUT="$(cd "$UNSTAMPED" && SDKMAN_DIR="$TMPDIR/broken-sdkman" PATH="/usr/bin:/bin" "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "a broken SDKMAN did not reach the refusal, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "no installed Jenesis matches" || dump_and_fail "broken SDKMAN did not reach the refusal" "$OUT"
echo "  ok"

# [10/11] jenesis: the refusal names the manager that installed this launcher, so a Scoop
# installation is not told to reach for SDKMAN
echo "[10/11] jenesis names the installing package manager"
SCOOP_HOME="$(cd "$TMPDIR" && pwd -P)/scoop-home"
mkdir -p "$SCOOP_HOME/scoop/apps/jenesis"
cp -r "$SDK_HOME" "$SCOOP_HOME/scoop/apps/jenesis/$VERSION"
set +e
OUT="$(cd "$UNSTAMPED" && HOME="$SCOOP_HOME" SDKMAN_DIR="$TMPDIR/no-sdkman" PATH="/usr/bin:/bin" \
    "$SCOOP_HOME/scoop/apps/jenesis/$VERSION/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse an uninstallable stamp, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "scoop install jenesis@" || dump_and_fail "a Scoop installation was pointed at SDKMAN" "$OUT"
echo "  ok"

# [11/11] jenesis-validate: a class file beside its source is inert and stays unreported,
# one whose source is gone is live code and is named
echo "[11/11] jenesis-validate on locally compiled classes"
COMPILED="$TMPDIR/compiled"
cp -r "$PROJ" "$COMPILED"
(cd "$COMPILED" && javac -nowarn build/jenesis/Project.java 2>/dev/null)
[ -f "$COMPILED/build/jenesis/Project.class" ] || dump_and_fail "javac produced no classes beside the sources"
OUT="$("${SDK_HOME}/bin/jenesis-validate" "$COMPILED" 2>&1)"
printf '%s' "$OUT" | grep -qF "0 differs, 0 missing, 0 additional" || dump_and_fail "shadowed class files were reported as drift" "$OUT"
rm "$COMPILED/build/jenesis/BuildExecutorCallback.java"
OUT="$("${SDK_HOME}/bin/jenesis-validate" "$COMPILED" 2>&1)"
printf '%s' "$OUT" | grep -qF "build/jenesis/BuildExecutorCallback.class additional" || dump_and_fail "an orphaned class file went unreported" "$OUT"
printf '%s' "$OUT" | grep -qF "build/jenesis/BuildExecutorCallback.java missing" || dump_and_fail "the removed source went unreported" "$OUT"
echo "  ok"

echo "sdk-tests: all checks passed"
