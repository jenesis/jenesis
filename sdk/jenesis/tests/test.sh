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

# The floor the launcher refuses through, read from the launcher itself, and a version above
# it: a project that has to be refused records the floor, a project that has to reach the
# engine through jenesis records the version above it.
UNSAFE_THROUGH="$(sed -n 's/^UNSAFE_THROUGH="\(.*\)"$/\1/p' "${SDK_HOME}/bin/jenesis")"
if [ -z "$UNSAFE_THROUGH" ]; then
    echo "sdk-tests: ${SDK_HOME}/bin/jenesis records no UNSAFE_THROUGH version" >&2
    exit 1
fi
SAFE="99.0.0"
echo "sdk-tests: unsafe through ${UNSAFE_THROUGH}"

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
PROJ="$TMPDIR/proj"
mkdir -p "$PROJ"

dump_and_fail() {
    echo "  fail: $1" >&2
    [ -n "${2:-}" ] && printf '%s\n' "$2" >&2
    exit 1
}

# [1/17] jenesis-version on a fresh directory: exit 1, reports missing build/jenesis
echo "[1/17] jenesis-version on fresh directory"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected exit 1, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "sdk is at version ${VERSION}" || dump_and_fail "missing 'sdk is at version' line" "$OUT"
printf '%s' "$OUT" | grep -qF "no build/jenesis found" || dump_and_fail "missing 'no build/jenesis found' line" "$OUT"
echo "  ok"

# [2/17] jenesis-init: populates build/jenesis and writes jenesis.version
echo "[2/17] jenesis-init"
"${SDK_HOME}/bin/jenesis-init" "$PROJ" >/dev/null
[ -d "$PROJ/build/jenesis" ] || dump_and_fail "build/jenesis not created"
[ -f "$PROJ/build/jenesis/jenesis.version" ] || dump_and_fail "jenesis.version not written"
RECORDED="$(cat "$PROJ/build/jenesis/jenesis.version")"
[ "$RECORDED" = "$VERSION" ] || dump_and_fail "jenesis.version was '$RECORDED', expected '$VERSION'"
echo "  ok"

# [3/17] jenesis-version on initialised project: exit 0, reports matching version
echo "[3/17] jenesis-version on initialised project"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-version" "$PROJ" 2>&1)"
RC=$?
set -e
[ "$RC" = "0" ] || dump_and_fail "expected exit 0, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "build/jenesis is at version ${VERSION}" || dump_and_fail "missing matching-version line" "$OUT"
echo "  ok"

# [4/17] jenesis-validate: reports zero drift against the bundled sources
echo "[4/17] jenesis-validate"
OUT="$("${SDK_HOME}/bin/jenesis-validate" "$PROJ" 2>&1)"
printf '%s' "$OUT" | grep -qF "0 differs, 0 missing, 0 additional" || dump_and_fail "drift reported against bundled sources" "$OUT"
echo "  ok"

# [5/17] jenesis: a recorded version the floor covers is refused, never installed and never
# run - the floor version itself as much as one below it
echo "[5/17] jenesis refuses a version that is no longer considered safe"
mkdir -p "$PROJ/sources"
printf 'module sdktest {}\n' > "$PROJ/sources/module-info.java"
UNSAFE="$TMPDIR/unsafe"
cp -r "$PROJ" "$UNSAFE"
printf '%s' "$UNSAFE_THROUGH" > "$UNSAFE/build/jenesis/jenesis.version"
set +e
OUT="$(cd "$UNSAFE" && "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse the floor version, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "every Jenesis up to ${UNSAFE_THROUGH} is no longer considered safe" || dump_and_fail "the refusal did not name the floor" "$OUT"
printf '%s' "$OUT" | grep -qF "is at version ${UNSAFE_THROUGH}" || dump_and_fail "the refusal did not name the recorded version" "$OUT"
printf '%s' "$OUT" | grep -qF "refusing to run" || dump_and_fail "an unsafe version was not refused" "$OUT"
if printf '%s' "$OUT" | grep -qF "a Java build tool"; then dump_and_fail "an unsafe version still ran the build" "$OUT"; fi
printf '%s' "$OUT" | grep -qF "jenesis-init" || dump_and_fail "the refusal did not point at jenesis-init" "$OUT"
printf '%s' "$OUT" | grep -qF "jenesis-unsafe [selectors]" || dump_and_fail "the refusal did not point at jenesis-unsafe" "$OUT"
printf '0.0.1' > "$UNSAFE/build/jenesis/jenesis.version"
set +e
OUT="$(cd "$UNSAFE" && "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse a version below the floor, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "is at version 0.0.1" || dump_and_fail "the refusal did not name the recorded version" "$OUT"
echo "  ok"

# [6/17] jenesis-unsafe: the same version, verified and run, because it carries no floor;
# `help` is a print-only goal, so it runs offline once a project descriptor exists.
echo "[6/17] jenesis-unsafe help"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis-unsafe" help 2>&1)" || dump_and_fail "jenesis-unsafe help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis-unsafe did not run the recorded version" "$OUT"
echo "  ok"

# [7/17] jenesis-make: runs the installed version directly, without the version lookup
echo "[7/17] jenesis-make help"
OUT="$(cd "$PROJ" && "${SDK_HOME}/bin/jenesis-make" help 2>&1)" || dump_and_fail "jenesis-make help exited non-zero" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "jenesis-make did not print the usage banner" "$OUT"
echo "  ok"

# [8/17] jenesis: a recorded version above the floor is resolved, verified and dispatched to
echo "[8/17] jenesis on a version above the floor"
SAFE_SDKMAN="$TMPDIR/sdkman"
mkdir -p "$SAFE_SDKMAN/candidates/jenesis"
cp -r "$SDK_HOME" "$SAFE_SDKMAN/candidates/jenesis/$SAFE"
FUTURE="$TMPDIR/future"
cp -r "$PROJ" "$FUTURE"
printf '%s' "$SAFE" > "$FUTURE/build/jenesis/jenesis.version"
OUT="$(cd "$FUTURE" && SDKMAN_DIR="$SAFE_SDKMAN" "${SDK_HOME}/bin/jenesis" help 2>&1)" || dump_and_fail "jenesis refused a version above the floor" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "a version above the floor was not run" "$OUT"
echo "  ok"

# [9/17] jenesis-unsafe: a patched vendored tree is refused, never run and never built from
echo "[9/17] jenesis-unsafe refuses an engine the vendored sources do not match"
PATCHED="$TMPDIR/patched"
cp -r "$PROJ" "$PATCHED"
echo "// local patch" >> "$PATCHED/build/jenesis/Platform.java"
set +e
OUT="$(cd "$PATCHED" && "${SDK_HOME}/bin/jenesis-unsafe" help 2>&1)"
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

# [10/17] jenesis: a stamp above the floor naming a version that cannot be installed is
# refused, not built
echo "[10/17] jenesis on an uninstalled stamp"
UNSTAMPED="$TMPDIR/unstamped"
cp -r "$PROJ" "$UNSTAMPED"
printf '99.9.9-ABSENT' > "$UNSTAMPED/build/jenesis/jenesis.version"
set +e
OUT="$(cd "$UNSTAMPED" && SDKMAN_DIR="$TMPDIR/no-sdkman" PATH="/usr/bin:/bin" "${SDK_HOME}/bin/jenesis" help 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "expected the launcher to refuse an uninstallable stamp, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF "no installed Jenesis matches" || dump_and_fail "the refusal did not name the missing version" "$OUT"
printf '%s' "$OUT" | grep -qF "refusing to run" || dump_and_fail "an uninstalled stamp was not refused" "$OUT"
echo "  ok"

# [11/17] jenesis: a broken SDKMAN never aborts the launcher, it reaches the same refusal
echo "[11/17] jenesis with a broken SDKMAN"
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

# [12/17] jenesis: the refusal names the manager that installed this launcher, so a Scoop
# installation is not told to reach for SDKMAN
echo "[12/17] jenesis names the installing package manager"
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

# [13/17] jenesis-validate: a class file beside its source is inert and stays unreported,
# one whose source is gone is live code and is named
echo "[13/17] jenesis-validate on locally compiled classes"
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

# [14/17] jenesis: a command linked onto PATH, the way mise installs it, follows the link
# back to its installation - and to the jenesis-unsafe beside it - through a relative link
# as well as an absolute one
echo "[14/17] jenesis through linked commands"
mkdir -p "$TMPDIR/links" "$TMPDIR/path"
ln -s "${SDK_HOME}/bin/jenesis" "$TMPDIR/links/jenesis"
ln -s "${SDK_HOME}/bin/jenesis-unsafe" "$TMPDIR/links/jenesis-unsafe"
ln -s "${SDK_HOME}/bin/jenesis-make" "$TMPDIR/links/jenesis-make"
ln -s ../links/jenesis "$TMPDIR/path/jenesis"
OUT="$(cd "$FUTURE" && SDKMAN_DIR="$SAFE_SDKMAN" "$TMPDIR/path/jenesis" help 2>&1)" || dump_and_fail "a linked jenesis did not find its installation" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "a linked jenesis did not run the recorded version" "$OUT"
OUT="$(cd "$PROJ" && "$TMPDIR/links/jenesis-unsafe" help 2>&1)" || dump_and_fail "a linked jenesis-unsafe did not find its installation" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "a linked jenesis-unsafe did not run the recorded version" "$OUT"
OUT="$(cd "$PROJ" && "$TMPDIR/links/jenesis-make" help 2>&1)" || dump_and_fail "a linked jenesis-make did not find its installation" "$OUT"
printf '%s' "$OUT" | grep -qF "a Java build tool" || dump_and_fail "a linked jenesis-make did not print the usage banner" "$OUT"
echo "  ok"

# [15/17] jenesis-jdk: a toolchain version becomes the newest SDKMAN identifier it matches,
# skipping the JavaFX, CRaC and GraalVM-numbered builds that carry no plain Java version
echo "[15/17] jenesis-jdk with SDKMAN"
FAKE_SDKMAN="$TMPDIR/fake-sdkman"
mkdir -p "$FAKE_SDKMAN/bin"
cat > "$FAKE_SDKMAN/list.txt" <<'EOL'
 Vendor         | Use | Version            | Identifier
--------------------------------------------------------------------------------
 GraalVM CE     |     | 25.3.4+1.r25       | 25.3.4+1.r25-graalce
                |     | 25.0.2             | 25.0.2-graalce
 Java.net       |     | 28.0.0+ea.15       | 28.0.0+ea.15-open
                |     | 27.ea.25           | 27.ea.25-open
                |     | 27.ea.9            | 27.ea.9-open
 Temurin        |     | 26.0.2+1.1         | 26.0.2+1.1-tem
                |     | 25.0.4             | 25.0.4-tem
                |     | 25.0.3             | 25.0.3-tem
 Zulu           |     | 25.0.4.fx          | 25.0.4.fx-zulu
                |     | 25.0.4+1.1         | 25.0.4+1.1-zulu
                |     | 25.0.3.crac        | 25.0.3.crac-zulu
EOL
cat > "$FAKE_SDKMAN/bin/sdkman-init.sh" <<'EOL'
sdk() {
    case "$1" in
        list) cat "$SDKMAN_DIR/list.txt" ;;
        install) echo "$3" > "$SDKMAN_DIR/installed"; mkdir -p "$SDKMAN_DIR/candidates/java/$3" ;;
    esac
}
EOL
for CASE in "25 25.0.4-tem" "25.0.3-temurin 25.0.3-tem" "25-zulu 25.0.4+1.1-zulu" \
        "25-graalvm-community 25.0.2-graalce" "27-ea 27.ea.25-open"; do
    REQUESTED="${CASE% *}"
    EXPECTED="${CASE#* }"
    OUT="$(SDKMAN_DIR="$FAKE_SDKMAN" "${SDK_HOME}/bin/jenesis-jdk" --tool=sdkman "$REQUESTED" 2>&1)" \
        || dump_and_fail "jenesis-jdk failed for $REQUESTED" "$OUT"
    [ "$(cat "$FAKE_SDKMAN/installed")" = "$EXPECTED" ] \
        || dump_and_fail "jenesis-jdk installed $(cat "$FAKE_SDKMAN/installed") for $REQUESTED, not $EXPECTED" "$OUT"
done
mkdir -p "$FAKE_SDKMAN/candidates/jenesis/$VERSION"
cp -R "${SDK_HOME}/bin" "$FAKE_SDKMAN/candidates/jenesis/$VERSION/bin"
OUT="$(SDKMAN_DIR="$FAKE_SDKMAN" "$FAKE_SDKMAN/candidates/jenesis/$VERSION/bin/jenesis-jdk" 25 2>&1)" \
    || dump_and_fail "jenesis-jdk installed by SDKMAN did not call SDKMAN back" "$OUT"
[ "$(cat "$FAKE_SDKMAN/installed")" = "25.0.4-tem" ] \
    || dump_and_fail "jenesis-jdk installed by SDKMAN installed $(cat "$FAKE_SDKMAN/installed") for 25" "$OUT"
set +e
OUT="$(SDKMAN_DIR="$FAKE_SDKMAN" "${SDK_HOME}/bin/jenesis-jdk" --tool=sdkman 25-nosuchvendor 2>&1)"
RC=$?
set -e
[ "$RC" = "2" ] || dump_and_fail "an unknown vendor word did not exit 2, got $RC" "$OUT"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-jdk" 025 2>&1)"
RC=$?
set -e
[ "$RC" = "64" ] || dump_and_fail "a malformed version did not exit 64, got $RC" "$OUT"
echo "  ok"

# [16/17] jenesis-jdk: mise gets its vendor names and resolves the newest build itself, and
# without --tool it calls back only the tool that installed Jenesis
echo "[16/17] jenesis-jdk with mise, and no tool of its own"
mkdir -p "$TMPDIR/fake-bin"
printf '#!/bin/sh\necho "$*" > "%s"\n' "$TMPDIR/mise-arguments" > "$TMPDIR/fake-bin/mise"
chmod +x "$TMPDIR/fake-bin/mise"
for CASE in "25.0.3-temurin java@temurin-25.0.3" "25.0.3-zulu java@zulu-25" "25-semeru java@semeru-openj9-25" \
        "25-oracle-jvmci java@oracle-graalvm-25" "25 java@temurin-25"; do
    REQUESTED="${CASE% *}"
    EXPECTED="install ${CASE#* }"
    OUT="$(PATH="$TMPDIR/fake-bin:$PATH" "${SDK_HOME}/bin/jenesis-jdk" --tool=mise "$REQUESTED" 2>&1)" \
        || dump_and_fail "jenesis-jdk failed for $REQUESTED" "$OUT"
    [ "$(cat "$TMPDIR/mise-arguments")" = "$EXPECTED" ] \
        || dump_and_fail "jenesis-jdk ran mise $(cat "$TMPDIR/mise-arguments") for $REQUESTED, not $EXPECTED" "$OUT"
done
set +e
OUT="$(PATH="$TMPDIR/fake-bin:$PATH" "${SDK_HOME}/bin/jenesis-jdk" --tool=mise 26-ea 2>&1)"
RC=$?
set -e
[ "$RC" = "2" ] || dump_and_fail "an early-access build with mise did not exit 2, got $RC" "$OUT"
set +e
OUT="$("${SDK_HOME}/bin/jenesis-jdk" 25 2>&1)"
RC=$?
set -e
[ "$RC" = "1" ] || dump_and_fail "jenesis-jdk outside SDKMAN and mise chose a tool of its own, got $RC" "$OUT"
printf '%s' "$OUT" | grep -qF -- "--tool=sdkman" || dump_and_fail "jenesis-jdk outside SDKMAN and mise did not name --tool" "$OUT"
echo "  ok"

# [17/17] jenesis-make and jenesis-exec name their own jenesis-jdk as the toolchain installer
# when SDKMAN or mise installed them, and leave a variable the user set, even an empty one, alone
echo "[17/17] the toolchain installer of an SDKMAN or mise install"
FAKE_JAVA="$TMPDIR/fake-java"
mkdir -p "$FAKE_JAVA/bin"
cat > "$FAKE_JAVA/bin/java" <<'EOL'
#!/bin/sh
if [ "$1" = "-version" ]; then
    echo 'openjdk version "25" 2025-09-16' >&2
else
    echo "installer=${JENESIS_TOOLCHAIN_INSTALLER-unset}"
fi
EOL
chmod +x "$FAKE_JAVA/bin/java"
CANDIDATE="$TMPDIR/sdkman-home/candidates/jenesis/$VERSION"
mkdir -p "$CANDIDATE"
cp -R "${SDK_HOME}/bin" "$CANDIDATE/bin"
CANDIDATE="$(cd "$CANDIDATE" && pwd -P)"
MISE_INSTALL="$TMPDIR/mise-data/installs/jenesis/$VERSION"
mkdir -p "$MISE_INSTALL"
cp -R "${SDK_HOME}/bin" "$MISE_INSTALL/bin"
MISE_INSTALL="$(cd "$MISE_INSTALL" && pwd -P)"
for COMMAND in jenesis-make jenesis-exec; do
    OUT="$(unset JENESIS_TOOLCHAIN_INSTALLER; MISE_DATA_DIR="$TMPDIR/mise-data" JAVA_HOME="$FAKE_JAVA" "$MISE_INSTALL/bin/$COMMAND" 2>&1)"
    [ "$OUT" = "installer=$MISE_INSTALL/bin/jenesis-jdk" ] \
        || dump_and_fail "$COMMAND installed by mise did not name its jenesis-jdk" "$OUT"
    OUT="$(unset JENESIS_TOOLCHAIN_INSTALLER; SDKMAN_DIR="$TMPDIR/sdkman-home" JAVA_HOME="$FAKE_JAVA" "$CANDIDATE/bin/$COMMAND" 2>&1)"
    [ "$OUT" = "installer=$CANDIDATE/bin/jenesis-jdk" ] \
        || dump_and_fail "$COMMAND installed by SDKMAN did not name its jenesis-jdk" "$OUT"
    OUT="$(JENESIS_TOOLCHAIN_INSTALLER= SDKMAN_DIR="$TMPDIR/sdkman-home" JAVA_HOME="$FAKE_JAVA" "$CANDIDATE/bin/$COMMAND" 2>&1)"
    [ "$OUT" = "installer=" ] || dump_and_fail "$COMMAND replaced an installer the user left empty" "$OUT"
    OUT="$(unset JENESIS_TOOLCHAIN_INSTALLER; SDKMAN_DIR="$TMPDIR/sdkman-home" JAVA_HOME="$FAKE_JAVA" "${SDK_HOME}/bin/$COMMAND" 2>&1)"
    [ "$OUT" = "installer=unset" ] || dump_and_fail "$COMMAND outside SDKMAN named an installer" "$OUT"
done
echo "  ok"

echo "sdk-tests: all checks passed"
