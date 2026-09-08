#!/usr/bin/env bash
#
# Tests for the bootstrap installer (install.sh). Each case installs into a
# throwaway project under a temporary directory and asserts what landed there,
# building the result wherever the install is meant to be usable. Nothing
# touches the repository this runs from.
#
# The sources are fetched for the ref named by JENESIS_TEST_REF (default: main),
# so the script under test is the local one while its payload comes from GitHub.
# Cases that need symbolic links are skipped where the filesystem has none.
#
# Exits 0 only when every case passes.

set -eu

SCRIPT="$(cd "$(dirname "$0")" && pwd -P)/install.sh"
REF="${JENESIS_TEST_REF:-main}"
REPO="${JENESIS_GITHUB_REPO:-jenesis/jenesis}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASSED=0
fail() { echo "install-tests: FAILED - $*" >&2; exit 1; }
pass() { PASSED=$((PASSED + 1)); echo "install-tests: ok - $*"; }

# Windows shells can answer `ln -s` by copying, so the probe only counts when
# what it produced is a link.
SYMLINKS=1
: > "$TMP/.probe-target"
ln -s "$TMP/.probe-target" "$TMP/.probe-link" 2>/dev/null || SYMLINKS=0
[ -L "$TMP/.probe-link" ] || SYMLINKS=0
rm -f "$TMP/.probe-link" "$TMP/.probe-target"

# A project small enough to build without resolving a single dependency.
scaffold() {
    mkdir -p "$TMP/$1/sources"
    printf 'module demo.sample {}\n' > "$TMP/$1/sources/module-info.java"
}

repository() {
    git -C "$TMP/$1" init -q .
    git -C "$TMP/$1" config user.email install-tests@example.com
    git -C "$TMP/$1" config user.name "install tests"
}

# install <name> [VAR=VALUE ...] - runs the installer against that project.
install() {
    local name="$1"; shift
    env JENESIS_TARGET="$TMP/$name" JENESIS_GITHUB_REPO="$REPO" "$@" \
        bash "$SCRIPT" "$REF" >"$TMP/$name.log" 2>&1
}

builds() {
    (cd "$TMP/$1" && java build/jenesis/Make.java >"$TMP/$1.build.log" 2>&1)
}

# --- vendor -----------------------------------------------------------------

scaffold vendored
install vendored || fail "vendoring into a plain directory: $(cat "$TMP/vendored.log")"
[ -d "$TMP/vendored/build/jenesis" ] && [ ! -L "$TMP/vendored/build/jenesis" ] \
    || fail "vendoring did not leave a real build/jenesis directory"
[ -f "$TMP/vendored/build/jenesis/jenesis.version" ] || fail "vendoring left no jenesis.version stamp"
builds vendored || fail "a vendored project does not build: $(cat "$TMP/vendored.build.log")"
pass "vendors into a plain directory, and the project builds"

# --- auto does not reach for a submodule ------------------------------------

scaffold auto
repository auto
install auto || fail "auto in a repository: $(cat "$TMP/auto.log")"
[ -f "$TMP/auto/.gitmodules" ] && fail "auto added a submodule to a git project, which is vendor's job"
[ -d "$TMP/auto/build/jenesis" ] && [ ! -L "$TMP/auto/build/jenesis" ] \
    || fail "auto did not vendor into a git project that tracks no submodule"
pass "auto vendors in a git repository rather than adding a submodule"

# --- submodule --------------------------------------------------------------

if [ "$SYMLINKS" = "1" ]; then
    scaffold submodule
    repository submodule
    install submodule JENESIS_MODE=submodule \
        || fail "adding a submodule: $(cat "$TMP/submodule.log")"
    grep -q 'path = .jenesis/upstream' "$TMP/submodule/.gitmodules" \
        || fail "no submodule recorded at .jenesis/upstream"
    grep -q 'shallow = true' "$TMP/submodule/.gitmodules" || fail "the submodule was not recorded as shallow"
    [ -L "$TMP/submodule/build/jenesis" ] || fail "build/jenesis was not linked into the submodule"
    [ -f "$TMP/submodule/build/jenesis/Make.java" ] || fail "the build/jenesis link does not resolve"
    git -C "$TMP/submodule" diff --cached --name-only | grep -q '.gitmodules' \
        || fail "the submodule was not staged in the superproject"
    builds submodule || fail "a submodule project does not build: $(cat "$TMP/submodule.build.log")"
    [ -z "$(git -C "$TMP/submodule/.jenesis/upstream" status --porcelain)" ] \
        || fail "building dirtied the submodule, so its path collides with the build's own state"
    pass "adds a submodule at .jenesis/upstream, links it, and the project builds"

    install submodule JENESIS_MODE=submodule \
        || fail "re-running against an existing submodule: $(cat "$TMP/submodule.log")"
    [ "$(grep -c 'path = ' "$TMP/submodule/.gitmodules")" = "1" ] \
        || fail "re-running added a second submodule entry"
    pass "re-running updates the submodule it already added"

    scaffold converted
    repository converted
    install converted || fail "vendoring before conversion: $(cat "$TMP/converted.log")"
    install converted JENESIS_MODE=submodule \
        || fail "converting a vendored project: $(cat "$TMP/converted.log")"
    [ -L "$TMP/converted/build/jenesis" ] || fail "conversion left the vendored directory in place"
    pass "converts a vendored installation into a submodule"
else
    echo "install-tests: skipping the submodule cases - this filesystem has no symbolic links"
fi

# --- submodule mode refuses what it cannot do -------------------------------

mkdir -p "$TMP/plain"
if install plain JENESIS_MODE=submodule; then
    fail "submodule mode accepted a target that is not a git repository"
fi
grep -q "needs a git repository" "$TMP/plain.log" \
    || fail "refusing a non-repository did not say why: $(cat "$TMP/plain.log")"
pass "submodule mode refuses a target that is not a git repository"

echo "install-tests: ${PASSED} case(s) passed"
