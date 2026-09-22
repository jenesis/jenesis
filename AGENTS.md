# AGENTS.md

Jenesis, the build tool, and jpx, the module runner, in one repository. The tool builds itself: `build/jenesis`
links to `sources/build/jenesis`, so `java build/jenesis/Make.java` compiles, tests and packages this
project with the very sources it is working on. `README.md` covers the layout, the demos, CI and releasing;
this file is how the code is written and changed. The user documentation is
[jenesis.build](https://jenesis.build) ([jenesis/jenesis-documentation](https://github.com/jenesis/jenesis-documentation)),
and `java build/jenesis/Make.java skill` prints an onboarding briefing from the same material.

## Build & test

- **JDK 25 or newer**, nothing else. `java build/jenesis/Make.java` builds and tests everything;
  `stage` lays out the release tree; `pin` rewrites the pins; `help` lists every selector and `-D` flag.
- One test class: `java -Djenesis.test.filter='.*BuildExecutorTest' -Djenesis.test.force=true
  -Djenesis.print.tests=true build/jenesis/Make.java +tests`. The filter is a regex over class names,
  `force=true` runs tests whose inputs did not change, `print.tests` streams the JUnit output. Classes named
  `*RunTest` run the real external tool and need the network.
- CI builds under `-Djenesis.dependency.pin=strict`: after changing a dependency, run `pin` and commit the
  rewritten `module-info.java` / `pom.xml` lines, or CI fails on the unpinned coordinate.
- When the tool cannot build itself, `mvn test` (the root `pom.xml`) compiles and tests the sources without
  Jenesis. It validates only; it stages and pins nothing.
- Every feature ships a demo under `demo/` with a README and a verification command in
  `.github/workflows/demos.yml`, which `build.yml` calls once per operating system; the demos are the
  end-to-end suite and the documentation's examples.

## How the code is written

**No comments, no Javadoc.** The sources carry neither, and a change adds none - not inline, not on a type,
not on a method, in `sources/` or `tests/`. A name, a type or a smaller method carries the meaning; if a
construct seems to need a comment, restructure it or name it better. The one place Javadoc appears is
`module-info.java`, because the `@jenesis.*` tags there (`@jenesis.main`, `@jenesis.test`, `@jenesis.pin`,
…) are configuration the tool reads, not commentary.

**One caller, no method.** A private method with one caller is inlined at it. It survives only where
inlining would hurt: a self-contained algorithm that has a name, a body with early returns that the caller
would have to be restructured around, one holding an anonymous class, or a pattern switch over a sealed type.
A static whose first argument is one of our own types belongs on that type as an instance method instead -
without inventing a type to make the call virtual.

**Static fields are one block.** No blank line separates them, and constants that belong together share one
declaration, wrapped onto continuation lines where they are long
(`private static final String MAVEN_GROUP = "org.antlr", MAVEN_ARTIFACT = "antlr4";`). A separate declaration
is what marks a separate concept, so a `Set` or `Pattern` initializer keeps its own line, where a declarator
comma would read as one of its own.

**Zero dependencies.** The tool ships as source, vendored into every project that uses it, and runs with a
JDK and nothing else - that is the promise, and it is not negotiable. `build.jenesis` `requires` only
`jdk.compiler` and `java.xml`; there is no third-party library anywhere in `sources/`, and none is added for
convenience. What the JDK does not provide is written against what it does: JSON is `Json.java` over
`java.base`, HTTP is `HttpURLConnection`, XML is `java.xml`. A tool the build needs at run time - a compiler, a
linter, a packager - is resolved as a dependency in its own group and forked or loaded in a module layer,
never added to the module's own `requires`.

**Java 25 idiom.** Every file starts with `import module java.base;` (plus `jdk.compiler` or `java.xml` where
used). Records, sealed types, pattern switches and unnamed variables (`_`) are the normal idiom.

**Immutable records with withers.** Configuration objects are records or final classes whose state never
changes after construction. Each exposes one method per component, named exactly like the component, that
returns a new instance with that value replaced (`new Project(Path.of(".")).version("1.0.0").sources(true)`,
`new BuildExecutor.Configuration().concurrency(4)`). No setters, no builders, no `with` prefix.

**System properties are the defaults.** Every setting is a `jenesis.<area>.<name>` system property, read
once where the object is built:

- `ofKeys(keys, …)` is the static factory that reads every setting from the provider it is given
  (`SequencedProperties.value(keys, "executor.digest", "MD5")`,
  `SequencedProperties.number(keys, "executor.concurrency", 0)`,
  `SequencedProperties.flag(keys, "source.pmd", true)`), with a value the object cannot do without
  following the provider as a further argument (`Project.ofKeys(keys, root)`);
- the public no-argument or short constructor reads nothing and takes each setting's default, exactly as
  if the provider answered for none of them - that is what `SequencedProperties.NONE` is;
- a canonical constructor takes every value explicitly;
- the wither overrides one value and calls the canonical constructor.

An object that hands settings to children it builds resolves them where it is built: its `ofKeys` calls each
child's own `ofKeys` and keeps the result, so no module, step, repository or resolver carries a provider and
no public signature mentions one. The exceptions are the two types an entry point hands a command line to -
`Project` and the assembler it gives a multi-project build - because they build a module per project module a
scan discovers, long after the settings were read. Every setting a child resolved is a wither of its own as
well, so a caller that names no strings configures the same object programmatically.

**Where the run talks is an argument too.** Nothing in the engine writes to `System.out` or `System.err`:
a line goes to a `Consumer<String>` that the run was given, and `Output` is the pair of them the entry point
hands down beside the provider. A factory that resolves a setting into a printing consumer - `Terms.ofKeys`,
`Dependencies.ofKeys`, `Signatures.ofKeys`, `Tree.ofKeys`, `BuildExecutor.Configuration`, the repositories -
therefore takes the `Output` right after `keys`, and the consumer it builds is still a wither of its own, so a
caller can hand one tool's lines somewhere else. `new Output()` is the pair that writes to the JVM's streams,
`new Output(out, err)` the pair that writes to a tool's writers, and `Make`, `Toolchain` and the daemon are the
exception, because they are the process boundary rather than the build.

**A command line can live in a file.** `Make`, `Execute`, `Jpx` and the three tools read `@<file>` as the
arguments it holds and `@@<text>` as an argument starting with an `@`, with `#` to the end of a line a comment
and both quote kinds holding what would otherwise split, exactly as the JDK's own tools read an argument file -
including that a file is not expanded again from within one. A setting may lead the arguments as
`-Djenesis.<key>=<value>` there, so a file carries a whole run.

**What the launcher and the engine must agree on lives in `Make`.** `Make` is the one type that may reference
nothing else of this project, so a rule both of them read - how a flag parses, what an `@<file>` argument
expands to - is written there, package-private, and the engine's own accessors call it: `flagOrNull` and
`arguments` on `SequencedProperties` are the names the rest of the code knows it by. The dependency points at
the launcher rather than away from it, which is what keeps a setting from meaning one thing before the build
starts and another inside it.

A setting that decides which process a build runs in cannot be honoured by the `jenesis-make`, `jenesis-exec`
and `jpx` tools, which run inside another program's JVM: `jenesis.toolchain.version`, `jenesis.project.docker`
and `jenesis.execute.docker` are refused by name there rather than ignored. Nothing else about a tool run
differs, because a run is configured by the provider and the output it is handed, so two of them in one JVM
never collide and none of them touches the JVM's own properties or streams.

A setting is therefore never read again later, and a caller that builds the object itself is never
surprised by the environment: `new Project(root)` is the defaults and nothing else, and only
`Project.ofKeys(SequencedProperties.SYSTEM, root)` - which the entry point calls, and nobody else - reads
the JVM's properties. Every setting is read through a `Function<String, String>` that answers one key,
never off `System` directly: `SequencedProperties.SYSTEM` is the provider that reads the JVM's properties,
and it is the one place the shared `jenesis.` prefix is spelt, so a key is named without it everywhere
else. `Make` and `Toolchain` are the exception and read the provider without the shared accessors, because
`MakeClosureTest` holds each of them to compiling alone - reaching for `SequencedProperties` there would drag
its closure into every build's first step. Where the rule itself must not differ, `Make` holds it and the
accessors call `Make`, rather than either side keeping a copy.
The accessors are `getProperty(keys, key)` and `getProperty(keys, key, default)` for the raw value, `value`
for the trimmed one that reads a blank as absent, `flag`, `flagOrNull`, `number`, `entries` and `words` -
and nothing else, so no reader hand-rolls a parse. A boolean is the setting absent being the default,
`=true` or the setting named with no value at all being true, `=false` being false, and any other value an
`IllegalArgumentException` naming what would be valid; `flagOrNull` answers `null` for the absent setting,
for one whose third state is the absence itself. A number refuses a value that is not one the same way.
`Boolean.getBoolean` and `Integer.getInteger` are not used, because they read `=false` and a bare `-Dkey`
alike as false and a misspelt value as false or as the default rather than as the mistake it is.
Environment variables are fallbacks for the repository settings only (`MAVEN_REPOSITORY_URI`,
`JENESIS_REPOSITORY_TOKEN`, …). `jenesis.properties` at the project root and the profile files are read by
`Make.settings`, which layers them under whatever the entry point already holds and hands
the result down as one provider; nothing is ever copied into the JVM's own properties, so
two builds in one JVM never see each other's settings. A new property is added in three places - the constructor that reads it, the catalogue behind the
`configuration` selector in `Project.java`, and the reference table in the user documentation. That catalogue
is the tool's own property reference: one line per property, `<key>|<default>|<description>`, printed with the
value in force, so **adding, renaming or removing a property means editing it in the same commit** - a
`jenesis.*` the code reads and the catalogue does not list is a bug, and so is a line whose default has drifted
from the constructor that reads it. `help` and `skill` point at `configuration` rather than listing properties,
so neither grows for a new property; `help` grows for a new selector.

**`jenesis.make.*` and `jenesis.project.*` split by who reads them.** `Make` and the main methods it launches
read `jenesis.make.*`: where the project is (`root`), which profiles to layer (`profiles`), where the
user-global file lives (`global`), and how the engine is compiled and reused (`compile`, `classes`, `daemon`).
All of it has to be read before a `Project` can exist, which is why it belongs to the entry point rather than
to the project. Everything the `Project` record reads for itself is `jenesis.project.*`. `Project` therefore
takes its root as a required constructor argument and its profiles as a value handed in by the entry point -
neither is a property it reads, and no `jenesis.project.*` key is read outside it.

**`build.jenesis` is a `ToolProvider`.** `MakeTool`, `ExecuteTool` and `JpxTool` publish
`jenesis-make`, `jenesis-exec` and `jpx` through `java.util.spi.ToolProvider`, under the names the
commands already answer to, so a program with the module resolved builds a project, runs what it
built, or runs a published program in its own JVM. They share `JenesisTool`, which takes the
leading `-Djenesis.*` arguments as that run's provider, hands the rest to the tool as a command
line would, and writes everything printed to the writers it was given.
`java.util.spi.ToolProvider` is in `java.base`, so this costs no dependency. The three are declared
twice, by `provides` in `module-info.java` and by `META-INF/services/java.util.spi.ToolProvider`,
because a named module reads the first and a jar on the class path reads the second, and the tools
answer to their names either way. A setting that would
replace the running process - `jenesis.toolchain.version`, `jenesis.project.docker`, and
`jenesis.execute.docker` for what `jenesis-exec` runs - is refused by name rather than ignored,
and `run` reports a failure through `err` and a code rather than throwing. `jenesis-exec` forks
the program it runs, as its command does, so only the build's own output reaches the writers.
Source mode registers no service, so a program there builds `new MakeTool()` itself; the contract
is the same.

**`jenesis.toolchain.*` picks the JVM, and only the user says where to look.** `Toolchain` reads
`jenesis.toolchain.version` and `jenesis.toolchain.searchpath`. `Make.main` and `Execute.main` reach it by
reflection, and only when a version is set, so source mode compiles it only then; it depends on `java.base`
alone, which `MakeClosureTest` holds it to. A JDK is identified by its `release` file and never executed
before it is chosen. The search path decides what the build executes, so `Make.settings` refuses it in
every file a project provides: a new way to read properties keeps that rule, and the relaunch
never takes a JVM option that configuration could supply.

**Configuration files are read through `SequencedProperties`.** A file is read with the type's own accessors -
`value`, `value(key, default)`, `flag`, `flag(key, default)`, `flagOrNull`, `entries` for a comma-separated
list, `words` for a whitespace-separated command line - which trim and treat a blank value as an absent one,
so no reader hand-rolls that again. A flag is the one exception and reads exactly as a `jenesis.*` setting
does: a key named with no value at all is true rather than absent, and a value that is neither `true` nor
`false` is refused, so one word never means one thing on a command line and another in a file. `getProperty` stays the raw `Properties` contract for the few readers that must tell
an empty value from a missing one (an alias line whose emptiness removes an entry, a coordinate whose empty
location marks it unresolved). A convention of the file format belongs on the type; parsing that belongs to
one file's schema - a `<name>=<coordinate>` plugin list, a key whitelist - stays a private static in that
reader.

**Steps are pure functions of folders.** A `BuildStep` reads its `arguments` (one folder per predecessor) and
writes into `context.next()`, nothing else. It is `Serializable` and its serialised form is part of the cache
key, so every value that should trigger a re-run is a non-`transient` field and every field is serialisable
(`Path` is hashed by its string form; a lambda field must be typed as a serialisable functional interface).
What a step holds is serialised with it - a `Resolver`, a version negotiator, a lambda typed as a
serialisable functional interface - so those types are `Serializable` too, and their scaffolding (a parser
factory, a lookup cache) is `transient` rather than left to drift into the key. A module is not serialisable
and never reaches a key at all, so a field of one is never `transient`.
Steps compose by folder conventions - `sources/`, `classes/`, `artifacts/` - never by inspecting predecessor
names. A step that forks a JDK tool extends `ProcessBuildStep` and thereby accepts `process-<tool>.properties`.

**Modules activate on a file.** A build module under `project/` (`CheckstyleModule`, `JaCoCoModule`, …)
switches itself on when its configuration file is present in a configuration folder
(`configurationFile(configuration)`), resolves its tool in a dependency group named after the tool, and is
opted out with its `jenesis.<kind>.<tool>=false` property. The inferred assembler wires the modules; a new
tool is a new module in the same shape, plus a demo.

**A tool reads one folder, the inference fills it.** A generator module (`XjcModule`,
`ProtocModule`, …) declares the folder it reads - `xjc/`, `protoc/` - and reads nothing else:
it never searches `sources/` or `resources/`, and it never learns where a contract lives. The
inferred module binds the configured `folders` (default `META-INF/build.jenesis`) into that
folder, filtered to the file kinds the tool compiles and keeping each file's path below the
folder it came from, so moving a contract elsewhere changes no step's inputs. An input the
tool identifies by name rather than by kind - a catalog, an OpenAPI document - is linked
under the customary name the tool expects, so renaming the file in the project does not
re-run the step either.

**A module configures only its own children.** Every `Inferred*Module` holds one
`Function<Child, BuildExecutorModule>` per module it wires, named exactly like the child it configures and
defaulting to the identity, or to `null` when that child's `jenesis.*` property switches it off; `null`
skips the child, and so does a configurator that returns `null`. That property is read in `ofKeys` and
nowhere else, so the plain constructor wires every child whatever the environment says, and a module that
wires another module builds that child with its own `ofKeys` and keeps the result beside the configurator
that shapes it, named for the child it holds (`checkstyleModule`, `javacStep`) - which is how one provider
handed to `Project` reaches the whole tree without being carried into it. A caller reaches
further down by nesting -
`assembler.toolchain(toolchain -> toolchain.compiler(compiler -> compiler.javac(javac -> …)))` - and no
module ever exposes a configurator for a module it does not wire itself, so a new child is a new
configurator on its own parent, never a new component on the assembler.

**Fail loudly, name the fix.** Bad input is an `IllegalArgumentException` whose message says what was given
and what would be valid; a missing prerequisite is an `IllegalStateException` that names it. Nothing
silently falls back, and a lenient wildcard selector is the one deliberate exception, documented as such.

## Tests

- `tests/` is the `@jenesis.test` module of `build.jenesis`, on JUnit Jupiter with AssertJ. A test method is
  a sentence in `snake_case` stating the behaviour it proves (`replaces_a_stale_staging_folder_from_a_crashed_run`);
  the assertion carries the reason as its `.as(...)` description where one is needed.
- A test that builds steps implements `Serializable`, so the lambdas it hands the executor can be hashed;
  state a step must not capture (a latch, a socket) lives in a static field.
- A test names the settings it needs in a `Map.of(…)::get` provider and collects what a run prints from an
  `Output` of its own. Setting a system property or swapping `System.out` is what the provider and the output
  exist to avoid, and it survives only where the test is about the JVM's own properties (`SequencedPropertiesTest`)
  or proves that a build ignores them.
- `@TempDir` folders for every build; `BuildExecutorCallback.nop()` and `BuildExecutorCache.nop()` unless the
  test is about them; `Runnable::run` as the executor when ordering matters.
- Behaviour that is user-visible gets a demo as well as a test.

## Public surface and the documentation

`build.jenesis` is published to Maven Central and vendored as source into every project that uses the tool, so
every public type is API: renaming a method, a property or a selector is a breaking change and is done
deliberately, with the demos, the `help`/`skill` text and the user documentation updated in the same pass.
The documentation repository's `AGENTS.md` describes how a change is verified and where each setting is
documented; a property that exists only in the code and not on jenesis.build is not finished.

A demo's README is a user guide: what to declare, what to run and what comes out, never how the engine
arrives there. The demos are read in order, so a page refers to the others by name and only backwards, and
the launchers under `build/` carry no comments either. A demo that teaches what another one already shows is
folded into it.

## Releasing and downstream

A release is a manual run of the release workflow from the Actions tab, so any commit is releasable: its
optional `sha` input names the commit (default: the head it runs on) and its optional `tag` input names the
tag (default: the next minor of the highest `vX.Y.Z` tag). The workflow stages, signs, publishes to Maven
Central, SDKMAN, Homebrew and Scoop, and cuts the `vX.Y.Z` tag. `jenesis-launcher`, `jenesis-modules` and
`jenesis-repository` pin this repository as the `build/.upstream` git submodule; after a release they are
moved to the release commit.
