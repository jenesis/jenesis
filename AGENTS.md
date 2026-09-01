# AGENTS.md

Jenesis, the build tool, and jpx, the module runner, in one repository. The tool builds itself: `build/jenesis`
links to `sources/build/jenesis`, so `java build/jenesis/Project.java` compiles, tests and packages this
project with the very sources it is working on. `README.md` covers the layout, the demos, CI and releasing;
this file is how the code is written and changed. The user documentation is
[jenesis.build](https://jenesis.build) ([jenesis/jenesis-documentation](https://github.com/jenesis/jenesis-documentation)),
and `java build/jenesis/Project.java skill` prints an onboarding briefing from the same material.

## Build & test

- **JDK 25 or newer**, nothing else. `java build/jenesis/Project.java` builds and tests everything;
  `stage` lays out the release tree; `pin` rewrites the pins; `help` lists every selector and `-D` flag.
- One test class: `java -Djenesis.test.filter='.*BuildExecutorTest' -Djenesis.test.force=true
  -Djenesis.print.tests=true build/jenesis/Project.java +tests`. The filter is a regex over class names,
  `force=true` runs tests whose inputs did not change, `print.tests` streams the JUnit output. Classes named
  `*RunTest` run the real external tool and need the network.
- CI builds under `-Djenesis.dependency.pin=strict`: after changing a dependency, run `pin` and commit the
  rewritten `module-info.java` / `pom.xml` lines, or CI fails on the unpinned coordinate.
- When the tool cannot build itself, `mvn test` (the root `pom.xml`) compiles and tests the sources without
  Jenesis. It validates only; it stages and pins nothing.
- Every feature ships a demo under `demo/` with a README and a verification command in
  `.github/workflows/build.yml`; the demos are the end-to-end suite and the documentation's examples.

## How the code is written

**No comments, no Javadoc.** The sources carry neither, and a change adds none - not inline, not on a type,
not on a method, in `sources/` or `tests/`. A name, a type or a smaller method carries the meaning; if a
construct seems to need a comment, restructure it or name it better. The one place Javadoc appears is
`module-info.java`, because the `@jenesis.*` tags there (`@jenesis.main`, `@jenesis.test`, `@jenesis.pin`,
…) are configuration the tool reads, not commentary.

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
returns a new instance with that value replaced (`new Project().version("1.0.0").sources(true)`,
`new BuildExecutor.Configuration().concurrency(4)`). No setters, no builders, no `with` prefix.

**System properties are the defaults.** Every setting is a `jenesis.<area>.<name>` system property, read
once where the object is constructed:

- the public no-argument or short constructor reads the property with its default
  (`System.getProperty("jenesis.executor.digest", "MD5")`, `Integer.getInteger("jenesis.executor.concurrency", 0)`,
  `Boolean.parseBoolean(System.getProperty("jenesis.source.pmd", "true"))`);
- a private canonical constructor takes every value explicitly;
- the wither overrides one value and calls the canonical constructor.

A property is therefore never read again later, and a caller that constructs the object itself is never
surprised by the environment. Choose the reading deliberately: `Boolean.getBoolean` means `=true` is required,
`getProperty(...) != null` means presence alone switches it on, `parseBoolean(getProperty(..., "true"))` means
an opt-out. Environment variables are fallbacks for the repository settings only
(`MAVEN_REPOSITORY_URI`, `JENESIS_REPOSITORY_TOKEN`, …). `jenesis.properties` at the project root and the
profile files feed the same properties; `Project.main` loads them before anything is constructed. A new
property is added in three places - the constructor that reads it, the `help` text in `Project.java` (and
`skill` where it fits), and the reference table in the user documentation; the `properties` selector picks it
up by itself.

**Configuration files are read through `SequencedProperties`.** A file is read with the type's own accessors -
`value`, `value(key, default)`, `flag`, `flag(key, default)`, `entries` for a comma-separated list, `words`
for a whitespace-separated command line - which trim and treat a blank value as an absent one, so no reader
hand-rolls that again. `getProperty` stays the raw `Properties` contract for the few readers that must tell
an empty value from a missing one (an alias line whose emptiness removes an entry, a coordinate whose empty
location marks it unresolved). A convention of the file format belongs on the type; parsing that belongs to
one file's schema - a `<name>=<coordinate>` plugin list, a key whitelist - stays a private static in that
reader.

**Steps are pure functions of folders.** A `BuildStep` reads its `arguments` (one folder per predecessor) and
writes into `context.next()`, nothing else. It is `Serializable` and its serialised form is part of the cache
key, so every value that should trigger a re-run is a non-`transient` field and every field is serialisable
(`Path` is hashed by its string form; a lambda field must be typed as a serialisable functional interface).
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
skips the child, and so does a configurator that returns `null`. A caller reaches further down by nesting -
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
- `@TempDir` folders for every build; `BuildExecutorCallback.nop()` and `BuildExecutorCache.nop()` unless the
  test is about them; `Runnable::run` as the executor when ordering matters.
- Behaviour that is user-visible gets a demo as well as a test.

## Public surface and the documentation

`build.jenesis` is published to Maven Central and vendored as source into every project that uses the tool, so
every public type is API: renaming a method, a property or a selector is a breaking change and is done
deliberately, with the demos, the `help`/`skill` text and the user documentation updated in the same pass.
The documentation repository's `AGENTS.md` describes how a change is verified and where each setting is
documented; a property that exists only in the code and not on jenesis.build is not finished.

## Releasing and downstream

A release is a manual run of the release workflow from the Actions tab, so any commit is releasable: its
optional `sha` input names the commit (default: the head it runs on) and its optional `tag` input names the
tag (default: the next minor of the highest `vX.Y.Z` tag). The workflow stages, signs, publishes to Maven
Central, SDKMAN, Homebrew and Scoop, and cuts the `vX.Y.Z` tag. `jenesis-launcher`, `jenesis-modules` and
`jenesis-repository` pin this repository as the `.jenesis/upstream` git submodule; after a release they are
moved to the release commit.
