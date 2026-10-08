#!/usr/bin/env bash
set -euo pipefail

REPOSITORY=
ARTIFACTS=()
TAG='v{version}'
CENTRAL=https://repo1.maven.org/maven2
MAVEN="${MAVEN_REPOSITORY_URI-$CENTRAL}"
APPLY=false
KINDS=(.jar .pom)
VERSIONS=()

usage() {
  echo "Usage: $0 --repository=<owner>/<name> --artifact=<groupId>:<artifactId> [--artifact=...]" >&2
  echo "       [--tag=v{version}] [--maven=<url>[|<groupId>...][,...]] [--sources] [--javadoc] [--apply] [<version>...]" >&2
  echo "Attaches the jar and the POM of each artifact, its sources and javadoc jars with --sources and" >&2
  echo "--javadoc, with their signatures and a .sha256 of each, as the Maven remotes hold them, to the GitHub" >&2
  echo "release of every version that lacks them; without --apply it only lists what it would upload." >&2
  echo "Without versions, every release whose tag matches --tag is patched. The Maven remotes default to" >&2
  echo "MAVEN_REPOSITORY_URI, read as Jenesis reads it, and else to $CENTRAL." >&2
  exit 2
}

for ARGUMENT in "$@"; do
  case "$ARGUMENT" in
    --apply) APPLY=true ;;
    --sources) KINDS+=(-sources.jar) ;;
    --javadoc) KINDS+=(-javadoc.jar) ;;
    --repository=*) REPOSITORY="${ARGUMENT#*=}" ;;
    --artifact=*) ARTIFACTS+=("${ARGUMENT#*=}") ;;
    --tag=*) TAG="${ARGUMENT#*=}" ;;
    --maven=*) MAVEN="${ARGUMENT#*=}" ;;
    -*) usage ;;
    *) VERSIONS+=("$ARGUMENT") ;;
  esac
done

[[ "$REPOSITORY" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "--repository=<owner>/<name> is required, got '$REPOSITORY'" >&2; usage; }
[ ${#ARTIFACTS[@]} -gt 0 ] || { echo "--artifact=<groupId>:<artifactId> is required" >&2; usage; }
for COORDINATE in "${ARTIFACTS[@]}"; do
  [[ "$COORDINATE" =~ ^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+$ ]] || { echo "--artifact expects <groupId>:<artifactId>, got '$COORDINATE'" >&2; exit 2; }
done
[[ "$TAG" == *'{version}'* ]] || { echo "--tag must name {version}, such as v{version}, got '$TAG'" >&2; exit 2; }
REMOTES=()
IFS=',' read -ra ENTRIES <<< "$MAVEN"
for ENTRY in "${ENTRIES[@]}"; do
  ENTRY="${ENTRY#"${ENTRY%%[![:space:]]*}"}"
  ENTRY="${ENTRY%"${ENTRY##*[![:space:]]}"}"
  if [ -z "$ENTRY" ]; then
    continue
  elif [ "$ENTRY" = @ ]; then
    REMOTES+=("$CENTRAL")
  elif [[ "$ENTRY" =~ ^https?://[^|@[:space:]]+(\|[A-Za-z0-9_.-]+)*$ ]]; then
    URL="${ENTRY%%|*}"
    REMOTES+=("${URL%/}${ENTRY:${#URL}}")
  else
    echo "The Maven remotes name '$ENTRY', where an entry is an http or https URL, optionally followed by" >&2
    echo "|<groupId> filters, or a bare @ for $CENTRAL; @<name> is not spliced here" >&2
    exit 2
  fi
done
[ ${#REMOTES[@]} -gt 0 ] || { echo "The Maven remotes name no repository: '$MAVEN'" >&2; exit 2; }
for VERSION in "${VERSIONS[@]}"; do
  [[ "$VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9._+-]*$ ]] || { echo "'$VERSION' is not a version" >&2; exit 2; }
done

for TOOL in gh curl sha1sum sha256sum; do
  command -v "$TOOL" > /dev/null || { echo "$TOOL is required" >&2; exit 1; }
done

if [ ${#VERSIONS[@]} -eq 0 ]; then
  PREFIX="${TAG%%\{version\}*}"
  SUFFIX="${TAG#*\{version\}}"
  while IFS= read -r RELEASED; do
    if [[ "$RELEASED" == "$PREFIX"* && "$RELEASED" == *"$SUFFIX" ]]; then
      VERSION="${RELEASED#"$PREFIX"}"
      VERSION="${VERSION%"$SUFFIX"}"
      [[ "$VERSION" =~ ^[0-9][A-Za-z0-9._+-]*$ ]] && VERSIONS+=("$VERSION")
    fi
  done < <(gh api --paginate "repos/$REPOSITORY/releases" --jq '.[].tag_name')
  [ ${#VERSIONS[@]} -gt 0 ] || { echo "No release of $REPOSITORY has a tag matching $TAG" >&2; exit 1; }
fi

fetch() {
  local STATUS
  STATUS="$(curl -sSL --retry 6 --retry-delay 10 -o "$1" -w '%{http_code}' "$2")" || STATUS=000
  case "$STATUS" in
    200) return 0 ;;
    404) return 1 ;;
    *) echo "$2 answered $STATUS" >&2; return 2 ;;
  esac
}

has() {
  printf '%s\n' "$PRESENT" | grep -qxF "$1"
}

serves() {
  local FILTER
  [[ "$1" != *"|"* ]] && return 0
  IFS='|' read -ra FILTERS <<< "${1#*|}"
  for FILTER in "${FILTERS[@]}"; do
    [[ "$2" == "$FILTER" || "$2" == "$FILTER".* ]] && return 0
  done
  return 1
}

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILED=0

for VERSION in "${VERSIONS[@]}"; do
  RELEASE="${TAG//\{version\}/$VERSION}"
  FOLDER="$WORK/$VERSION"
  mkdir -p "$FOLDER"
  if ! PRESENT="$(gh api "repos/$REPOSITORY/releases/tags/$RELEASE" --jq '.assets[].name')"; then
    echo "$RELEASE: no such release of $REPOSITORY, skipped" >&2
    FAILED=1
    continue
  fi
  UPLOAD=()
  for COORDINATE in "${ARTIFACTS[@]}"; do
    GROUP="${COORDINATE%%:*}"
    ARTIFACT="${COORDINATE#*:}"
    for KIND in "${KINDS[@]}"; do
      NAME="$ARTIFACT-$VERSION$KIND"
      if has "$NAME" && has "$NAME.asc" && has "$NAME.sha256"; then
        continue
      fi
      STATUS=1
      for REMOTE in "${REMOTES[@]}"; do
        serves "$REMOTE" "$GROUP" || continue
        SOURCE="${REMOTE%%|*}"
        BASE="$SOURCE/${GROUP//.//}/$ARTIFACT/$VERSION/$NAME"
        STATUS=0
        fetch "$FOLDER/$NAME" "$BASE" || STATUS=$?
        [ "$STATUS" -eq 1 ] || break
      done
      if [ "$STATUS" -eq 1 ]; then
        echo "$RELEASE: no Maven remote holds $NAME, skipped"
        continue
      elif [ "$STATUS" -ne 0 ] || ! fetch "$FOLDER/$NAME.sha1" "$BASE.sha1"; then
        echo "$RELEASE: $NAME could not be read from $SOURCE, skipped" >&2
        FAILED=1
        continue
      fi
      EXPECTED="$(awk '{print $1}' "$FOLDER/$NAME.sha1")"
      ACTUAL="$(sha1sum "$FOLDER/$NAME" | awk '{print $1}')"
      if [ "$EXPECTED" != "$ACTUAL" ]; then
        echo "$RELEASE: $NAME does not match its .sha1 ($ACTUAL, published $EXPECTED), skipped" >&2
        FAILED=1
        continue
      fi
      if has "$NAME"; then
        if ! fetch "$FOLDER/$NAME.released" "https://github.com/$REPOSITORY/releases/download/$RELEASE/$NAME"; then
          echo "$RELEASE: $NAME could not be read from the release, skipped" >&2
          FAILED=1
          continue
        elif ! cmp -s "$FOLDER/$NAME" "$FOLDER/$NAME.released"; then
          echo "$RELEASE: $NAME on the release differs from the one in $SOURCE, skipped" >&2
          FAILED=1
          continue
        fi
      else
        UPLOAD+=("$FOLDER/$NAME")
      fi
      if ! has "$NAME.asc"; then
        STATUS=0
        fetch "$FOLDER/$NAME.asc" "$BASE.asc" || STATUS=$?
        if [ "$STATUS" -eq 0 ]; then
          UPLOAD+=("$FOLDER/$NAME.asc")
        elif [ "$STATUS" -eq 1 ]; then
          echo "$RELEASE: $SOURCE holds no $NAME.asc, signature skipped"
        else
          echo "$RELEASE: $NAME.asc could not be read from $SOURCE, skipped" >&2
          FAILED=1
        fi
      fi
      if ! has "$NAME.sha256"; then
        sha256sum "$FOLDER/$NAME" | awk '{print $1}' > "$FOLDER/$NAME.sha256"
        UPLOAD+=("$FOLDER/$NAME.sha256")
      fi
    done
  done
  if [ ${#UPLOAD[@]} -eq 0 ]; then
    echo "$RELEASE: complete"
  elif [ "$APPLY" = true ]; then
    gh release upload "$RELEASE" "${UPLOAD[@]}" --repo "$REPOSITORY"
    printf "$RELEASE: uploaded %s\n" "${UPLOAD[@]##*/}"
  else
    printf "$RELEASE: would upload %s\n" "${UPLOAD[@]##*/}"
  fi
done

exit "$FAILED"
