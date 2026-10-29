# AGENTS.md

Jenesis, the build tool, and jpx, the module runner, in one repository. The tool builds itself: `build/jenesis`
links to `sources/build/jenesis`, so `java build/jenesis/Make.java` compiles, tests and packages this project with
the sources it is working on. `README.md` covers the layout, the demos, CI and releasing; this file is how the code
is written and changed. The user documentation is [jenesis.build](https://jenesis.build)
([jenesis/jenesis-documentation](https://github.com/jenesis/jenesis-documentation)); `skill/start` prints an
onboarding briefing from the same material, `skill/<page>` one page and `skill` all of them.

## Build & test

- **JDK 25 or newer**, nothing else. `java build/jenesis/Make.java` builds and tests everything; `stage` lays out
  the release tree, `pin` rewrites the pins, `help` lists every selector and `-D` flag.
- One test class: `java -Djenesis.test.filter='.*BuildExecutorTest' -Djenesis.test.force=true
  -Djenesis.print.tests=true build/jenesis/Make.java +tests` - a regex over class names, run even when nothing
  changed, with the JUnit output streamed. Classes named `*RunTest` run the real tool and need the network.
- CI builds under `-Djenesis.dependency.pin=strict`: after changing a dependency, run `pin` and commit the rewritten
  `module-info.java` / `pom.xml` lines.
- When the tool cannot build itself, `mvn test` (the root `pom.xml`) compiles and tests the sources; it stages and
  pins nothing.
- Every feature ships a demo under `demo/` with a README and a verification command in `.github/workflows/demos.yml`,
  which `build.yml` runs once per operating system. The demos are the end-to-end suite and the documentation's
  examples.

## How the code is written

- **No comments, no Javadoc**, in `sources/` and `tests/`. A name, a type or a smaller method carries the meaning.
  The `@jenesis.*` tags in `module-info.java` are configuration, not commentary, and the one exception.
- **One caller, no method.** A private method with one caller is inlined, unless it is a self-contained algorithm
  with a name, has early returns the caller would be restructured around, holds an anonymous class, or is a pattern
  switch over a sealed type. A static whose first argument is one of our own types is an instance method of that
  type - without inventing a type to make the call virtual.
- **Static fields are one block**, without blank lines. Constants that belong together share one declaration
  (`private static final String MAVEN_GROUP = "org.antlr", MAVEN_ARTIFACT = "antlr4";`); a `Set` or `Pattern`
  initializer keeps a declaration of its own.
- **What wraps, wraps one per line.** A declaration with several variables and an argument or parameter list that
  does not fit on its line put each variable or argument on a line of its own; `Map.of` keeps a key and its value
  together.
- **Zero dependencies.** The tool ships as source, vendored into every project that uses it: `build.jenesis`
  requires only `jdk.compiler` and `java.xml`, and nothing third-party is added for convenience. JSON is `Json.java`,
  HTTP is `HttpURLConnection`, XML is `java.xml`. A tool the build runs - a compiler, a linter, a packager - is
  resolved in a group of its own and forked or loaded in a module layer, never required.
- **Java 25.** Every file starts with `import module java.base;`. Records, sealed types, pattern switches and `_`
  are the normal idiom.
- **Immutable objects with withers.** Configuration is a record or a final class; each value has a method named
  exactly like it that returns a copy with that value replaced (`new Project(root).version("1.0.0")`). No setters,
  no builders, no `with` prefix. A record takes what it is handed: no compact constructor copies a collection or
  checks a value unless a rule of the type depends on it.
- **Fail loudly, name the fix.** Bad input is an `IllegalArgumentException` naming what was given and what would be
  valid; a missing prerequisite is an `IllegalStateException` naming it. Nothing silently falls back; a lenient
  wildcard selector is the one documented exception.

## Settings

**Every setting is a `jenesis.<group>.<key>` property** - three segments, never two, never a nested group - read once,
where the object is built:

- `ofEnvironment(environment, …)` reads every setting from the `Environment` it is handed
  (`environment.value("executor.digest", "MD5")`), with what the object cannot do without as further arguments
  (`Project.ofEnvironment(environment, root)`);
- the public short constructor reads nothing and takes every default, as `Environment.NONE` would;
- the canonical constructor takes every value; a wither replaces one and calls it.

An object that builds children calls their `ofEnvironment` and keeps the result, so no module, step, repository or
resolver carries an environment, and every setting a child resolved is a wither of its own.

**`Environment` is what a run is given**: the immutable map of settings in force, a `Consumer<String>` each for
output and errors, and the project root a relative path resolves against, which `Project.ofEnvironment` sets with
`root(root)`. Nothing in the engine writes to `System.out` or `System.err`. Colour is a `Palette` resolved from
`jenesis.palette.colors` where the printing object is built, `transient` in a step; it names roles (`status`,
`failure`, …), and no escape sequence is written outside it. An object built without an environment prints nothing
until a wither hands it a consumer. `Environment.NONE` holds no setting and writes to the JVM's streams;
`new Environment(keys)` and `new Environment(keys, out, err)` add settings and writers, each a wither too
(`Environment.NONE.out(printed::add)`). No environment reads the JVM's properties as they stand, as code running in a
build could change one between two reads.

The environment is an argument of construction, never a field: what keeps talking keeps the consumer it resolved. Only
`Project` and the multi-project assembler hold one, as they build a module per scanned project module long after the
settings were read, and `ExternalModule` and `InternalModule`, which hand it to a plugin they create once its closure
is resolved - so neither has a public constructor. `Make`, `Toolchain` and the daemon are the process boundary and may
use the streams.

**`Make` reads the JVM once.** It copies every `jenesis.*` property when an entry point starts, lays the command
line's `-Djenesis.*` arguments over it and hands the map down; a program of its own reads the same through
`Make.settings(root).keys()`. `Make.keys` is the one place the `jenesis.` prefix is spelt. A setting whose keys cannot
be named in advance (`jenesis.platform.<token>`, `jenesis.plugin.<name>`) is found by listing the map. `Make` and
`Toolchain` read a bare `Map<String, String>`, because `MakeClosureTest` holds each to compiling alone; `Project.perform`
and `Project.run` take that map, so the reflective handoff names JDK types alone. A rule both sides need - how a flag
parses, what `@<file>` expands to - lives in `Make`, package-private, and the engine calls it (`flagOrNull` and
`arguments` on `SequencedProperties`).

**Reading a value.** The accessors are `getProperty` for the raw value, `value` for the trimmed one that reads blank as
absent, `flag`, `flagOrNull`, `number`, `entries`, `words`, `path` for a file or folder, resolved against the root,
and `variable` for a file value naming
`jenesis.variable.<key>` as `@<key>`. A boolean is the default when absent, true when named with no value or
`=true`, false at `=false`, and any other value is refused naming the valid ones; `flagOrNull` answers `null` when
absence is a third state. `Boolean.getBoolean` and `Integer.getInteger` are not used. Environment variables are
fallbacks for the repository settings (`MAVEN_REPOSITORY_URI`, `JENESIS_REPOSITORY_TOKEN`, …) and the toolchain
installer alone.

**Files and command lines.** `Make.settings` layers `jenesis.properties` at the project root and the profile files
under what the entry point holds and hands the result down as one map; nothing is copied into the JVM, so two builds
in one JVM never see each other's settings. `Make`, `Execute`, `Jpx` and the three tools read `@<file>` as the
arguments it holds, as the JDK's tools do (`@@` escapes, `#` comments, quotes, no nested expansion), and a file may
lead with `-Djenesis.<key>=<value>` lines. A process the build hands a command line to - a container, the daemon - is
given the settings rather than left to read its JVM: the daemon receives them with every request and keys its
fingerprint on the engine, the JVM, its options and the environment, so that one daemon serves every project that
matches, and on the project root as well under `jenesis.daemon.shared=false`; a container receives every setting save
the `jenesis.project.docker` keys that start it.

**A relative path is relative to where it is written.** On a command line it names a folder below the one the command
runs in: `Make.ambient` and `JenesisTool` resolve every setting naming a path (`Make.PATHS`, the comma-separated
`Make.LOCATIONS`, the files of `project.resources` and of the jarsigner passwords) through `Make.located` before
anything else reads them, so a daemon, whose own folder is its working directory, is handed absolute paths. In
`~/.jenesis/jenesis.properties` and its profiles, a path naming what the machine shares between projects
(`Make.MACHINE`: the local repositories, `daemon.folder`, the signing key and its passwords) is relative to
`~/.jenesis`, resolved by `Make.settings` as it reads the file. `Make` alone knows where a value was written, so it
resolves these two before the layers merge. Everything else, a project's own files and a map a program hands `Make`
or `Project` included, is relative to the project root: a reader takes a path through `environment.path(key)`, never
`Path.of(environment.value(key))`, and an absolute path is used as it is. A new setting naming a path is read through
`path` and joins `Make.PATHS` or `Make.LOCATIONS` in the same commit. A step resolves nothing against the working
directory: it reads the folders it is handed and writes `context.next()`.

**A project's own files are untrusted.** `Make.settings` refuses, in a project's `jenesis.properties` and profiles,
every key naming a program the build runs or a JVM's options, a credential, a cache, folder or trust root shared
between projects, the signing key, or a container's boundary, and keeps the folders it names for writing below the
project root. `~/.jenesis/jenesis.properties` and its profiles outrank the project, the command line outranks both. A
new property of those kinds joins those lists in the same commit.

**Who reads what.** `jenesis.make.*` is read by `Make` and the main methods it launches, before a `Project` exists:
`root`, `profiles`, `global`, `mode` (`source`, `compiled`, `aot` or `daemon`) and `classes`. Unset, the mode is
`daemon`, or `compiled` where `jenesis.process.mode` names `fork` or `aot`, `jenesis.project.docker` is set or
`jenesis.release.publish` publishes; `Make` hands the mode it resolved down as `make.mode`, which the executor prints
in the `[STARTED]` line and the `started` event and nothing else reads. Everything `Project`
reads is `jenesis.project.*`, read nowhere else; its root is a constructor argument and its profiles are handed in.
`jenesis.toolchain.*` picks the JVM: `Toolchain` reads `version`, `searchpath` and `installer`, is reached by
reflection only when a version is set, depends on `java.base` alone, and identifies a JDK by its `release` file
without running it. The search path and the installer decide what runs, so no project file may set them; Jenesis
installs nothing itself. The installer falls back to `JENESIS_TOOLCHAIN_INSTALLER`, which `jenesis-make` and
`jenesis-exec` set to their own `jenesis-jdk` where SDKMAN, mise or Scoop installed them (`jpx` to `jpx-jdk`, a
byte-identical copy the SDK tests hold to it).

**A new property is added in three places**: the constructor that reads it, the catalogue behind the `configuration`
selector in `Project.java` (`<key>|<default>|<description>`), and the reference table of the user documentation, in
the same commit as a rename or removal. A property the code reads and the catalogue lacks, or a default that drifted,
is a bug. `help` grows for a new selector, never for a property.

**`build.jenesis` is a `ToolProvider`.** `MakeTool`, `ExecuteTool` and `JpxTool` publish `jenesis-make`,
`jenesis-exec` and `jpx`, declared by `provides` in `module-info.java`; the empty `build.jenesis/classpath.properties`
has the build write the `META-INF/services` file for the class path. `JenesisTool` takes the leading `-Djenesis.*`
arguments as the run's settings and writes to the writers it is handed without flushing them. A setting that would
replace the process - `jenesis.toolchain.version`, `jenesis.project.docker`, `jenesis.execute.docker` - is refused by
name, and `run` reports a failure through `err` and its code. Source mode registers no service; a program there
builds `new MakeTool()`.

## Plugins

**`jenesis.plugins.properties` names the plugins the build does not discover** - one per line, `<name>+<hook
point>=<module>` or `<name>=<module>` for the module build. A value starting with `./` is a folder of the project
compiled by an `InternalModule`; a bare name is a module an `ExternalModule` resolves as `module/<name>` from the
Jenesis module repository, and a coordinate with a repository's prefix (`module/<name>`, `maven/<groupId>/<artifactId>`)
is resolved by that repository and its resolver, as a dependency is anywhere else, and loads as `jpx` runs it, with the
JDK's own loaders: `ExternalModule.ofEnvironment` places a Maven coordinate's closure on a class path and infers a
module's, every jar declaring a module in the plugin's layer and any other on a class path beneath it, which the layer's
automatic modules read. `jenesis.project.layout=modular`, or `ExternalModule.pathPlacement(PathPlacement.MODULE_PATH)`,
loads every jar as a module, and the modular layout refuses a Maven coordinate. A class path is parented on the
platform loader, which hands out the packages of the engine's own modules where the engine runs from a module path, so
a plugin on a class path then links against the engine's `build.jenesis` rather than its own. The closure is pinned in
the group `plugin.<name>` (handed on as `+closure`, which `pin` writes into every module that runs the plugin), and
`@<provider>` selects the provider annotated `@BuildModuleName`.
`Project.ofEnvironment` reads the file, and the assembler wires a plugin into the `custom` namespace of its hook point,
in a module only where `plugin-<name>.properties` is found in its configuration locations. The languages, observers and
frameworks of a module hold every plugin they are handed, from that file or from code, under `plugin/<name>` beside
the official ones (`PluginModule.plugins`), so no name of an official plugin can be taken. Its group, which its pins and
everything it resolves are keyed by, and its name at every hook point of the project, is `plugin.<name>`
(`ProjectPlugins.declared`), so an official plugin of the same name, present or added later, runs beside it untouched;
the line naming it, `plugin-<name>.properties`, the `<name>.<key>` arguments and the `plugin/<name>` selector spell the
name as written.

**A plugin provides a factory**, created by `ServiceLoader`, whose `create` validates its values - the engine checks
none - and is handed the loading build's environment, its process `Permits`, its group - its name for an official
plugin, `plugin.<name>` for a declared one - and the build's repositories and resolvers. Everything a plugin resolves
lies in a group `<group>:<purpose>`, its tool in `<group>:tool`; a step resolving in any other group fails. The contracts live in `build.jenesis`:
`BuildModuleFactory` returns the module that is wired; `ObservabilityFactory`, for the hook point `observe`, an
`Observation` pairing the engine that joins the tests' JVM (resolved as `observers/<name>`, its agent in
`<name>:agent`) with the module reporting over what it recorded; `TestFrameworkFactory`, for `test`, a
`TestFramework` prepared as `frameworks/<name>` and consulted before the built-in JUnit Platform, JUnit 4 and TestNG
(`framework=<name>` in `test.properties` picks one); `LanguageFactory`, for `language`, a `Language` naming its
extensions, its compiler before or after javac and its documentation tool, which runs wherever a module holds such a
file, resolved as `languages/<name>`, its documentation joining the aggregate. Kotlin, Scala and Groovy are official
plugins the chains resolve only where the scan finds their extension; a source of a switched-off language fails the
build, and a `pom.xml` module reads a folder beside `src/main/java` only where `jenesis.maven.sources` names it.

**Values and inputs.** A value is a string the host resolved (`@<key>` or `@<key>/<default>` is
`jenesis.variable.<key>`, `@@` a literal `@`), never a path. A key `@<input>[/<target>]=<path>` binds a file or folder
of the project, resolved against the module's folder, into the named input at `<target>`, handed to the plugin as
`../inputs/<input>`; one input takes a key per target, merged by `Bind.asInputs`. A folder must resolve, through any
link, inside the project; a file may lie outside, so projects can share one.

**Hook points of the project**: `preprocess`, `postprocess/transform`, `postprocess/inspect`, `stage/transform`,
`stage/inspect`, `release` and `plugin`, each keeping its plugins apart from its own steps (`custom/`, `transform/`,
`inspect/`). A preprocessor sees only what it binds and exports nothing: it can order and stop the build, never feed
it. A transform adds a file to a module by naming it under `<module>.<key>` in an `inventory.properties` of its own,
and what belongs to no module under `project/`, gathered by `postprocess/project` and copied by `stage/project`; a
module that does not exist or a path written twice is refused. An inspection fails the build by throwing, or by
changing a file it was handed. With a plugin of `stage`, the stock staging moves to `stage/staged/<tree>` and
`stage/<tree>` merges what the transforms wrote, refusing a file staged twice. A releaser runs where a configuration
folder holds its `plugin-<name>.properties`, is handed everything staged plus `root.properties` and
`metadata.properties`, and publishes only under `jenesis.release.publish=true`, which only a command line may set;
`pin` runs after that dry release. The `plugin` module holds plugins run only by their selector, each handed only
what it binds; they are switched on by their line alone, configured by `<name>.<key>` lines of
`jenesis.plugins.arguments.properties` and `jenesis.plugins.arguments-<profile>.properties` (an earlier profile wins),
and pinned into `jenesis.plugins.pin.properties`. `export` has no hook point. `jenesis.project.plugins=false` leaves
out every plugin the file names, while `pin` still resolves them.

**Plugins add, never replace** - except `binary/transform`, whose plugins rewrite the compiled classes in order, for
bytecode enhancement. The module hook point `package` merges what its plugins write to `packages/` with `jpackage`'s
output into `stage/packages/`. Changing what stock steps do takes an entry point that wires its assembler in code. A
plugin runs the project's code, so the project names it; `jenesis.project.docker` isolates an untrusted project, and
no project file may set or widen it.

**A plugin is kept warm.** Its module layer is kept per plugin, jar digest and placement, softly reachable, and
handed to the next build in the JVM loading the same jars, wherever that build resolved them; the layer reads a
private copy of the jars, so no build cleaning its folders pulls them from under it, and the JVM deletes it when it
exits, as it does every folder it keeps for itself (`OfProcess.temporary`). A static field of a plugin must stay right
for that next build.

**Official plugins are discovered.** A module under `project/` (`JDepsModule`, `LicenseHeaderModule`, …) runs what
the JDK ships and activates when its file is in a configuration folder. Every tool the build discovers, shipped or
downloaded, is switched off by `jenesis.<part>.<tool>=false`, named after the part of the build it belongs to -
`check`, `format`, `validate`, `artifact`, `generate`, `language`, `observe`, `compliance`, `documentation`, `release` -
and read by the `ofEnvironment` of the inferred module that wires it, as the boolean beside its child; a tool wired in
two namespaces, as ktlint lints in `check` and formats in `format`, is switched in each apart. A checker's findings
fail the build only under `jenesis.strict.<name>=true` and are reported otherwise. A plugin `jenesis.plugins.properties`
names is switched off by `jenesis.plugin.<name>=false` instead, so the two never share a setting and no name is
reserved. A tool the build would download is an official plugin of `jenesis/jenesis-plugins`: the inferred module that
ran it holds it as `ExternalModule.official`, resolved as `module/build.jenesis.plugin.<name>` in the group `<name>`,
and wires it where a
configuration folder holds its `<name>.properties` (a language plugin where the scan finds a `.kt`, `.scala` or
`.groovy` source). The properties name the tool's own file as `configuration=<path>`, resolved against their own
folder and handed to the plugin as the input `rules` under the name the tool reads (`checkstyle.xml`, `.editorconfig`,
…), and leaving out a key the tool cannot run without fails the build naming it. Only where no configuration folder
holds the properties does the tool's one conventional file in a configuration folder switch it on, bound as if named
(`PluginModule.discovered`); a file at the project root never does, and a plugin needing more than a file, as
Asciidoctor's folder of documents, has no conventional file. The other lines are the plugin's values;
it needs no line in `jenesis.plugins.properties`, and a declared plugin of the same name runs beside it. A
generator reads only the folder it names (`xjc/`, `protoc/`); the project binds its contracts there with
`@<input>/<folder>=<path>`, under the customary name where the tool expects one.

## Modules and steps

**A module configures only its own children.** Every `Inferred*Module` holds a `Function<Child, BuildExecutorModule>`
per child, named like it, the identity by default or `null` when the child's property switches it off (`null`, also
as a result, skips it). The child is built by its own `ofEnvironment` and kept beside its configurator, named for it
(`checkstyleModule`, `javacStep`). A caller nests (`assembler.toolchain(toolchain -> toolchain.compiler(compiler ->
compiler.javac(javac -> …)))`); a new child is a configurator on its parent, never on the assembler. Every
`Inferred*Module` and the module build also hold `custom`, extra children wired in a sub-module `custom`, added by
`custom(name, module)` or `custom(name, step)`, refusing a name taken.

**Configuration files are read through `SequencedProperties`**: `value`, `flag`, `flagOrNull`, `entries`, `words`
trim and read a blank as absent, and a flag reads as a `jenesis.*` setting does. `getProperty` is for the readers
that must tell an empty value from a missing one; a file's own schema is parsed by a private static of its reader.

**Steps are pure functions of folders.** A `BuildStep` reads its `arguments` and writes `context.next()`. Its
serialised form is part of the cache key: a value that should trigger a re-run is a non-`transient` field, and what a
step holds (a `Resolver`, a lambda typed as a serialisable functional interface) is `Serializable`, its scaffolding
`transient`; a module never reaches a key. Steps compose by folders (`sources/`, `classes/`, `artifacts/`), never by
names. Two builds of the same inputs write the same bytes, which `jenesis.project.reproduce` reports step by step.
What a step keeps for its next run - an incremental compiler's state - goes to `context.persistent()`: the folder of
the last successful run, moved and handed only with `previous`, discarded after a failure, empty when the step changed
or the build cache supplied its output, kept when a run reports nothing new. A run from an empty folder writes the same
bytes, only more slowly.

**Processes.** A step forking a JDK tool extends `ProcessBuildStep` and accepts `process-<tool>.properties`; one running
a program extends `EnvironmentalProcessBuildStep` and accepts `environment-<tool>.properties`, whose variables (with
`@<key>` as `jenesis.variable.<key>`) it is handed beside the platform's own (`PATH`, `HOME`, `LANG`, `TMPDIR`,
`SystemRoot`, …); a contract variable is named in `inherits`. What `Execute` or `jpx` runs gets the whole environment.
A tool's main method runs through `ProcessHandler.OfProcess.ofJavaMain()`, by `jenesis.process.mode`:

- `fork` (default) runs each call in a JVM of its own through `build.jenesis.daemon.Worker --once`, so a returned
  code counts and a lingering thread ends with the JVM;
- `aot` does so from an ahead-of-time cache of the tool in `jenesis.aot.folder`, trained by the first run with the
  JVM's messages off;
- `daemon`, set for every build the daemon runs, keeps a `Worker` JVM per JVM, options, class path (by file
  identity), environment and working directory, which compiles a `.java` launcher once, captures each call's
  output, is replaced after a `System.exit` and ends after ten idle minutes.

`jenesis.make.mode=aot` and `=daemon` set the process mode alike unless it is set. The AOT folder holds every cache,
the engine's and the tools', and `Make` clears it whole when `aot.properties` records another engine or JVM or the
folder outlived `jenesis.aot.lifetime`. The build daemon is the same `Worker` on a loopback socket behind a token,
running `DaemonBuild` per request, one at a time. Daemons announce themselves in `jenesis.daemon.folder`
(`~/.jenesis/daemons`), one folder each named after its fingerprint, beside a copy of every engine compiled from
source that one runs; a client locks its project's `.jenesis/daemon.lock` for the build, hands it to a free daemon of
its fingerprint or starts one, and first stops the one used least recently once `jenesis.daemon.limit` (3) run. A
shared daemon starts in its own folder; `jenesis.daemon.shared=false` keys the fingerprint on the project root and
starts the daemon there, which on Windows keeps the project's folder in use for as long as it runs. Either way every
process the build starts runs in `jenesis.process.directory`, the root by default, as it does without a daemon. After
each build the daemon restores the system properties, the default locale and time zone, and ends after a build that
changed a JVM-wide default it cannot restore (the security providers, the default TLS context, socket factory and host
name verifier, authenticator, proxy selector, cookie handler, response cache, uncaught exception handler, standard
input), naming it; a new default of that kind joins `Worker.global` in the same commit. That keeps one build's
settings out of the next, never its code: a daemon is no boundary, which is why a release and a container never
run in one unless a mode names it.

A tool whose main method exits is called through a `<Tool>Driver` of its plugin with an `int main(String[])`, compiled
against the tool by `requires static` or reaching it by reflection where that does not compile; `OfProcess.driver`
writes it once per JVM to a private folder ahead of the tool's jars. `javac` with `-J` options runs in one warm JVM
whose calls run side by side; a tool keeping its state per call asks for the same with `shared(true)`, which a
compiler does. Under `jenesis.process.shared` (default) that JVM is shared in every mode: `fork` and `aot` end it when
the last build running in the JVM ends, through the scope `Project.build` holds from `ProcessHandler.scope()`, while
`daemon` keeps it for the next build; `false` forks such calls as well. A command line of another shape forks as
`ofJavaHome("bin/java")` does.
Every process the engine waits on is started through `OfProcess.start`, which ends it with the JVM: a shutdown hook
asks it and its descendants to stop and kills them ten seconds later, so a terminated build leaves no test JVM
behind. `Make` and `Toolchain`, which compile alone, end the JVM they relaunch with a hook of their own.

## Tests

- `tests/` is the `@jenesis.test` module on JUnit Jupiter and AssertJ. A test method is a sentence in `snake_case`
  stating what it proves; an assertion carries its reason in `.as(...)` where needed.
- A step is hashed with what it captures: a step with state is a static nested record, a test whose step lambdas use
  its fields implements `Serializable`, and state a step must not capture (a latch, a socket) is static.
- A test names its settings in `new Environment(Map.of(…))` and collects output with `.out(printed::add)`; it sets no
  system property and swaps no stream unless it is about the JVM's own properties.
- `@TempDir` for every build, `BuildExecutorCallback.nop()` and `BuildExecutorCache.nop()` unless under test,
  `Runnable::run` where ordering matters. User-visible behaviour gets a demo as well.

## Public surface and the documentation

`build.jenesis` is published to Maven Central and vendored as source, so every public type is API: a rename of a
method, property or selector updates the demos, the `help`/`skill` text and the user documentation in the same pass,
and a property missing from jenesis.build is not finished. A demo's README is a user guide - what to declare, what to
run, what comes out - read in order, referring only backwards; a demo repeating another is folded into it.

## Releasing and downstream

A release is a manual run of the release workflow: its optional `sha` input names the commit, its optional `tag` the
tag (default: the next minor of the highest `vX.Y.Z`). It stages, signs, publishes to Maven Central, SDKMAN, Homebrew
and Scoop, and tags. `jenesis-launcher`, `jenesis-modules` and `jenesis-repository` pin this repository as the
`build/.upstream` submodule and are moved to the release commit afterwards.
