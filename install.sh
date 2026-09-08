#!/usr/bin/env bash
#
# Jenesis bootstrap installer.
#
# Installs or updates Jenesis in a project. Two modes:
#
#   * vendor    - copy the bootstrap sources from a release's sources jar into
#                 build/jenesis (self-contained, no git required). This is the
#                 default for projects that do not track Jenesis as a submodule.
#   * submodule - track Jenesis as a git submodule. An existing one (a .gitmodules
#                 entry whose URL points at the Jenesis repo) is checked out to the
#                 requested ref; a project that has none gets one added, at
#                 build/.upstream, with build/jenesis linked into it. Either way
#                 the new commit is staged in the superproject.
#
# Auto picks submodule when the project already tracks one and vendor otherwise,
# so an existing setup keeps working and a fresh project stays self-contained.
# Ask for JENESIS_MODE=submodule to have one added.
#
# By default the latest published release is installed. An optional argument
# pins an arbitrary git ref instead - a tag, a commit, or a branch:
#
#     curl -fsSL https://get.jenesis.build | bash               # latest release
#     curl -fsSL https://get.jenesis.build | bash -s -- v0.6.1  # a tag
#     curl -fsSL https://get.jenesis.build | bash -s -- main    # a branch
#     curl -fsSL https://get.jenesis.build | bash -s -- 1f48f48 # a commit
#
# When a ref is given, vendor mode copies the bootstrap sources straight from
# the repository tree at that ref (so unreleased branches and commits work too),
# and submodule mode checks the submodule out to it.
#
# Environment variables:
#   JENESIS_REF           Git ref to install (tag, commit, or branch); same as
#                         the positional argument, for when passing one is awkward
#   JENESIS_VERSION       Release version to install (default: latest GitHub release);
#                         ignored when a ref is given
#   JENESIS_TARGET        Target project directory (default: current working directory)
#   JENESIS_GITHUB_REPO   Source repository, owner/name (default: jenesis/jenesis)
#   JENESIS_MODE          auto (default) | vendor | submodule
#   JENESIS_SUBMODULE_PATH  Where a newly added submodule goes (default: build/.upstream)
#
# After the script completes, build the project with:
#
#     java build/jenesis/Make.java
#
set -e

GITHUB_REPO="${JENESIS_GITHUB_REPO:-jenesis/jenesis}"
MODE="${JENESIS_MODE:-auto}"

say() { echo "jenesis-install: $*"; }
die() { echo "jenesis-install: $*" >&2; exit 1; }

case "$MODE" in
    auto|vendor|submodule) ;;
    *) die "JENESIS_MODE must be one of: auto, vendor, submodule (got '$MODE')" ;;
esac

if command -v curl >/dev/null 2>&1; then
    fetch_to()     { curl -fsSL "$1" -o "$2"; }
    fetch_stdout() { curl -fsSL "$1"; }
elif command -v wget >/dev/null 2>&1; then
    fetch_to()     { wget -q "$1" -O "$2"; }
    fetch_stdout() { wget -q "$1" -O -; }
else
    die "neither 'curl' nor 'wget' found - install one and retry"
fi

TARGET="${JENESIS_TARGET:-$PWD}"
[ -d "$TARGET" ] || die "target '$TARGET' is not a directory"

# --- resolve the ref or release version to install --------------------------
#
# A positional argument (or JENESIS_REF) pins an arbitrary git ref: a tag, a
# commit, or a branch. When given, that ref is installed verbatim - the
# submodule is checked out to it, and vendor mode copies the bootstrap sources
# from the repository tree at that ref. Without it, the latest release (or
# JENESIS_VERSION) is installed from its published sources jar. RELEASE marks
# which path applies; VERSION is only set on the release path.

REF="${1:-${JENESIS_REF:-}}"
if [ -n "$REF" ]; then
    RELEASE=0
    say "using ref ${REF}"
elif [ -n "${JENESIS_VERSION:-}" ]; then
    RELEASE=1
    VERSION="${JENESIS_VERSION#v}"
    REF="v${VERSION}"
    say "using pinned version ${VERSION}"
else
    RELEASE=1
    say "resolving latest release from github.com/${GITHUB_REPO}"
    META="$(fetch_stdout "https://api.github.com/repos/${GITHUB_REPO}/releases/latest")" \
        || die "failed to query GitHub API - set JENESIS_VERSION or pass a ref to bypass version lookup"
    VERSION="$(printf '%s' "$META" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"v\{0,1\}\([^"]*\)".*/\1/p' | head -n1)"
    [ -n "$VERSION" ] || die "could not parse latest version from GitHub API response"
    REF="v${VERSION}"
    say "latest version is ${VERSION}"
fi

# --- detect whether the project tracks Jenesis as a submodule ----------------

# Prints "&lt;name&gt;\t&lt;path&gt;" if .gitmodules has an entry whose URL points at the
# Jenesis repo, otherwise prints nothing. The name comes back too because the
# shallow flag is written against it, not against the path. Always returns 0 so
# it is safe to call from a command substitution under 'set -e'.
detect_submodule_path() {
    local key url name
    command -v git >/dev/null 2>&1 || return 0
    [ -f "$TARGET/.gitmodules" ] || return 0
    git config -f "$TARGET/.gitmodules" --get-regexp '^submodule\..*\.url$' 2>/dev/null \
    | while IFS=' ' read -r key url; do
        case "$url" in
            *"${GITHUB_REPO}" | *"${GITHUB_REPO}.git" | *"${GITHUB_REPO}/")
                name="${key#submodule.}"; name="${name%.url}"
                printf '%s\t%s\n' "$name" \
                    "$(git config -f "$TARGET/.gitmodules" --get "submodule.${name}.path")"
                break
                ;;
        esac
    done
    return 0
}

SUBMODULE_NAME=""
SUBMODULE_PATH=""
if [ "$MODE" != "vendor" ]; then
    SUBMODULE_ENTRY="$(detect_submodule_path)"
    if [ -n "$SUBMODULE_ENTRY" ]; then
        SUBMODULE_NAME="${SUBMODULE_ENTRY%%	*}"
        SUBMODULE_PATH="${SUBMODULE_ENTRY#*	}"
    fi
fi

# Asked for a submodule and the project has none yet: add one. The checkout,
# the ref and the staging are the same work as an update, so this only creates
# the entry and then falls through to the block below. Auto never adds one - it
# takes a submodule the project already tracks, and vendors otherwise.
CREATED_SUBMODULE=0
if [ "$MODE" = "submodule" ] && [ -z "$SUBMODULE_PATH" ]; then
    command -v git >/dev/null 2>&1 || die "JENESIS_MODE=submodule requires git, which was not found"
    git -C "$TARGET" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
        || die "JENESIS_MODE=submodule needs a git repository at ${TARGET} - run 'git init' there first, or use JENESIS_MODE=vendor"
    SUBMODULE_PATH="${JENESIS_SUBMODULE_PATH:-build/.upstream}"
    SUBMODULE_NAME="$SUBMODULE_PATH"
    [ -e "$TARGET/$SUBMODULE_PATH" ] \
        && die "cannot add a submodule at '${SUBMODULE_PATH}': something is already there"
    say "adding Jenesis as a submodule at '${SUBMODULE_PATH}'"
    git -C "$TARGET" submodule add --depth 1 "https://github.com/${GITHUB_REPO}.git" "$SUBMODULE_PATH" >/dev/null 2>&1 \
        || git -C "$TARGET" submodule add "https://github.com/${GITHUB_REPO}.git" "$SUBMODULE_PATH" >/dev/null 2>&1 \
        || die "failed to add the Jenesis submodule at '${SUBMODULE_PATH}'"
    CREATED_SUBMODULE=1
fi

# --- submodule mode: move the existing submodule to the requested ref --------

if [ -n "$SUBMODULE_PATH" ]; then
    command -v git >/dev/null 2>&1 || die "git is required to update a Jenesis submodule"
    SUB="$TARGET/$SUBMODULE_PATH"
    say "detected Jenesis submodule at '${SUBMODULE_PATH}' - updating to ${REF}"

    # Jenesis is read at one pinned commit and its history is never browsed from
    # the consuming project, so record the submodule as shallow before the first
    # clone - after it, the flag only governs the next fresh checkout.
    if [ -n "$SUBMODULE_NAME" ] \
        && [ "$(git config -f "$TARGET/.gitmodules" --get "submodule.${SUBMODULE_NAME}.shallow" 2>/dev/null)" != "true" ]; then
        git config -f "$TARGET/.gitmodules" "submodule.${SUBMODULE_NAME}.shallow" true \
            && say "recorded submodule '${SUBMODULE_PATH}' as shallow in .gitmodules"
    fi

    git -C "$TARGET" submodule update --init --depth 1 -- "$SUBMODULE_PATH" \
        || git -C "$TARGET" submodule update --init -- "$SUBMODULE_PATH" \
        || die "failed to initialize submodule '${SUBMODULE_PATH}'"
    # Fetch just the requested ref at depth 1. A server that refuses a bare
    # commit in a want, or an older git, falls back to the full fetch - a
    # shallower clone is a preference here, never a requirement.
    git -C "$SUB" fetch --quiet --depth 1 origin "$REF" 2>/dev/null \
        || git -C "$SUB" fetch --quiet --tags origin \
        || die "failed to fetch in submodule '${SUBMODULE_PATH}'"
    git -C "$SUB" checkout --quiet "$REF" 2>/dev/null \
        || git -C "$SUB" checkout --quiet FETCH_HEAD \
        || die "failed to check out '${REF}' in '${SUBMODULE_PATH}' - does the ref exist?"
    git -C "$TARGET" add "$SUBMODULE_PATH" \
        || die "failed to stage the updated submodule pointer"

    # A project that vendored Jenesis before, or one that never had it, has no
    # build/jenesis pointing into the submodule yet. Without that link the entry
    # point the documentation names is not there, so the install is not usable.
    if [ "$CREATED_SUBMODULE" = "1" ]; then
        LINK="$TARGET/build/jenesis"
        if [ -d "$LINK" ] && [ ! -L "$LINK" ]; then
            say "replacing the vendored build/jenesis with a link into the submodule"
            rm -rf "$LINK"
        fi
        mkdir -p "$TARGET/build"
        if [ ! -e "$LINK" ] && [ ! -L "$LINK" ]; then
            # The link lives in build/, so a submodule under build/ is named from
            # there directly and anything else steps back out to the root first.
            case "$SUBMODULE_PATH" in
                build/*) LINK_TARGET="${SUBMODULE_PATH#build/}/sources/build/jenesis" ;;
                *)       LINK_TARGET="../${SUBMODULE_PATH}/sources/build/jenesis" ;;
            esac
            ln -s "$LINK_TARGET" "$LINK" \
                || die "failed to link build/jenesis into '${SUBMODULE_PATH}' - this filesystem may not support symbolic links, so use JENESIS_MODE=vendor"
            git -C "$TARGET" add "build/jenesis" >/dev/null 2>&1 || true
            say "linked build/jenesis to ${SUBMODULE_PATH}/sources/build/jenesis"
        fi
        say "added submodule '${SUBMODULE_PATH}' at ${REF} (staged in the superproject; commit when ready)"
    else
        say "updated submodule '${SUBMODULE_PATH}' to ${REF} (staged in the superproject; commit when ready)"
    fi
    say "next: run 'java build/jenesis/Make.java' from ${TARGET}"
    exit 0
fi

# --- vendor mode: copy the bootstrap sources into build/jenesis --------------

if command -v unzip >/dev/null 2>&1; then
    extract() { unzip -q -d "$2" "$1"; }
elif [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/jar" ]; then
    extract() { (cd "$2" && "${JAVA_HOME}/bin/jar" xf "$1"); }
elif command -v jar >/dev/null 2>&1; then
    extract() { (cd "$2" && jar xf "$1"); }
else
    die "no extractor found - install 'unzip' or a JDK (with 'jar' on PATH or via JAVA_HOME)"
fi

TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT
mkdir -p "$TMPDIR/extract"

if [ "$RELEASE" = "1" ]; then
    # A published release: take the blessed sources jar from the release assets.
    JAR_URL="https://github.com/${GITHUB_REPO}/releases/download/v${VERSION}/build.jenesis-${VERSION}-sources.jar"
    say "downloading ${JAR_URL}"
    fetch_to "$JAR_URL" "$TMPDIR/sources.jar" \
        || die "failed to download $JAR_URL - check that the release exists on GitHub"
    extract "$TMPDIR/sources.jar" "$TMPDIR/extract" \
        || die "failed to extract sources jar"
    SRC_DIR="$TMPDIR/extract/build/jenesis"
    STAMP="$VERSION"
else
    # An arbitrary ref: take the repository tree at that ref as a zip archive
    # (works for tags, commits, and branches that have no published release).
    ZIP_URL="https://github.com/${GITHUB_REPO}/archive/${REF}.zip"
    say "downloading ${ZIP_URL}"
    fetch_to "$ZIP_URL" "$TMPDIR/source.zip" \
        || die "failed to download $ZIP_URL - does the ref '${REF}' exist on github.com/${GITHUB_REPO}?"
    extract "$TMPDIR/source.zip" "$TMPDIR/extract" \
        || die "failed to extract the source archive"
    SRC_DIR="$(find "$TMPDIR/extract" -type d -path '*/sources/build/jenesis' 2>/dev/null | head -n1)"
    STAMP="$REF"
fi

[ -n "$SRC_DIR" ] && [ -d "$SRC_DIR" ] \
    || die "could not locate build/jenesis in the downloaded sources - artifact layout unexpected"

DEST="$TARGET/build/jenesis"
if [ -d "$DEST" ]; then
    if [ -f "$DEST/jenesis.version" ]; then
        say "replacing existing build/jenesis (was version $(cat "$DEST/jenesis.version"))"
    else
        say "replacing existing build/jenesis (unknown previous version)"
    fi
    rm -rf "$DEST"
fi
mkdir -p "$TARGET/build"
cp -R "$SRC_DIR" "$DEST"
printf '%s' "$STAMP" > "$DEST/jenesis.version"
say "installed Jenesis ${STAMP} to ${DEST}"
say "next: run 'java build/jenesis/Make.java' from ${TARGET}"
