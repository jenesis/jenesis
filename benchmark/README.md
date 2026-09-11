Build-performance benchmarks
============================

`benchmark.sh` measures this project's own build performance. It compares Maven against the ways the Jenesis
build is launched (source, `javac`-precompiled, daemon-backed and `native-image`) across tool acquisition,
launch overhead, compile-and-package, full builds, Maven 3 vs Maven 4, and the dependency-pinning modes.

Where the figures come from
---------------------------

From the `Build performance benchmarks` GitHub Action, not from a laptop. Trigger it from the Actions tab,
optionally choosing which tables and how many repetitions: it runs the tables unattended on Linux, macOS and
Windows, prints them into the run summary and uploads a `benchmark-<os>.txt` per runner. Anyone can re-trigger
it and get the same tables on the same hardware class, which is what makes a number worth quoting. Runner
figures are noisier than a controlled machine (shared, virtualized cores), so read them as relative
comparisons across a run rather than as absolute timings.

Run it locally for A/B work - when you want to know whether a change you are holding made the build faster -
and then confirm on the workflow before the figure leaves this repository.

Running
-------

    benchmark/benchmark.sh compile     # one table
    benchmark/benchmark.sh all         # every table

| Subcommand  | What it isolates                                                                          |
|-------------|-------------------------------------------------------------------------------------------|
| `launch`    | Launch overhead alone: `help` with no project work, once per launch strategy               |
| `make`      | The engine itself: `jenesis.make.compile`, `jenesis.make.classes`, `jenesis.make.daemon`   |
| `compile`   | Maven vs Jenesis, compile and package, tests compiled but not run                          |
| `full`      | Maven vs Jenesis with the whole test suite executed                                        |
| `maven`     | Maven 3 vs Maven 4 on the same `pom.xml`                                                   |
| `pinning`   | `jenesis.dependency.pin` default (checksums verified) vs `versions` (checksums stripped)   |
| `aot`       | JDK 25's command-line AOT cache (JEP 514/515) for the compiled tool, captured by a recording run. This is the JVM cache, *not* Graal `native-image`; it mirrors the `launch` and `compile` scenarios and needs JDK 25+ |
| `bootstrap` | What it costs to get from an empty machine to a built artifact, acquiring the tool included |

Configuration (environment variables, all optional):

| Variable        | Default        | Purpose                                                             |
|-----------------|----------------|---------------------------------------------------------------------|
| `JAVA_HOME`     | java on `PATH` | JDK 25+ for the Jenesis and Maven builds                            |
| `MVN`           | `mvn`          | Maven 3 launcher                                                    |
| `MVN4`          | unset          | Maven 4 launcher; the `maven` table is skipped without it           |
| `MAVEN_VERSION` | `3.9.9`        | The distribution the `bootstrap` table downloads and times          |
| `GRAALVM_HOME`  | unset          | GraalVM 25+; the native launcher is skipped without it              |
| `RUNS_COLD`     | `5`            | repetitions for cold builds                                         |
| `RUNS_WARM`     | `3`            | repetitions for warm and incremental builds                         |

The script prepares what it needs: it precompiles the engine into `.jenesis/tool` for the precompiled rows,
and (when `GRAALVM_HOME` is set) captures reachability metadata and builds a native launcher once. Daemons it
starts are stopped when it exits, including on an interrupt.

It is `bash` (not POSIX `sh`) and runs on Linux, macOS and Windows (Git Bash). Where GNU `time` is absent
(macOS, Windows) it falls back to the shell's `time` keyword for wall-clock, and where `/proc/net/dev` is
absent it omits the `net<=NKB` column rather than asserting zero bytes.

Methodology
-----------

These controls are what make the comparison fair; they are the conclusions of a long measurement exercise.

- **External timing.** Wall-clock comes from `/usr/bin/time` (its `%e` field), never a build tool's own
  "BUILD SUCCESS in N s" line. The cold figure is the median of `RUNS_COLD`, the rest of `RUNS_WARM`.
- **CPU power profile.** Run on a `performance` profile. A laptop's default `power-saver` pins the cores low
  (here ~1.4 GHz vs ~3.6 GHz) and inflates every figure ~2.6x; the script warns if the profile is not
  `performance`. Keep the machine on AC and otherwise idle.
- **Warm caches, and network proven absent.** Except in `bootstrap`, both tools run with warm dependency
  caches (`~/.m2`, `.jenesis/artifacts`). Each run records the host network byte delta and prints `net<=NKB`;
  with warm caches the compile, incremental and launch builds transfer **0 KB**. The exceptions are the full
  build with tests, which makes a ~25 KB `maven-metadata.xml` lookup for the `RELEASE`-versioned external test
  tools - the same on both tools - and `bootstrap`, which is about the bytes.
- **Like-for-like work.** Maven runs with `-DskipTests`, which compiles the test sources but skips executing
  them; Jenesis's `-Djenesis.test.skip` likewise compiles the test module and skips only the runner. Both
  therefore compile the same 166 main + 158 test sources. (Maven's `-Dmaven.test.skip=true` would skip
  compiling the tests entirely and is *not* comparable.) The full table runs the real suite on both: 1577
  `@Test` methods plus 52 `@ParameterizedTest` methods, which expand into more.
- **Acquiring the tool is part of the cost.** A build tool you have to fetch before you can build is not free,
  so `bootstrap` times it: Maven's distribution download and unpack against compiling the vendored engine, and
  then each tool's first build into an empty dependency cache (`-Dmaven.repo.local`, `jenesis.project.artifacts`
  pointed at throwaway folders, no `-o`). The Maven it downloads is the Maven it then builds with, and where
  `MAVEN_REPOSITORY_URI` mirrors Central - as CI does, to stay under the rate limits - a generated
  `settings.xml` points Maven at the same host, so the row times two build tools rather than two CDNs. It is
  the one table where the network is the subject rather than a contaminant, so its rows run once and the
  `net<=NKB` column carries as much of the answer as the seconds do.

The engine is the project here
------------------------------

Jenesis compiles its own engine once and caches it: `jenesis.make.compile` (default `true`) batch-compiles
`build/jenesis/*.java` into `jenesis.make.classes` (default `.jenesis/classes`) and stamps it with a digest of
those sources. A later run that matches the stamp skips the compile; a run that does not pays a full engine
compile of roughly 400 class files before any project work begins.

In this repository the project under test *is* the engine, so editing any main source invalidates that stamp.
A source-launched incremental build here therefore recompiles the engine as well as the project, while Maven,
the precompiled launcher and the native launcher do not - which is why `compile` measures the incremental
loop twice: once on a **test** source, where no tool code changes and the four launchers are strictly
comparable, and once on a **main** source, which is the honest self-build figure and is labelled as recompiling
the engine. A project that only vendors the engine and edits its own sources sees the first of those two.

`jenesis.make.daemon` (default `false`) keeps a JVM alive between builds, so the engine's classes stay loaded
and JIT-compiled. Its fingerprint covers the engine and the non-`jenesis.*` JVM flags, so each distinct flag
set gets a daemon of its own; the script stops the ones it starts. Source mode cannot benefit much from it:
the JDK source launcher compiles `Make.java` and what it pulls in on every single invocation, before any
daemon is reached, and that floor is the `source` row of the `launch` table.

Notes on the figures
---------------------

- The `native` (Graal) launcher this script builds carries `jdk.compiler`/`jdk.jartool` (`--add-modules`) and forces
  javac's and jar's message bundles in (`-H:IncludeResourceBundles`, without which the in-process compiler fails
  non-deterministically on an un-recorded bundle), so it runs `javac` in-process; with the ahead-of-time-compiled
  compiler - no JVM startup, no JIT warm-up - it is the fastest configuration measured here, cold and warm. A bare
  Graal native image without those modules has no in-process JDK tools and forks an external `javac`, making it the
  slowest on a cold build instead (the `jenesis.process.factory=tool|fork` property forces either path). This is the
  Graal `native-image` launcher; the `aot` table measures the separate *Java AOT* JVM cache.
- `pin=versions` (checksums stripped, versions kept) shows no measurable speedup: the warm hot path is the
  incremental cache's MD5 hashing, and the SHA-256 artifact validation it removes runs only on a cold
  `Dependencies` step and is negligible.
- Maven 4.0.0-rc-6 (no GA at the time of writing) needs the `maven-compiler-plugin` pin the project's `pom.xml`
  already carries, its own default being too old to read Java 25 bytecode; with it, Maven 3 and 4 are equivalent
  bar Maven 4's startup overhead.
- A from-scratch machine pays one-time dependency and external-tool downloads that every table but `bootstrap`
  excludes. In particular, the first test-running build of a session populates the external-tool cache, so when
  comparing cold full builds make sure that cache is warm on both sides first.
