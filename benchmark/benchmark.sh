#!/usr/bin/env bash
# Reproducible build-performance benchmarks for the Jenesis project.
# Usage, methodology and configuration are in benchmark/README.md.
#   benchmark/benchmark.sh {launch|make|compile|full|maven|pinning|aot|bootstrap|all}
#
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
cd "$ROOT"

MVN="${MVN:-mvn}"
MVN4="${MVN4:-}"
MAVEN_VERSION="${MAVEN_VERSION:-3.9.9}"
GRAALVM_HOME="${GRAALVM_HOME:-}"
RUNS_COLD="${RUNS_COLD:-5}"
RUNS_WARM="${RUNS_WARM:-3}"
ENGINE="build/jenesis"; [ -f $ENGINE/Make.java ] || ENGINE="sources/build/jenesis"
TOOL="$ROOT/.jenesis/tool"
CLASSES="$ROOT/.jenesis/classes"
STAMP="$ROOT/.jenesis/make.digest"
SCRATCH="$ROOT/.jenesis/benchmark-scratch"
EXE=""; NICMD=""; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) EXE=".exe"; NICMD=".cmd";; esac
NATIVE="$ROOT/.jenesis/benchmark-image"
NATIVE_BIN="$NATIVE$EXE"
EDIT="sources/build/jenesis/Project.java"
EDIT_TEST="tests/build/jenesis/test/SafeSegmentTest.java"
TF="$(mktemp)"; LOG="$(mktemp)"
SOURCE_DAEMON=0
trap 'stop_daemons; rm -rf "$SCRATCH"; rm -f "$TF" "$LOG"' EXIT
HAVE_GTIME=0; /usr/bin/time -f %e true >/dev/null 2>&1 && HAVE_GTIME=1
HAVE_NET=0; [ -r /proc/net/dev ] && HAVE_NET=1

note() { printf '\n== %s ==\n' "$*"; }
warn() { printf '!! %s\n' "$*" >&2; }

check_env() {
  if command -v powerprofilesctl >/dev/null 2>&1; then
    [ "$(powerprofilesctl get 2>/dev/null)" = performance ] \
      || warn "CPU power profile is not 'performance' - figures will be slow and noisy"
  fi
  java -version >/dev/null 2>&1 || { warn "no java on PATH / JAVA_HOME"; exit 1; }
}

rxtx() { [ "$HAVE_NET" = 1 ] && awk -F'[: ]+' '/eth|wl|en|wlp|enp|eno/{rx+=$3;tx+=$11} END{print rx+0" "tx+0}' /proc/net/dev || echo "0 0"; }

timeit() {
  local b0 b1 rc wall r0 t0; b0=$(rxtx)
  if [ "$HAVE_GTIME" = 1 ]; then
    /usr/bin/time -f "%e" -o "$TF" bash -c "$1" >"$LOG" 2>&1; rc=$?
    wall=$(cut -d' ' -f1 "$TF")
  else
    TIMEFORMAT=%R
    { time bash -c "$1" >"$LOG" 2>&1; } 2>"$TF"; rc=$?
    wall=$(tr -d '[:space:]' < "$TF" | tr ',' '.')
  fi
  b1=$(rxtx); set -- $b0; r0=$1; t0=$2; set -- $b1
  if [ "$HAVE_NET" = 1 ]; then echo "$wall $(( ($1-r0)/1024 )) $(( ($2-t0)/1024 )) $rc"; else echo "$wall na na $rc"; fi
}

median() {
  sort -n | awk '{a[NR]=$1} END{print (NR%2)?a[(NR+1)/2]:(a[NR/2]+a[NR/2+1])/2}'
}

result() {
  if [ "$HAVE_NET" = 1 ]; then printf 'median %6.2fs  (n=%s, net<=%sKB)\n' "$(printf '%s' "$1" | median)" "$2" "$3"
  else printf 'median %6.2fs  (n=%s)\n' "$(printf '%s' "$1" | median)" "$2"; fi
}

bench() {
  local label="$1" runs="$2" setup="$3" cmd="$4" i walls="" worst=0
  printf '%-30s ' "$label"
  for (( i=1; i<=runs; i++ )); do
    [ -n "$setup" ] && bash -c "$setup" >/dev/null 2>&1
    read -r wall rx tx rc <<<"$(timeit "$cmd")"
    walls+="$wall
"
    [ "$rc" != 0 ] && { printf 'FAILED (rc=%s)\n' "$rc"; tail -25 "$LOG" >&2; return 1; }
    [ "$rx" != na ] && [ "$((rx+tx))" -gt "$worst" ] && worst=$((rx+tx))
  done
  result "$walls" "$runs" "$worst"
}

bench_warm() {
  local label="$1" runs="$2" warmup="$3" cmd="$4" i walls="" worst=0
  bash -c "$warmup" >/dev/null 2>&1
  printf '%-30s ' "$label"
  for (( i=1; i<=runs; i++ )); do
    read -r wall rx tx rc <<<"$(timeit "$cmd")"
    walls+="$wall
"
    [ "$rc" != 0 ] && { printf 'FAILED (rc=%s)\n' "$rc"; tail -25 "$LOG" >&2; return 1; }
    [ "$rx" != na ] && [ "$((rx+tx))" -gt "$worst" ] && worst=$((rx+tx))
  done
  result "$walls" "$runs" "$worst"
}

build_tool() {
  [ -d "$TOOL" ] && return 0
  note "Precompiling the engine to $TOOL"
  rm -rf "$TOOL"; javac -d "$TOOL" $(find -L "$ENGINE" -name '*.java')
}

build_native() {
  [ -x "$NATIVE_BIN" ] && return 0
  [ -n "$GRAALVM_HOME" ] || { warn "GRAALVM_HOME not set - skipping the native launcher"; return 1; }
  build_tool
  note "Capturing reachability metadata and building the native launcher (one-off)"
  local cfg; cfg="$(mktemp -d)"
  "$GRAALVM_HOME/bin/java" $LAYOUT -Djenesis.process.factory=tool -Djenesis.test.skip=true \
      -agentlib:native-image-agent=config-output-dir="$cfg" \
      -cp "$TOOL" build.jenesis.Make build >/dev/null 2>&1
  rm -rf target
  "$GRAALVM_HOME/bin/native-image$NICMD" --no-fallback --add-modules jdk.compiler,jdk.jartool \
      -H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,com.sun.tools.javac.resources.ct,sun.tools.jar.resources.jar \
      -H:ConfigurationFileDirectories="$cfg" \
      -cp "$TOOL" build.jenesis.Make "$NATIVE" >/dev/null 2>&1 \
    && echo "native launcher (in-process javac): $NATIVE_BIN" || { warn "native-image build failed"; return 1; }
}

# The engine compiles itself into .jenesis/classes and stamps that folder with a digest
# of build/jenesis/*.java; removing both is what a first run on a fresh checkout sees.
# Scenario setups run in their own shell, so they clear the two by path rather than
# through a function of this one.
stop_daemons() {
  [ -d "$TOOL" ] && java $DAEMON -cp "$TOOL" build.jenesis.Make --stop >/dev/null 2>&1
  [ "$SOURCE_DAEMON" = 1 ] && java $DAEMON $ENGINE/Make.java --stop >/dev/null 2>&1
  SOURCE_DAEMON=0
  return 0
}

M_NT() { echo "$1 package -o -q -ntp -DskipTests"; }
M_F()  { echo "$1 package -o -q -ntp"; }
# The repository defaults to MODULAR_TO_MAVEN, which would stage a modular jar on top of
# the Maven jar; pin the Maven layout so a Jenesis build produces the same single artifact
# as the Maven baseline and the comparison stays like-for-like (as it was before the default).
LAYOUT="-Djenesis.project.layout=maven"
DAEMON="-Djenesis.make.daemon=true"
SRC_NT="java $LAYOUT -Djenesis.test.skip=true $ENGINE/Make.java build"
NOCOMPILE_NT="java $LAYOUT -Djenesis.make.compile=false -Djenesis.test.skip=true $ENGINE/Make.java build"
JAVAC_NT="java $LAYOUT -Djenesis.test.skip=true -cp $TOOL build.jenesis.Make build"
DAEMON_NT="java $LAYOUT $DAEMON -Djenesis.test.skip=true -cp $TOOL build.jenesis.Make build"
SRC_DAEMON_NT="java $LAYOUT $DAEMON -Djenesis.test.skip=true $ENGINE/Make.java build"
NATIVE_NT="$NATIVE_BIN $LAYOUT -Djenesis.test.skip=true build"
SRC_F="java $LAYOUT $ENGINE/Make.java build"

table_launch() {
  note "Table: build-tool launch overhead (run 'help', no project work)"
  build_tool
  bench      "source (compile=false)"   "$RUNS_WARM" "" "java -Djenesis.make.compile=false $ENGINE/Make.java help"
  bench      "source (engine recompiled)" "$RUNS_WARM" "rm -rf $CLASSES $STAMP" "java $ENGINE/Make.java help"
  bench_warm "source (engine cached)"   "$RUNS_WARM" "java $ENGINE/Make.java help" "java $ENGINE/Make.java help"
  bench_warm "precompiled"              "$RUNS_WARM" "java -cp $TOOL build.jenesis.Make help" "java -cp $TOOL build.jenesis.Make help"
  bench_warm "precompiled + daemon"     "$RUNS_WARM" "java $DAEMON -cp $TOOL build.jenesis.Make help" "java $DAEMON -cp $TOOL build.jenesis.Make help"
  SOURCE_DAEMON=1
  bench_warm "source + daemon"          "$RUNS_WARM" "java $DAEMON $ENGINE/Make.java help" "java $DAEMON $ENGINE/Make.java help"
  build_native && bench_warm "native"   "$RUNS_WARM" "$NATIVE_BIN help" "$NATIVE_BIN help"
  stop_daemons
}

table_make() {
  note "Table: how the engine is launched and kept - jenesis.make.compile, .classes, .daemon"
  build_tool
  echo "-- warm no-op build (nothing changed; the engine is the only variable) --"
  bench_warm "source (compile=false)"   "$RUNS_WARM" "rm -rf target; $NOCOMPILE_NT" "$NOCOMPILE_NT"
  bench_warm "source (engine cached)"   "$RUNS_WARM" "rm -rf target; $SRC_NT"       "$SRC_NT"
  bench_warm "precompiled"              "$RUNS_WARM" "rm -rf target; $JAVAC_NT"     "$JAVAC_NT"
  bench_warm "precompiled + daemon"     "$RUNS_WARM" "rm -rf target; $DAEMON_NT"    "$DAEMON_NT"
  SOURCE_DAEMON=1
  bench_warm "source + daemon"          "$RUNS_WARM" "rm -rf target; $SRC_DAEMON_NT" "$SRC_DAEMON_NT"
  echo "-- first build after an engine source changed (the stamp misses, the engine recompiles) --"
  echo "   in this repository every main source is an engine source; in a project that only vendors"
  echo "   the engine, editing its own sources leaves the stamp intact and costs the cached row above."
  bench "source (engine recompiled)"    "$RUNS_WARM" "rm -rf $CLASSES $STAMP; rm -rf target; $JAVAC_NT" "$SRC_NT"
  bench "precompiled (engine untouched)" "$RUNS_WARM" "rm -rf target; $JAVAC_NT" "$JAVAC_NT"
  stop_daemons
}

table_compile() {
  note "Table: compile + package, tests compiled but not run"
  build_tool; build_native
  local m; m="$(M_NT "$MVN")"
  echo "-- cold (empty target/) --"
  bench "maven3"      "$RUNS_COLD" "rm -rf target" "$m"
  bench "jenesis-source"  "$RUNS_COLD" "rm -rf target" "$SRC_NT"
  bench "jenesis-precompiled" "$RUNS_COLD" "rm -rf target" "$JAVAC_NT"
  bench "jenesis-daemon" "$RUNS_COLD" "rm -rf target" "$DAEMON_NT"
  [ -x "$NATIVE_BIN" ] && bench "jenesis-native" "$RUNS_COLD" "rm -rf target" "$NATIVE_NT"
  echo "-- warm no-op (nothing changed) --"
  bench_warm "maven3"      "$RUNS_WARM" "rm -rf target; $m" "$m"
  bench_warm "jenesis-source"  "$RUNS_WARM" "rm -rf target; $SRC_NT" "$SRC_NT"
  bench_warm "jenesis-precompiled" "$RUNS_WARM" "rm -rf target; $JAVAC_NT" "$JAVAC_NT"
  bench_warm "jenesis-daemon" "$RUNS_WARM" "rm -rf target; $DAEMON_NT" "$DAEMON_NT"
  [ -x "$NATIVE_BIN" ] && bench_warm "jenesis-native" "$RUNS_WARM" "rm -rf target; $NATIVE_NT" "$NATIVE_NT"
  echo "-- one-line edit to a test source (no tool's own code changes) --"
  if git diff --quiet -- "$EDIT_TEST" 2>/dev/null; then
    bench_warm "maven3"      "$RUNS_WARM" "git checkout -- $EDIT_TEST; rm -rf target; $m" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT_TEST; $m"
    bench_warm "jenesis-source"  "$RUNS_WARM" "git checkout -- $EDIT_TEST; rm -rf target; $SRC_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT_TEST; $SRC_NT"
    bench_warm "jenesis-precompiled" "$RUNS_WARM" "git checkout -- $EDIT_TEST; rm -rf target; $JAVAC_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT_TEST; $JAVAC_NT"
    bench_warm "jenesis-daemon" "$RUNS_WARM" "git checkout -- $EDIT_TEST; rm -rf target; $DAEMON_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT_TEST; $DAEMON_NT"
    [ -x "$NATIVE_BIN" ] && bench_warm "jenesis-native" "$RUNS_WARM" "git checkout -- $EDIT_TEST; rm -rf target; $NATIVE_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT_TEST; $NATIVE_NT"
    git checkout -- "$EDIT_TEST" 2>/dev/null
  else
    warn "skipping the test-edit table: $EDIT_TEST has uncommitted changes (commit or stash them to measure it)"
  fi
  echo "-- one-line edit to a main source (here also an engine source: jenesis-source recompiles the engine) --"
  if git diff --quiet -- "$EDIT" 2>/dev/null; then
    bench_warm "maven3"      "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $m" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $m"
    bench_warm "jenesis-source"  "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $SRC_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $SRC_NT"
    bench_warm "jenesis-precompiled" "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $JAVAC_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $JAVAC_NT"
    bench_warm "jenesis-daemon" "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $DAEMON_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $DAEMON_NT"
    [ -x "$NATIVE_BIN" ] && bench_warm "jenesis-native" "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $NATIVE_NT" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $NATIVE_NT"
    git checkout -- "$EDIT" 2>/dev/null
  else
    warn "skipping the edit table: $EDIT has uncommitted changes (commit or stash them to measure it)"
  fi
  echo "-- spurious touch (content identical) --"
  bench "maven3"      "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $m>/dev/null 2>&1; touch $EDIT" "$m"
  bench "jenesis-source"  "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $SRC_NT>/dev/null 2>&1; touch $EDIT" "$SRC_NT"
  bench "jenesis-precompiled" "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $JAVAC_NT>/dev/null 2>&1; touch $EDIT" "$JAVAC_NT"
  bench "jenesis-daemon" "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $DAEMON_NT>/dev/null 2>&1; touch $EDIT" "$DAEMON_NT"
  [ -x "$NATIVE_BIN" ] && bench "jenesis-native" "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $NATIVE_NT>/dev/null 2>&1; touch $EDIT" "$NATIVE_NT"
  stop_daemons
}

table_bootstrap() {
  note "Table: from an empty machine - acquiring the build tool counts, network required"
  local dist="$SCRATCH/maven" repo="$SCRATCH/m2" art="$SCRATCH/artifacts" base settings="" mvn
  base="apache-maven-$MAVEN_VERSION-bin.tar.gz"
  local fetch="curl -fsSL --retry 3 https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries/$base -o $dist/$base"
  fetch="$fetch || curl -fsSL --retry 3 https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/$base -o $dist/$base"
  # Jenesis honours MAVEN_REPOSITORY_URI, so where one is set (CI mirrors Central to
  # avoid rate limits) Maven has to be pointed at the same host or the two rows would
  # be timing two different CDNs rather than two build tools.
  mkdir -p "$SCRATCH"
  if [ -n "${MAVEN_REPOSITORY_URI:-}" ]; then
    printf '<settings><mirrors><mirror><id>benchmark</id><url>%s</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>\n' \
        "$MAVEN_REPOSITORY_URI" > "$SCRATCH/settings.xml"
    settings="-s $SCRATCH/settings.xml"
  fi
  echo "-- acquire the tool itself (a JDK is a prerequisite of both and is not counted) --"
  bench "maven $MAVEN_VERSION download+unpack" 1 "rm -rf $dist; mkdir -p $dist" "$fetch; tar -xzf $dist/$base -C $dist"
  bench "jenesis compile the engine"           1 "rm -rf $CLASSES $STAMP" "javac -nowarn -d $CLASSES \$(find -L $ENGINE -name '*.java')"
  printf '%-30s %s\n' "jenesis download" "none - the engine is $(find -L $ENGINE -name '*.java' | wc -l | tr -d ' ') vendored source files in the repository, 0 KB over the network"
  echo "-- first build on that machine, empty dependency cache (no -o; both resolve for real) --"
  mvn="$dist/apache-maven-$MAVEN_VERSION/bin/mvn"; [ -x "$mvn" ] || mvn="$MVN"
  bench "maven3"             1 "rm -rf target $repo" "$mvn $settings package -q -ntp -DskipTests -Dmaven.repo.local=$repo"
  bench "jenesis-source"     1 "rm -rf target $art $CLASSES $STAMP" "java $LAYOUT -Djenesis.project.artifacts=$art -Djenesis.test.skip=true $ENGINE/Make.java build"
  bench "jenesis-precompiled" 1 "rm -rf target $art" "java $LAYOUT -Djenesis.project.artifacts=$art -Djenesis.test.skip=true -cp $CLASSES build.jenesis.Make build"
  rm -rf "$SCRATCH"
}

table_full() {
  note "Table: full build, all tests (test-bound; ~25 KB symmetric metadata fetch, network required)"
  local m; m="$(M_F "$MVN")"
  bench      "jenesis cold"          1 "rm -rf target" "$SRC_F"
  bench      "maven3 cold"           1 "rm -rf target" "$m"
  bench_warm "maven3 warm no-op"     1 "rm -rf target; $m"     "$m"
  bench_warm "jenesis warm no-op"    2 "rm -rf target; $SRC_F" "$SRC_F"
}

table_maven() {
  note "Table: Maven 3 vs Maven 4 (both honour the pinned maven-compiler-plugin)"
  [ -n "$MVN4" ] || { warn "set MVN4=<maven-4 launcher> to run this table"; return 1; }
  local m3 m4; m3="$(M_NT "$MVN")"; m4="$(M_NT "$MVN4")"
  echo "-- cold --"
  bench "maven3" "$RUNS_COLD" "rm -rf target" "$m3"
  bench "maven4" "$RUNS_COLD" "rm -rf target" "$m4"
  echo "-- warm no-op --"
  bench_warm "maven3" "$RUNS_WARM" "rm -rf target; $m3" "$m3"
  bench_warm "maven4" "$RUNS_WARM" "rm -rf target; $m4" "$m4"
}

table_aot() {
  note "Table: Java AOT cache (JEP 514/515) for the compiled tool - JVM, not Graal"
  build_tool
  local jar="$ROOT/.jenesis/tool.jar" aot="$ROOT/.jenesis/build.aot"
  rm -f "$jar"; jar --create --file "$jar" -C "$TOOL" .
  local B="$LAYOUT -Djenesis.test.skip=true -cp $jar build.jenesis.Make build"
  local J="java $B" JA="java -XX:AOTCache=$aot $B"
  local H="java -cp $jar build.jenesis.Make help" HA="java -XX:AOTCache=$aot -cp $jar build.jenesis.Make help"
  note "recording run (-XX:AOTCacheOutput captures classes + method profiles)"
  rm -rf target "$aot"
  java -XX:AOTCacheOutput="$aot" $B >/dev/null 2>&1 || { warn "AOT cache training failed (needs JDK 25+)"; rm -f "$jar"; return 1; }
  echo "-- launch overhead (run 'help', no project work) --"
  bench_warm "compiled (jar)"       "$RUNS_WARM" "$H"  "$H"
  bench_warm "compiled (jar) + AOT" "$RUNS_WARM" "$HA" "$HA"
  echo "-- cold (empty target/) --"
  bench "compiled (jar)"       "$RUNS_COLD" "rm -rf target" "$J"
  bench "compiled (jar) + AOT" "$RUNS_COLD" "rm -rf target" "$JA"
  echo "-- warm no-op (nothing changed) --"
  bench_warm "compiled (jar)"       "$RUNS_WARM" "rm -rf target; $J"  "$J"
  bench_warm "compiled (jar) + AOT" "$RUNS_WARM" "rm -rf target; $JA" "$JA"
  echo "-- one-line edit to a main source --"
  if git diff --quiet -- "$EDIT" 2>/dev/null; then
    bench_warm "compiled (jar)"       "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $J"  "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $J"
    bench_warm "compiled (jar) + AOT" "$RUNS_WARM" "git checkout -- $EDIT; rm -rf target; $JA" "printf '\n//e%s\n' \"\$(date +%s%N)\">>$EDIT; $JA"
    git checkout -- "$EDIT" 2>/dev/null
  else
    warn "skipping the edit rows: $EDIT has uncommitted changes (commit or stash them to measure it)"
  fi
  echo "-- spurious touch (content identical) --"
  bench "compiled (jar)"       "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $J>/dev/null 2>&1; touch $EDIT"  "$J"
  bench "compiled (jar) + AOT" "$RUNS_WARM" "rm -rf target>/dev/null 2>&1; $JA>/dev/null 2>&1; touch $EDIT" "$JA"
  rm -f "$jar" "$aot"
}

table_pinning() {
  note "Table: dependency pinning - default (checksums verified) vs versions (checksums stripped)"
  build_tool
  local def="java $LAYOUT -Djenesis.test.skip=true -cp $TOOL build.jenesis.Make build"
  local ver="java $LAYOUT -Djenesis.dependency.pin=versions -Djenesis.test.skip=true -cp $TOOL build.jenesis.Make build"
  echo "-- cold (Dependencies step runs; default validates artifact digests) --"
  bench "default"      "$RUNS_COLD" "rm -rf target" "$def"
  bench "pin=versions" "$RUNS_COLD" "rm -rf target" "$ver"
  echo "-- warm no-op (Dependencies step cached; no validation either way) --"
  bench_warm "default"      "$RUNS_WARM" "rm -rf target; $def" "$def"
  bench_warm "pin=versions" "$RUNS_WARM" "rm -rf target; $ver" "$ver"
}

check_env
case "${1:-}" in
  launch)    table_launch ;;
  make)      table_make ;;
  compile)   table_compile ;;
  full)      table_full ;;
  maven)     table_maven ;;
  pinning)   table_pinning ;;
  aot)       table_aot ;;
  bootstrap) table_bootstrap ;;
  all)       table_launch; table_make; table_compile; table_full; table_maven; table_pinning; table_aot; table_bootstrap ;;
  *) echo "usage: $0 {launch|make|compile|full|maven|pinning|aot|bootstrap|all}"; exit 1 ;;
esac
note "done: ${1:-}"
