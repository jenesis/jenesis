Jenesis demos - a guided tour
=============================

Every demo is a small, self-contained project that adds one idea to the one
before it, so reading them in order doubles as a tutorial: start with a single
Maven project, turn it into a module, scale to many modules, package a runnable
application, configure the compiler, generate sources instead of writing them,
shape the dependency graph, lock the supply chain down, add quality gates and
tests, bring in other JVM languages, cache and confine the build, extend it, and
finally sign, publish and consume what you built.

Every demo has its own `build/jenesis` symlink into this repository's
`sources/build/jenesis`, so each one runs from inside its own directory with no
installation step. Each writes to a local `target/` directory; delete it to build
from scratch.

Running a demo
--------------

Most demos are run with the shipped entry point:

    java build/jenesis/Make.java

That runs the default `build` goal. Any other goal is a command-line argument:

    java build/jenesis/Make.java pin      # record resolved versions and checksums in the sources
    java build/jenesis/Make.java stage    # lay the artifacts out as local repositories
    java build/jenesis/Make.java export   # publish them into the local repositories
    java build/jenesis/Make.java ide      # write IntelliJ, Eclipse and VS Code project files
    java build/jenesis/Make.java help     # usage; `skill` prints a longer briefing

An installed CLI takes the same arguments (`jenesis stage`), and so does a demo
whose entry point is `build/Demo.java`.

Two switches apply to any demo, on the command line or in a `jenesis.properties`
file at the demo root; an explicit `-D` wins over the file:

    java -Djenesis.project.watch=true build/jenesis/Make.java   # rebuild on every source change (Ctrl+C to stop)
    java -Djenesis.project.docker=true build/jenesis/Make.java  # run the whole build inside a throwaway container

A demo that has an entry point is run with `java build/jenesis/Execute.java`,
which builds the project and then launches it. A demo that packages an
application, asserts that a policy violation fails the build, or drives the API
directly ships its own `build/Demo.java` and is run with that; the index below
names the command for each.

Quick index
-----------

| #  | Demo                                                              | Shows                                                                       | Run from the demo dir             |
|----|-------------------------------------------------------------------|-----------------------------------------------------------------------------|-----------------------------------|
| 1  | [`java-pom`](demo-01-java-pom/README.md)                          | A single Maven (`pom.xml`) project: one dependency, pinned                   | `java build/jenesis/Make.java`    |
| 2  | [`java-modular`](demo-02-java-modular/README.md)                  | The same project as a Java module (`module-info.java`, no `pom.xml`)         | `java build/jenesis/Make.java`    |
| 3  | [`java-pom-multi`](demo-03-java-pom-multi/README.md)              | Many Maven modules: a library, and a consumer of it and of an external jar   | `java build/jenesis/Make.java`    |
| 4  | [`java-modular-multi`](demo-04-java-modular-multi/README.md)      | The same multi-module project as Java modules                                | `java build/jenesis/Make.java`    |
| 5  | [`startup`](demo-05-startup/README.md)                            | What a build costs to start, and what the daemon saves on top                | `java build/jenesis/Make.java`    |
| 6  | [`java-pom-executable`](demo-06-java-pom-executable/README.md)    | A runnable Maven project packaged into an application image with `jpackage`  | `java build/Demo.java`            |
| 7  | [`java-modular-executable`](demo-07-java-modular-executable/README.md) | The same as a module, plus a `.jmod`, a `jlink` runtime and a bundle    | `java build/Demo.java`            |
| 8  | [`bundle`](demo-08-bundle/README.md)                              | Ship an app as a zip of jars for a stock JRE, then unpack and run it         | `java build/Demo.java`            |
| 9  | [`java-multi-release`](demo-09-java-multi-release/README.md)      | A multi-release jar: a Java 21 baseline with a Java 25 override              | `java build/jenesis/Execute.java` |
| 10 | [`javac-arguments`](demo-10-javac-arguments/README.md)            | Pass extra flags to `javac` with a `process-javac.properties` file           | `java build/jenesis/Execute.java` |
| 11 | [`annotations`](demo-11-annotations/README.md)                    | Run an annotation processor declared with `@jenesis.plugin`                  | `java build/jenesis/Make.java`    |
| 12 | [`error-prone`](demo-12-error-prone/README.md)                    | Run Error Prone as a `javac` plugin, and fail on what it finds               | `java build/Demo.java`            |
| 13 | [`data-formats`](demo-13-data-formats/README.md)                  | Generate sources from an XML schema, a `.proto` and an Avro schema           | `java build/jenesis/Make.java`    |
| 14 | [`service-contracts`](demo-14-service-contracts/README.md)        | Generate a client from a WSDL and from an OpenAPI document                   | `java build/jenesis/Make.java`    |
| 15 | [`antlr`](demo-15-antlr/README.md)                                | Generate a parser from an ANTLR grammar                                      | `java build/jenesis/Execute.java` |
| 16 | [`maven-exclusions`](demo-16-maven-exclusions/README.md)          | Prune a transitive dependency, with `<exclusions>` or `@jenesis.exclude`     | `java build/jenesis/Make.java`    |
| 17 | [`bom`](demo-17-bom/README.md)                                    | Curate versions in a bill of materials with `@jenesis.bom`                   | `java build/jenesis/Make.java`    |
| 18 | [`module-alias`](demo-18-module-alias/README.md)                  | Give a jar with no module name one with `@jenesis.alias`                     | `java build/jenesis/Execute.java` |
| 19 | [`module-layout`](demo-19-module-layout/README.md)                | Select the pure modular layout: resolve by module name, emit no `pom.xml`    | `java build/jenesis/Make.java`    |
| 20 | [`module-override`](demo-20-module-override/README.md)            | Require an API a dependency already ships, with `@jenesis.override`          | `java build/jenesis/Execute.java` |
| 21 | [`module-layers`](demo-21-module-layers/README.md)                | Keep a dependency private in a layer with `@jenesis.layer`                   | `java build/Demo.java`            |
| 22 | [`module-layer-legacy`](demo-22-module-layer-legacy/README.md)    | The same over a tree of jars that name themselves nowhere                    | `java build/Demo.java`            |
| 23 | [`platform-guard`](demo-23-platform-guard/README.md)              | Pin a classified variant, and guard which one each platform gets             | `java build/jenesis/Execute.java` |
| 24 | [`platform-guard-pom`](demo-24-platform-guard-pom/README.md)      | The same guards in a `pom.xml`                                               | `java build/jenesis/Execute.java` |
| 25 | [`pinning`](demo-25-pinning/README.md)                            | What a pin records, and what strict pinning refuses                          | `java build/Demo.java`            |
| 26 | [`openpgp`](demo-26-openpgp/README.md)                            | Declare the OpenPGP key that signs a dependency                              | `java build/Demo.java`            |
| 27 | [`sigstore`](demo-27-sigstore/README.md)                          | Declare the Sigstore identity that released a dependency                     | `java build/Demo.java`            |
| 28 | [`sbom`](demo-28-sbom/README.md)                                  | The CycloneDX bill of materials every build emits                            | `java build/jenesis/Make.java`    |
| 29 | [`compliance`](demo-29-compliance/README.md)                      | Fail the build on a dependency license the policy denies                     | `java build/Demo.java`            |
| 30 | [`vulnerabilities`](demo-30-vulnerabilities/README.md)            | Fail the build on a known vulnerability, queried from OSV                    | `java build/Demo.java`            |
| 31 | [`java-quality`](demo-31-java-quality/README.md)                  | Checkstyle, PMD, SpotBugs and a formatter, each on by its config file        | `java build/jenesis/Make.java`    |
| 32 | [`test-framework`](demo-32-test-framework/README.md)              | Run tests whose module names no engine, inferred or declared                 | `java build/jenesis/Make.java`    |
| 33 | [`code-coverage`](demo-33-code-coverage/README.md)                | Record coverage with JaCoCo and render a report                              | `java build/jenesis/Make.java`    |
| 34 | [`test-selection`](demo-34-test-selection/README.md)              | Re-run only the tests a change can reach                                     | `java build/Demo.java`            |
| 35 | [`pitest`](demo-35-pitest/README.md)                              | Mutation testing with PIT                                                    | `java build/jenesis/Make.java`    |
| 36 | [`jmh`](demo-36-jmh/README.md)                                    | Generate, compile and run a JMH benchmark                                    | `java build/jenesis/Execute.java` |
| 37 | [`api-compatibility`](demo-37-api-compatibility/README.md)        | Compare the jar against the last release with japicmp                        | `java build/Demo.java`            |
| 38 | [`kotlin`](demo-38-kotlin/README.md)                              | Java and Kotlin in one module                                                | `java build/jenesis/Make.java`    |
| 39 | [`kotlin-quality`](demo-39-kotlin-quality/README.md)              | detekt and ktlint, inferred from their config files                          | `java build/jenesis/Make.java`    |
| 40 | [`kotlin-plugin`](demo-40-kotlin-plugin/README.md)                | Run a Kotlin compiler plugin declared with `@jenesis.plugin`                 | `java build/jenesis/Make.java`    |
| 41 | [`scala`](demo-41-scala/README.md)                                | Java and Scala 3 in one module                                               | `java build/jenesis/Make.java`    |
| 42 | [`scala-quality`](demo-42-scala-quality/README.md)                | Scalastyle and scalafmt, inferred from their config files                    | `java build/jenesis/Make.java`    |
| 43 | [`groovy`](demo-43-groovy/README.md)                              | Java and Groovy in one module                                                | `java build/jenesis/Make.java`    |
| 44 | [`groovy-quality`](demo-44-groovy-quality/README.md)              | CodeNarc, inferred from its config file                                      | `java build/jenesis/Make.java`    |
| 45 | [`profiles`](demo-45-profiles/README.md)                          | Switch a set of properties on together with a named profile, or a run with a file | `java build/jenesis/Make.java`    |
| 46 | [`build-cache`](demo-46-build-cache/README.md)                    | Serve step outputs from a cache shared across builds                         | `java build/jenesis/Make.java`    |
| 47 | [`docker-isolation`](demo-47-docker-isolation/README.md)          | Confine the build and the program it produces to a container                 | `java build/jenesis/Make.java`    |
| 48 | [`agents`](demo-48-agents/README.md)                              | Attach a library as a Java agent with `@jenesis.attach`                      | `java build/Demo.java`            |
| 49 | [`native-access`](demo-49-native-access/README.md)                | Grant native access with `@jenesis.native`, and discover what to redeclare  | `java build/jenesis/Execute.java` |
| 50 | [`native-access-layer`](demo-50-native-access-layer/README.md)    | Grant a library native access, which it passes on to the modules of its layer | `java build/Demo.java`            |
| 51 | [`custom-assembler`](demo-51-custom-assembler/README.md)          | Wrap the assembler to preprocess sources before the regular flow             | `java build/Demo.java`            |
| 52 | [`custom-jmod`](demo-52-custom-jmod/README.md)                    | Pack extra content into a `.jmod` and carry it into a packaged app           | `java build/Demo.java`            |
| 53 | [`internal-module`](demo-53-internal-module/README.md)            | Move that preprocessing into a build module loaded from local source         | `java build/Demo.java`            |
| 54 | [`external-module`](demo-54-external-module/README.md)            | Resolve the same build module as a published coordinate                      | `java build/Demo.java`            |
| 55 | [`custom-maven`](demo-55-custom-maven/README.md)                  | Drive a multi-module Maven build without `Project`                           | `java build/Demo.java`            |
| 56 | [`custom-modular`](demo-56-custom-modular/README.md)              | The same for `module-info.java` modules                                      | `java build/Demo.java`            |
| 57 | [`custom-build`](demo-57-custom-build/README.md)                  | No template at all: wire the build by hand                                   | `java build/Demo.java`            |
| 58 | [`tools-api`](demo-58-tools-api/README.md)                        | Run a build, or a published program, inside another program's JVM            | `java build/Demo.java`            |
| 59 | [`code-signing`](demo-59-code-signing/README.md)                  | Sign the produced jar with `jarsigner`, keyed by the environment             | `java build/Demo.java`            |
| 60 | [`export`](demo-60-export/README.md)                              | Install a build into the local repositories, for other projects to require   | `java build/jenesis/Make.java export`|
| 61 | [`publishing`](demo-61-publishing/README.md)                      | Assemble a Maven Central ready bundle and resolve it back                    | `java build/Demo.java`            |
| 62 | [`module-convention`](demo-62-module-convention/README.md)        | Resolve your own modules from your own Maven repository                      | `java build/Demo.java`            |
| 63 | [`reproducible`](demo-63-reproducible/README.md)                  | Build the same bytes on every machine, checked against a recorded digest     | `java build/Demo.java`            |
| 64 | [`toolchain`](demo-64-toolchain/README.md)                        | Name the JDK the build runs on, and relaunch on it                           | `java build/jenesis/Execute.java` |
| 65 | [`native-image`](demo-65-native-image/README.md)                  | Compile the application into a GraalVM native binary                         | `java build/jenesis/Make.java`    |
| 66 | [`jpx`](demo-66-jpx/README.md)                                    | Run a released program without building anything                             | `java build/Demo.java`            |

## 1. A single Maven project - [`java-pom`](demo-01-java-pom/README.md)

Start here. `java-pom` is the smallest real build: a `pom.xml` and one Java source
that uses `org.apache.commons.lang3.StringUtils`. A `pom.xml` at the root is all
Jenesis needs to know what kind of project this is, so there is no build script.
Running it resolves `commons-lang3` from Maven Central (or from your `~/.m2`) and
compiles against it.

Two ideas every later demo builds on:

- **You declare the project, not the build.** The descriptor you already have
  decides the shape of the build; nothing about the toolchain is configured by
  hand.
- **Pinning.** `java build/jenesis/Make.java pin` records each resolved
  dependency, with the `SHA-256` of the bytes it was served, into the POM's
  `<dependencyManagement>` block. Later builds verify every download against it.
  This demo ships already pinned.

## 2. The same project as a module - [`java-modular`](demo-02-java-modular/README.md)

`java-modular` is the same one-class project with `sources/module-info.java` as
its only descriptor and no `pom.xml`. A `requires org.slf4j` is what drives
resolution, and the build emits both a modular jar and a generated `pom.xml`, so
the artifact is consumable either way.

Pins live in the module declaration instead of a POM, as Javadoc tags:

    @jenesis.pin org.slf4j 2.0.16
    @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/...

A pin token reads by its slash count: a bare token is a Java module name, one
slash is a Maven `<groupId>/<artifactId>`, and the fully explicit form is
`<group>/<repository>/<coordinate>`. `pin` writes all of them for you.

`stage` lays the output out as both a module repository and a Maven repository,
and `export` publishes them into your local repositories.

## 3. Many modules at once - [`java-pom-multi`](demo-03-java-pom-multi/README.md), [`java-modular-multi`](demo-04-java-modular-multi/README.md)

Point Jenesis at the root of a multi-module project and it finds the modules,
orders them, and lets them depend on one another with no wiring.
`java-pom-multi` is a Maven aggregator over a `greeter` library and an `app` that
depends on it and on external `commons-lang3`. `java-modular-multi` is the modular
twin, where `app` requires the sibling `demo.greeter` and the external
`org.slf4j`. Pinning records the external dependencies; a sibling has no
published version to pin against.

Both demos also run tests. In the Maven project the `greeter` module adds a
`<testSourceDirectory>` and a test-scoped JUnit dependency; in the modular one a
separate `greeter-test` module is marked `@jenesis.test demo.greeter`. Either way
the test engine is detected from the resolved jars and the tests run as part of
the build.

Build one module rather than all of them by naming it, and write the project
files your editor reads with `ide`:

    java build/jenesis/Make.java +greeter
    java build/jenesis/Make.java ide

## 4. What a build costs to start - [`startup`](demo-05-startup/README.md)

`startup` is a timing exercise rather than a feature. It builds an ordinary
project and reports what the build costs: roughly 3.6 seconds for a build that
runs once, and 0.8 for a repeat.

A reused JVM shaves the compile time on top of that, and is off by default:

    java -Djenesis.make.daemon=true build/jenesis/Make.java
    java build/jenesis/Make.java --stop

The daemon is worth reaching for on a project with several modules to compile,
and worth nothing on a small one. It serves one project at a time, and only
`-Djenesis.*` flags travel with a call - change the build sources, the
environment or the JVM arguments and the daemon is replaced.

## 5. Packaging a runnable application - [`java-pom-executable`](demo-06-java-pom-executable/README.md), [`java-modular-executable`](demo-07-java-modular-executable/README.md), [`bundle`](demo-08-bundle/README.md)

A module that declares an entry point can be packaged into a self-contained
application image. The entry point is declared where the descriptor already is:
an `@jenesis.main sample.Sample` tag on `module-info.java`, or a `<mainClass>`
property in the POM. A module without one is not packaged.

Packaging is turned on by a `packaging.properties` file in the configuration
location - a module's `META-INF/build.jenesis/` folder, its `build.jenesis/`
folder, or the project root. Its keys are the packaging menu:

    jpackage=app-image   # a native application image; any jpackage --type value
    jmod=true            # a .jmod beside the modular jar
    jlink=true           # a runtime image trimmed to the module graph
    bundle=true          # a zip of jars to unpack onto a stock JRE
    launcher=true        # a single executable jar you run with java -jar
    docker=<base image>  # a Dockerfile and its build context

Both executable demos commit `jpackage=app-image` and run the packaged image with
the arguments you pass:

    java build/Demo.java ada lovelace

Each also ships `build/DemoNative.java`, which packages the platform's native
installer (`deb`/`rpm`, `exe`/`msi`, `dmg`/`pkg`) instead, and
`build/DemoLauncher.java`, which builds the single executable jar and runs it.
The installer needs the platform's packaging tools on the `PATH`, so it is a
local exercise. `bundle` unpacks the `bundle.zip` its own build wrote and runs it
on a stock JRE, and the `docker` profile stages a build context you hand to
Docker yourself:

    java -Djenesis.make.profiles=docker build/jenesis/Make.java stage
    docker build -t sample target/stage/docker/output/module

Staged output lands beside the other staging trees: `stage/packages` for images,
`stage/runtime` for a `jlink` runtime, `stage/docker` for a build context.

## 6. A multi-release jar - [`java-multi-release`](demo-09-java-multi-release/README.md)

One artifact can hold version-specific code. The module compiles at Java 21 with
an `@jenesis.release 21` tag, and one class has a Java 25 replacement under
`sources/META-INF/versions/25/`. The build compiles the overlay separately and
marks the jar `Multi-Release: true`, so the same jar prints the baseline line on
Java 21 and the override on Java 25:

    java build/jenesis/Execute.java

## 7. Configuring the Java compiler - [`javac-arguments`](demo-10-javac-arguments/README.md), [`annotations`](demo-11-annotations/README.md)

`javac-arguments` hands `javac` an extra flag without a build script. A
`process-javac.properties` file in a configuration location lists arguments for
the tool its name points at - here `-parameters`, so parameter names survive into
the bytecode, which the demo asserts by reflection. The same file name pattern
configures every external tool the build runs: `process-kotlinc.properties`,
`process-jlink.properties`, `process-jpackage.properties`, and so on. A profile's
file overrides a general one, and an empty file switches its arguments off.

`annotations` runs a Java annotation processor. It is named by a single tag on
the module declaration, by module name, just as `requires` names a dependency:

    @jenesis.plugin org.immutables.value

Processors are declared, never discovered: the same jar is also an ordinary
`requires static` dependency here, and deleting the `@jenesis.plugin` line stops
the processor from running even though the jar is still on the compile path. The
generic form is `@jenesis.plugin <repository>/<coordinate>`, and naming a
compiler first routes the plugin to that compiler instead.

## 8. A plugin inside the compiler - [`error-prone`](demo-12-error-prone/README.md)

Error Prone is a check that runs inside `javac` rather than beside it, so it sees
the same typed syntax tree the compiler does and reports through the compiler's
own diagnostics. It is declared with the same tag an annotation processor uses,
with the compiler named first:

    @jenesis.plugin javac maven/com.google.errorprone/error_prone_core

An `errorprone.properties` is the switch that turns it on, and its `arguments`
are passed on to the plugin, so every Error Prone flag applies:

    arguments=-Xep:ReferenceEquality:ERROR

The demo promotes `ReferenceEquality` to an error and the build fails on a `==`
comparison of two strings; the second half of `build/Demo.java` switches the
plugin off again and the same sources compile. `-Djenesis.compile.errorprone=false`
is the opt-out.

## 9. Generating sources instead of writing them - [`data-formats`](demo-13-data-formats/README.md), [`service-contracts`](demo-14-service-contracts/README.md)

Not every source file is written by hand. `data-formats` builds three modules
from three inputs: an `order.xsd` through the JAXB binding compiler, a
`greeting.proto` through `protoc` and its gRPC plugin, and a `user.avsc` through
`avro-tools`. `service-contracts` does the same for the client side of a service:
a WSDL through `wsimport` and an OpenAPI document through the OpenAPI Generator.

Each generator is turned on by its own configuration file - `xjc.properties`,
`protoc.properties`, `avro.properties`, `wsimport.properties`,
`openapi.properties` - and an empty file is enough to activate one. The file also
carries whatever that tool needs to be told: `openapi.properties` names its
input, because a `.yaml` does not announce itself the way a `.xsd` or a `.proto`
does.

The input goes under `META-INF/build.jenesis/` in the module's sources, where it
is an input to the build and nothing more - none of these jars carries the schema
it was built from. An input that *should* ship goes in a folder the configuration
file names instead (`folders=schema`), which is how `service-contracts` keeps its
WSDL in the jar, where a JAX-WS client reads it at run time.

Generated sources are compiled into the module as if you had written them, and
the linters and formatters read your own sources only. Each tool resolves in a
dependency group named after it and is pinned separately from the module's own
dependencies, so upgrading a generator never moves the library the generated code
calls into.

## 10. Generating a parser - [`antlr`](demo-15-antlr/README.md)

`antlr` is the same three moves for a grammar: a `.g4` under the module's
`META-INF/build.jenesis/` folder, an `antlr.properties` that activates the
generator, and generated sources compiled into the module. What the file adds is
where the parser lands:

    package=demo.antlr.calc
    arguments=-visitor -no-listener

`package` is passed to ANTLR and also decides the directory it writes into, and
`arguments` goes to the tool verbatim. The module declares only the ANTLR
*runtime* the generated code calls into; the tool resolves in its own `antlr`
group, so upgrading the generator never moves the runtime.

    java build/jenesis/Execute.java "10 / 2 + 3 * 4"

## 11. Excluding a transitive dependency - [`maven-exclusions`](demo-16-maven-exclusions/README.md)

`maven-exclusions` declares Apache Commons Text but prunes its Commons Lang
transitive, and a test confirms the result. A POM states this with
`<exclusions>`; a module declaration states the same thing as a tag:

    @jenesis.exclude org.apache.commons.text org.apache.commons/commons-lang3

One line names the module the exclusion applies to and any number of
`<groupId>/<artifactId>` targets. As in Maven, it applies to the test path as
well as the main one, and the excluded artifact is absent from the generated POM
and from the bill of materials too, because the build never fetched it.

## 12. Bills of materials - [`bom`](demo-17-bom/README.md)

Where a `@jenesis.pin` tag pins one artifact, a bill of materials curates
versions for many. `bom` shows both forms:

    @jenesis.bom org.slf4j/slf4j-bom 2.0.16      # a Maven BOM, fetched and imported
    @jenesis.bom pin-lang3.properties            # a local file of versions and checksums

A Maven BOM is imported from its `dependencyManagement`; because POM bytes differ
between repositories the reference itself carries no hash, so `pin` records a
checksum for each artifact resolved through it. A local properties file carries
the version and the `SHA-256` for each entry itself, so nothing has to be pinned
beside it. BOM keys use the pin token grammar without the group; the group and an
optional platform guard sit on the declaration (`kotlinc/pin-lang3.properties`).

A local `@jenesis.pin` overrides any BOM entry, the last declared BOM wins a
conflict, and either model satisfies `-Djenesis.dependency.pin=strict`.

## 13. Aliasing a plain library - [`module-alias`](demo-18-module-alias/README.md)

Plenty of libraries carry no module identity at all: no `module-info`, not even
an `Automatic-Module-Name`. `module-alias` gives one the name the project wants
to use:

    @jenesis.alias org.kohsuke.args4j args4j/args4j

The module then states `requires org.kohsuke.args4j` like any other dependency,
and can `opens` a package to it - which args4j needs, since it sets `@Option`
fields by reflection. The alias renames the resolved file rather than rewriting
it, so a pinned checksum keeps describing the same bytes.

    java build/jenesis/Execute.java -name Ada -shout

An alias makes a jar requirable, but only as an automatic module, and `jlink`
refuses those. A `modules.properties` file holding `mode=declared` closes that
gap: the resolved closure is rewritten into explicit named modules, and that
rewritten closure is what the compiler, the tests and the packaging all use, so a
module graph that does not hold together fails at compile time rather than first
in the shipped image. With `jlink=true` beside it, `java build/jenesis/Make.java
stage` links a runtime that runs the application with no class path.
`mode=synthetic` names a deep transitive nobody wants to name by hand, and
`mode=none` opts one module out of a project-wide file.

## 14. Choosing the pure modular layout - [`module-layout`](demo-19-module-layout/README.md)

A `module-info.java` with no `pom.xml` gives you a modular jar *and* a generated
POM. The pure modular layout instead resolves dependencies by Java module name
and emits the modular jar alone, so no Maven coordinate is involved anywhere.
Select it in a `jenesis.properties` file at the project root, which the launcher
reads before the build:

    jenesis.project.layout=modular

`stage` then produces `target/stage/modular` and no `target/stage/maven`. Reach
for this layout when your artifacts are consumed only as Java modules; keep the
default when you also need a POM. It is opt-in because every dependency has to
resolve as a named module.

## 15. Overriding a shaded module - [`module-override`](demo-20-module-override/README.md)

A package belongs to exactly one module, and a library that copies another
library's packages into its own jar breaks that rule for everyone downstream.
Tomcat Embed is the well-known case: it exports `jakarta.servlet` and
`jakarta.el` under its own module names, so a modular library that states
`requires jakarta.servlet` cannot share a module path with it.

One tag per module states the relationship:

    @jenesis.override jakarta.servlet org.apache.tomcat.embed.core

A module of the required name is placed that holds no packages and reads the
carrier, and every artifact that would carry the packages twice is dropped. The
published descriptor still says `requires jakarta.servlet`, which is the point:
what you publish names the API rather than the server you happened to build
against.

## 16. Keeping a dependency private - [`module-layers`](demo-21-module-layers/README.md), [`module-layer-legacy`](demo-22-module-layer-legacy/README.md)

A module path holds one module per name, so a library that needs a different
version of some dependency than its consumer has nowhere to put it. Rather than
relocate packages, declare a layer on the library that needs the isolation:

    @jenesis.layer render api demo.layers.spi
    @jenesis.layer render provider demo.layers.impl

The `api` line names the one module the library shares with its layer; the
`provider` line names what the layer holds, resolved in a dependency group of its
own. The consumer declares nothing: the declaration travels in the library's jar,
and any build that resolves that jar reconstructs the layer from it. Layers nest,
and a `@jenesis.test` module gets its layers the same way a deployment does.

What crosses the boundary is the API module, so a dependency whose types your API
module exposes cannot be isolated behind it - the build says so rather than
leaving it to a `LinkageError`.

`module-layer-legacy` applies the same mechanism to a tree of jars that name
themselves nowhere: one `@jenesis.alias` names the jar the code calls, and the
rest is read through the layer's own class path.

## 17. Selecting a dependency variant - [`platform-guard`](demo-23-platform-guard/README.md), [`platform-guard-pom`](demo-24-platform-guard-pom/README.md)

Some artifacts publish several variants under one coordinate, told apart by a
Maven classifier, and which one you want usually depends on the machine. Both
halves live in the pin: the classifier in its value, and a guard at the end of
the line, matched against the platform the build runs on - the detected operating
system and chipset, plus any token a `-Djenesis.platform.<token>=true` flag adds:

    @jenesis.pin mutiny.zero 1.1.1 SHA-256/2ba03737...
    @jenesis.pin mutiny.zero :jdk-flow:0.4.3 SHA-256/0556f076... (legacy)

The most specific match wins and the unguarded line is the fallback, so every
variant stays committed with its own checksum and the build stays reproducible on
every machine. A real project guards with `(windows)` or `(macos,aarch64)`; this
demo uses a neutral `legacy` token so the effect is visible anywhere:

    java -Djenesis.platform.legacy=true build/jenesis/Execute.java

`platform-guard-pom` shows the identical guards in a `pom.xml`, inside its
`<!--jenesis.pin ... -->` comment block. Re-pinning refreshes only the line that
matched the local platform and leaves the others untouched.

## 18. Pinning a dependency - [`pinning`](demo-25-pinning/README.md)

A pin records the version of a dependency and the `SHA-256` of the bytes that
version served, so a pinned project resolves the same closure on every machine
and fails if a repository serves something else. `pinning` ships three small
projects, each wrong in a different way: one is pinned and builds, one carries a
version but no checksum, and one carries a checksum that does not match.

The unpinned project builds by default and fails under strict pinning, which is
the mode a hardened build wants:

    java -Djenesis.dependency.pin=strict build/jenesis/Make.java

The tampered one fails either way, because every download is compared against its
pin. You never write these lines by hand - `java build/jenesis/Make.java pin`
records what it resolved, and `-Djenesis.dependency.pin=ignore` before a `pin` run
is how a project deliberately moves to newer versions.

## 19. Who produced the bytes - [`openpgp`](demo-26-openpgp/README.md)

A checksum proves a dependency has not changed since you vetted it, not that what
you vetted was genuine. Name the OpenPGP key that signs a coordinate and the
build checks the signature published beside each artifact:

    @jenesis.signature OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 org.assertj/*

`openpgp` verifies a real dependency against the key AssertJ really signs with:
the right fingerprint passes, a rotated one is blocked even though the published
signature is perfectly valid, and a `signature-<name>.properties` file is the
shape a project uses to vet a key once and share it between modules. Strict
verification adds one more rule - every dependency needs a declared key:

    java -Djenesis.dependency.signature=strict build/jenesis/Make.java

The line carries no version, so vetting a key once covers every future release
from it, where a checksum covers exactly one file. `gpgv` on the `PATH` does the
checking.

## 20. Signing without keeping a key - [`sigstore`](demo-27-sigstore/README.md)

The same question answered without anyone holding a key: the signer authenticates
to an identity provider, a short-lived certificate names that identity, and the
signature is recorded in a public log. `@jenesis.signature` therefore takes a
second form that names an identity rather than a fingerprint:

    @jenesis.signature Sigstore/github.com/sigstore/protobuf-specs dev.sigstore/*

The path narrows an owner and a repository, and stops before the tag a release
was built from, which moves with every version - so one line covers every future
release. Declaring another repository blocks the build even though the bundle it
finds is genuine. Nothing is installed and no key is fetched.

## 21. Software bill of materials - [`sbom`](demo-28-sbom/README.md)

Every build emits a CycloneDX bill of materials. An optional `sbom.properties`
picks the format, and `-Djenesis.sbom.cyclonedx=false` turns it off without a
file:

    format=json      # or xml, or none

It lands three ways: embedded in the jar under `META-INF/sbom/`, collected by
`stage` into `target/stage/reports/sbom/`, and attached beside the POM as
`<artifact>-<version>-cyclonedx.json`, so `export` publishes it with the release.

## 22. Licenses and known vulnerabilities - [`compliance`](demo-29-compliance/README.md), [`vulnerabilities`](demo-30-vulnerabilities/README.md)

Two gates over the dependencies you ship, each turned on by a file in the
configuration directory and each writing a report under `reports/compliance/`.

`compliance` is the license gate, enabled by a `licensing.properties`. Each
declared license is normalized to an SPDX id and category and checked against
your policy:

    allowed=permissive
    unknown=fail
    override.maven/org.example/widget=Apache-2.0

A dependency with no recognized license fails by default, since no license means
all rights reserved. The demo ships a permissive dependency and a GPL one, so a
permissive-only policy passes the first and fails the build on the second.

`vulnerabilities` is the known-vulnerability gate, enabled by a
`vulnerability.properties`. It asks the public OSV database about the resolved
coordinates, with no account and no key:

    severity=critical
    warn=false

The demo ships a deliberately vulnerable `log4j-core` 2.14.1, so the build fails
on Log4Shell. The query runs only when the file is present, so a build never
reaches OSV unless you ask it to.

## 23. Code quality for Java - [`java-quality`](demo-31-java-quality/README.md)

`java-quality` turns a set of tools on without a build script: each runs because
its configuration file is there. A `checkstyle.xml` and a `pmd.xml` lint the
sources, a `spotbugs-exclude.xml` brings in SpotBugs over the compiled classes,
and a `javaformat.properties` selects `google-java-format`. Each tool resolves in
a group of its own, so its dependencies never mix with yours.

The linters report; the formatter fails the build when a source is not already
formatted, and one switch flips it to rewriting in place:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

## 24. The framework that runs the tests - [`test-framework`](demo-32-test-framework/README.md)

Which framework a module's tests are written against follows from what it
resolves - the Jupiter API marks the JUnit Platform, `junit` marks JUnit 4,
`org.testng` marks TestNG - and whatever that framework needs but the module
does not carry is resolved for it: the engine, the console runner, and the
vintage engine where a JUnit 4 jar shares the path.

The demo's test module requires one migration-support module and no engine at
all, yet its Jupiter test and its JUnit 4 test both run. Nothing is resolved
that the module already requires, so naming an engine yourself is what the build
uses.

Where a project would rather say it outright, a `test.properties` in the
module's configuration location names the framework:

    framework=junit-platform      # or junit4, or testng

The demo ships that file under a profile, so both paths stay runnable:

    java -Djenesis.make.profiles=explicit build/jenesis/Make.java

## 25. Coverage, selection, mutation and benchmarks - [`code-coverage`](demo-33-code-coverage/README.md), [`test-selection`](demo-34-test-selection/README.md), [`pitest`](demo-35-pitest/README.md), [`jmh`](demo-36-jmh/README.md)

`code-coverage` records which lines the tests exercise and renders an HTML and
XML report, enabled by a `jacoco.properties` file. A `graal.properties` file
enables a second recording in the same place: the GraalVM tracing agent, which
captures the reflection, resources and proxies the tests use, so an ahead-of-time
compiler can be told about them. Neither needs anything in the sources.

`test-selection` re-runs only the test classes a change can reach, which is worth
having in a watch loop and not in continuous integration:

    java -Djenesis.test.incremental -Djenesis.project.watch=true build/jenesis/Make.java

`pitest` measures what the tests actually check rather than what they touch: with
a `pitest.properties` in the module, PIT seeds faults into the code, re-runs the
tests against each one, and reports which survived. A surviving mutant is
behaviour no test pins down.

`jmh` builds and runs a benchmark. The harness is generated by an annotation
processor, `@jenesis.alias` gives `jmh-core` the module name it does not ship,
and `@jenesis.main` runs it:

    java build/jenesis/Execute.java

## 26. Guarding a published API - [`api-compatibility`](demo-37-api-compatibility/README.md)

An empty `japicmp.properties` makes the build compare the bytecode of the jar it
produced against the bytecode of the last release of the same coordinate - which
is what a caller actually linked against. With no `baseline` key the coordinate
is this module's own at `RELEASE`, so every build measures the change you are
about to publish against the release before it, with nothing to re-point by hand.
A `baseline=` key names another coordinate instead.

The check writes `reports/japicmp/japicmp-report.xml` and keeps the build green;
four `error-on-*` keys turn a finding into a failure:

    error-on-binary-incompatibility=true

The demo publishes its own previous release first, so it has something to compare
against. `-Djenesis.artifact.japicmp=false` is the opt-out.

## 27. Kotlin - [`kotlin`](demo-38-kotlin/README.md), [`kotlin-quality`](demo-39-kotlin-quality/README.md), [`kotlin-plugin`](demo-40-kotlin-plugin/README.md)

Other JVM languages need no configuration beyond their sources. `kotlin` is a
module that mixes Java and Kotlin, and exports a package holding only Kotlin. The
Kotlin compiler is resolved and pinned in a `kotlinc` group of its own, so it
never mixes with the project's own dependencies.

`kotlin-quality` infers detekt (`detekt.yml`) and ktlint (`.editorconfig`) from
their configuration files, with ktlint doubling as the formatter and sharing the
`-Djenesis.format.rewrite=true` switch.

`kotlin-plugin` adds a compiler plugin, declared with the compiler named first:

    @jenesis.plugin kotlinc maven/org.jetbrains.kotlin/kotlin-serialization-compiler-plugin

The kotlinx.serialization plugin then generates `Point.serializer()` for an
`@Serializable` data class; delete the line and the build fails.

## 28. Scala - [`scala`](demo-41-scala/README.md), [`scala-quality`](demo-42-scala-quality/README.md)

Scala works the same way: a module that mixes Java and Scala exports a pure-Scala
package, and the compiler is pinned in a `scalac` group. `scala-quality` infers
Scalastyle (`scalastyle-config.xml`) and scalafmt (`.scalafmt.conf`), with
scalafmt doubling as the formatter.

## 29. Groovy - [`groovy`](demo-43-groovy/README.md), [`groovy-quality`](demo-44-groovy-quality/README.md)

Groovy follows the same pattern with one restriction: an exported package needs
at least one Java type in it, which the demo's own README explains. The compiler
is pinned in a `groovyc` group. `groovy-quality` lints with CodeNarc
(`codenarc.xml`); there is no inferred Groovy formatter.

## 30. Build profiles - [`profiles`](demo-45-profiles/README.md)

A profile is a `*.properties` file at the project root that switches a set of
properties on together:

    java -Djenesis.make.profiles=release build/jenesis/Make.java

The demo's `release` profile turns on source jars and chains to a `supply-chain`
profile that enforces strict pinning, so one switch produces a hardened
publication build. Profiles compose, because a profile may name further profiles;
everything a profile sets is a default, so the command line always wins. The
always-loaded base is an optional `jenesis.properties` beside it.

## 31. A shared build cache - [`build-cache`](demo-46-build-cache/README.md)

Every build is already incremental under `target/`. A shared cache outside
`target/` goes one step further and hands a step its finished output instead of
running it, so a colleague, another checkout or a CI workspace reuses what was
built once:

    java -Djenesis.project.cache build/jenesis/Make.java            # a project-local folder
    java -Djenesis.cache.uri=file:///srv/jenesis build/jenesis/Make.java
    java -Djenesis.cache.uri=https://cache.example.com build/jenesis/Make.java

The demo makes the effect visible by bootstrapping the cache and then forcing a
full rebuild, which is served from it:

    java -Djenesis.project.cache -Djenesis.executor.rebuild=true build/jenesis/Make.java

An optional `cache.properties` at the cache root caps and prunes it (`steps`,
`versions`, `size` with `lru` eviction, `compressed`, and `read`/`write`
toggles - `write=false` for a read-only consumer).

## 32. Confining the build with Docker - [`docker-isolation`](demo-47-docker-isolation/README.md)

A build runs your tests and whatever they pull in, and the artifact it produces
has a `main` that runs later - all with your rights. This demo's test and its
`main` both read a secret file and a secret environment variable and then
overwrite the file. Run on the host, both reach the secrets; run confined,
neither does:

    java -Djenesis.project.docker=true build/jenesis/Make.java     # the build, tests included
    java -Djenesis.execute.docker=true build/jenesis/Execute.java  # the program

The local repositories are mounted read-only, so dependencies have to be cached
already and `export` does not work inside the container. It needs a Docker
daemon, so it is a local exercise.

## 33. Attaching Java agents - [`agents`](demo-48-agents/README.md)

Some libraries have to run as a `-javaagent` rather than be called through an
API. `@jenesis.attach` declares one next to the execution it belongs to:

    @jenesis.attach org.mockito
    @jenesis.attach io.opentelemetry.javaagent/opentelemetry-javaagent

The first sits beside the `@jenesis.test` tag, where Mockito is both a test
dependency and an agent - one resolved file in both roles. The second sits beside
`@jenesis.main`: the OpenTelemetry agent is required by nothing and compiled
against nothing, and is attached only to the run of the entry point. The token
follows the pin grammar without a version, anything after it is the agent's own
option string, and the jar must carry a `Premain-Class`. A `pom.xml` project
declares the same in a `<!--jenesis.attach ... -->` comment block.

## 34. Granting native access - [`native-access`](demo-49-native-access/README.md)

Calling native code through the foreign function API or JNI needs
`--enable-native-access`, and whether a program calls it is decided by the
module that uses the native API, not by the library that offers it. That module
names the module needing access, and the name is recorded in its jar:

    @jenesis.native demo.natives.text

A declaration grants access only to the runs of the module that makes it - its
tests, `Execute` and what it packages - and is never inherited, so an
application that runs such a module names the same module again.
`jenesis.dependency.native=warn` reports a name the running module does not
grant, and `strict` fails the build on it. A `pom.xml` project declares the same
in a `<!--jenesis.native ... -->` comment block.

## 35. Native access in a layer - [`native-access-layer`](demo-50-native-access-layer/README.md)

A library that keeps a module in a layer, as in `module-layers`, hides it from
the application that uses the library. When that module needs native access, the
library passes its own native access on to it:

    @jenesis.layer strings native demo.strings.text

and the application grants access to the library alone, as it would a library
that had shaded the module. `jenesis.dependency.native` asks for the library,
never for the module in its layer.

## 36. Customizing the build - [`custom-assembler`](demo-51-custom-assembler/README.md), [`custom-jmod`](demo-52-custom-jmod/README.md)

The next demos open up the template. `custom-assembler` keeps the standard flow
but names a customizer, a function from the stock assembler to the one to build
with, which wraps it so every module's sources pass through a preprocessing step
before compile, jar and test run unchanged:

    jenesis.project.customizer=build.custom.Preprocessing

Any step that produces a `sources/` tree fits the same shape: template expansion,
code generation, license headers.

`custom-jmod` contributes an extra input rather than replacing anything. It turns
the stock `jmod`, `jlink` and `jpackage` steps on with a `packaging.properties`
and adds a step that emits a configuration directory, which travels with the jmod
into the linked runtime and into the packaged application, where the program
reads it back from its own `<java.home>/conf/` - content a jar cannot carry.

## 37. Build logic as a module - [`internal-module`](demo-53-internal-module/README.md), [`external-module`](demo-54-external-module/README.md)

`internal-module` does the same preprocessing, but from a build module in its own
`plugin/` project, compiled from local source and loaded as a service - it even
has a dependency of its own. A `.jenesis.skip` marker keeps the plugin project
out of the host project's module discovery.

`external-module` is identical except that the same build module is resolved as a
published coordinate instead of compiled from source. Build logic is just another
module: written inline, loaded from source, or consumed as a versioned artifact.

## 38. Driving the build without `Project` - [`custom-maven`](demo-55-custom-maven/README.md), [`custom-modular`](demo-56-custom-modular/README.md)

These two drive a multi-module build from a hand-written `build/Demo.java`, with
no layout and no goals, while reusing the whole standard toolchain:

    Environment environment = new Environment(Make.settings(Path.of(".")).keys());
    BuildExecutor root = BuildExecutor.of(Path.of("target"));
    root.addModule("maven", MavenProject.make(environment, Path.of("."), assembler));
    root.execute(args);

`custom-maven` does it for Maven modules, `custom-modular` for
`module-info.java` modules. The three-argument `make` discovers the modules and
supplies the defaults a normal build would configure.

## 39. Dropping the template entirely - [`custom-build`](demo-57-custom-build/README.md)

The last of the customization demos removes the template altogether and wires the
build by hand in one `main` method - no `pom.xml`, no `module-info.java`, just
the steps you add, including one that generates a source the compiler then picks
up. This is the escape hatch when a build needs something the templates do not
model:

    java build/Demo.java
    java -cp target/jar/output/artifacts/classes.jar sample.Sample

## 40. Running a build from another program - [`tools-api`](demo-58-tools-api/README.md)

A build does not have to be a process of its own. `build.jenesis` publishes three
`java.util.spi.ToolProvider` tools, named after the commands they answer to, so a
program that has the module on its path can run one in its own JVM:

    ToolProvider.findFirst("jenesis-make").orElseThrow()
            .run(out, err, "-Djenesis.project.version=1.0.0", "build");

The leading `-Djenesis.*` arguments configure that run and that run alone, the rest
is what the command line would take, and everything the tool prints arrives on the
writers you hand it. `jenesis-make` builds, `jenesis-exec` builds and then runs what
it built, and `jpx` runs a published program. A whole command line can come from a
file instead: `@<file>` stands for the arguments it holds, as it does for `javac` and
for the commands here. The demo builds the same project twice in one JVM with a
different version each time, runs the result, asks `jpx` for its help, and shows that
the JVM itself is left holding neither version.

Three settings cannot be honoured in-process, because they replace the process a
build runs in - naming another JDK with `jenesis.toolchain.version`, or a container
with `jenesis.project.docker` or `jenesis.execute.docker` - and each is refused by
name rather than ignored. Reach for the `jenesis` command there.

## 41. Signing the jar you publish - [`code-signing`](demo-59-code-signing/README.md)

Where `openpgp` and `sigstore` ask who produced the dependencies coming in,
`code-signing` answers the same question about what goes out, and answers it
inside the archive: `jarsigner` writes digests and a signature block into the jar
itself, which a JVM checks as it loads the classes.

Signing has no configuration file; it is named by `jenesis.jarsigner.*`
properties, which split between the project and the machine. The alias is usually
the project's own, while the store and its password differ between a laptop, a
release machine and a CI runner, so commit what the project knows - in a release
profile or in `~/.jenesis/jenesis.properties` - and pass the rest with `-D`:

    jenesis.jarsigner.alias=release
    jenesis.jarsigner.keystore=/keys/release.p12
    jenesis.jarsigner.storepass=env KEYSTORE_PASSWORD

Naming any of them says the project signs, so leaving the store, the alias or the
password location unnamed fails the build rather than shipping an unsigned jar.
The password is never a value: `storepass` takes `env <variable>` or
`file <path>`, and anything else is refused. The signed jar replaces the unsigned
one before the inventory, the staged repositories or a publication ever see it.

## 42. Requiring a build from another local project - [`export`](demo-60-export/README.md)

Before anything is published, `export` installs what `stage` laid out into this
machine's local repositories: the modular tree into `~/.jenesis`, the Maven tree
into `~/.m2/repository`. The demo exports one project and requires it from a
second one by module name alone, and points both local repositories at a
temporary folder with `jenesis.module.local` and `jenesis.maven.local` so your own
stay untouched. Because the Maven tree carries a generated POM, Maven, Gradle and
anything else that reads the local Maven repository consume the export too.

A project that sets no version exports an unversioned module, whose POM carries
`0-SNAPSHOT`. That serves two projects changing together, but it names no build in
particular, so strict pinning refuses it; exporting with `jenesis.project.version`
and pinning that version fixes the dependency. A consumer resolves again when what
it declares changes, not when a new export appears, so `jenesis.executor.rebuild`
is how it takes one.

## 43. Publishing to Maven Central - [`publishing`](demo-61-publishing/README.md)

Publishing is two jobs - produce a correct bundle and upload it - and Jenesis
does the first. A `module-info.java` plus a `project.properties` supply the
metadata Central demands (`name`, `description`, `url`, licenses, developers,
SCM), and `stage` writes the complete upload-ready bundle under
`target/stage/maven/output/`: the jar, the generated POM, `-sources.jar` and
`-javadoc.jar`. The demo then resolves that coordinate straight back out of the
staged tree to prove the bundle is complete, all offline.

The remote upload and the GPG signing are left to a release tool pointed at the
staged tree - which is how Jenesis itself releases - so the demo needs no
credentials, no key and no network. A `jreleaser.yml` at the project root adds that step to the `release`
goal, a rehearsal unless told otherwise.

## 44. Your own modules from your own Maven repository - [`module-convention`](demo-62-module-convention/README.md)

`publishing` showed the coordinate a module is published under: the groupId from
the first two dotted segments of its name, the artifactId from the whole name.
`module-convention` reads that convention in the other direction, so a
`requires demo.convention.greeter` is served by
`demo.convention:demo.convention.greeter` out of whatever Maven repository you
deploy to - a team on its own Nexus, Artifactory or GitHub Packages keeps no
module registry and no coordinate mapping.

The demo publishes a library into a Maven repository under `target/` and builds
the consumer against it. A repository like that is named as the project's
`module` repository, in code or in the module repository chain, and how deep the
group reaches into the name is configuration too:

    jenesis.maven.segments=3

## 45. The same bytes on every machine - [`reproducible`](demo-63-reproducible/README.md)

What you publish, anyone holding the sources should be able to build again and
get the same bytes. `reproducible` turns that into a check: it builds a module
and compares the jar's `SHA-256` with a digest recorded in the demo. CI runs it
on Linux, macOS and Windows, each on whatever update of JDK 25 the runner
provides, so a match everywhere shows that nothing about the machine reaches the
jar.

Nothing has to be configured for it. The one setting involved is the timestamp
every archive entry records, which you can point at something meaningful such as
the time of the release commit - and the recorded digest moves with it:

    jenesis.archive.timestamp=2026-09-01T12:00:00Z

## 46. The JDK a build runs on - [`toolchain`](demo-64-toolchain/README.md)

`reproducible` promises the same bytes for the same JDK; `toolchain` makes the
JDK part of the project. Its `jenesis.properties` names one:

    jenesis.toolchain.version=25

`Make` and `Execute` check the JVM they were started on before anything else.
When it matches, nothing changes; when it does not, they find a matching JDK
among those already installed and run themselves again on it, with the same
selectors and `-Djenesis.*` properties. A version is matched as a prefix plus
words the JDK has to answer to, taken from the vendor and version it records:
`25-temurin`, `25-zulu`, `26-ea`. No JDK is ever installed for you, and where to
look is yours alone to say - `jenesis.toolchain.searchpath` defaults to the
operating system's usual JDK folders and is accepted only from the command line
or `~/.jenesis/jenesis.properties`, never from a project's own files.

## 47. Ahead-of-time native image - [`native-image`](demo-65-native-image/README.md)

Where `jpackage` bundles your bytecode with a trimmed JVM, GraalVM
`native-image` compiles the program and the runtime it touches into a single
machine-code binary. It is one key in `packaging.properties`:

    native=true

`native-image` is located through `GRAALVM_HOME`, the running JDK, then the
`PATH`, so the build runs on a GraalVM JDK or with `GRAALVM_HOME` pointed at one.
Because the compiler only sees code reached statically, anything dynamic needs
reachability metadata: the demo's `graal.properties` records it during the test
run and the image build picks it up, so one build produces a working binary with
nothing committed in between. Committing the metadata under
`sources/META-INF/native-image/` stays the way to vet exactly what a published
artifact bakes in. `stage` collects the executable into `stage/native/output/`.

Native image is an alternative to `jpackage`, not a successor: `jpackage` for a
faithful bundle of the JVM you tested against, native image when startup and
footprint dominate. It needs GraalVM, so it is a local exercise.

## 48. Running a released program - [`jpx`](demo-66-jpx/README.md)

Every demo so far built something. `jpx` builds nothing: it resolves a published
module or Maven artifact, installs its runtime closure once under
`~/.jenesis/jpx/`, and runs its entry point:

    jpx org.junit.platform.console@6.1.3 --version
    jpx org.junit.platform:junit-platform-console@6.1.3 --version

The first names a Java module, the second a Maven coordinate; a module name can
never contain a colon, so the grammar `<name>[@<version>][/<main-class>]` keeps
them apart with no flag. A module name is run on the module path, a coordinate on
the class path.

Naming a version and a digest makes the command reproducible, which is what makes
`jpx` usable in a pipeline rather than only at a prompt:

    jpx --hash=ed5600ef861c7e86cab68c134c6ca0cf org.junit.platform.console@6.1.3 --version

The digest covers every jar of the installation, so a jar swapped underneath an
existing installation is caught as readily as a tampered download. It is matched
as a prefix, with 32 hex characters the shortest accepted. Resolution reaches the
default repositories through your local `~/.jenesis/` and `~/.m2/`, and either can
be pointed at a mirror with `JENESIS_REPOSITORY_URI` and `MAVEN_REPOSITORY_URI`.

The demo runs those same commands from `java build/Demo.java`, with the
installation directed at its own `target/` so your home directory is left alone.

Cross-cutting concepts
----------------------

A few things recur across the tour and are worth collecting in one place.

**Layouts.** The descriptor at the project root decides the build shape. A
`pom.xml` gives a classic jar plus that POM; a `module-info.java` with no POM
gives a modular jar plus a generated POM; `jenesis.project.layout=modular`
selects the pure modular layout, which resolves by module name and emits no POM.
A subtree rooted at a `.jenesis.skip` marker is left out of discovery.

**Goals.** `build` (the default) compiles, jars and tests; `pin` records resolved
versions and checksums back into the sources; `stage` lays the artifacts out as
local Maven and module repositories; `export` publishes them; `dependencies`
prints the resolved graph; `ide` writes IntelliJ, Eclipse and VS Code project
files; `configuration` prints every setting with the value in force.
`java build/jenesis/Execute.java` builds and then runs the entry point, with
`-Djenesis.execute.module` and `-Djenesis.execute.mainClass` picking which one in
a multi-module build.

**Configuration locations.** A tool is switched on by its configuration file, and
the build looks for it in a module's `META-INF/build.jenesis/` folder, its
`build.jenesis/` folder, and the project-wide configuration directory - the
project root by default. A profile's copy of a file overrides a more general one,
and an empty file switches the tool off.

**Pins.** A pin lives in the sources: in a POM's `<dependencyManagement>` with
its checksum comment, or as a `@jenesis.pin <token> <version> [<algorithm>/<hex>]`
tag on a module declaration. The token reads by slash count: a bare name is a Java
module, one slash is a Maven `<groupId>/<artifactId>`, and more is the explicit
`<group>/<repository>/<coordinate>`. Every download is verified against its pin,
and `-Djenesis.dependency.pin=strict` refuses a dependency that has none. Every
demo here is committed pinned.

**Groups.** A group is the outermost isolation axis of a dependency. Your own
dependencies live in the `main` group; a resolved tool lives in a group named
after it (`kotlinc`, `scalac`, `groovyc`, `antlr`, `jacoco`, `japicmp`), so a
tool's closure never mixes with the program's. `pin` writes each group
separately.
