Kotlin demo
===========

Build a project that mixes Java and Kotlin in one module of the Java module
system, and export a package that holds only Kotlin. You point Jenesis at the
project, it compiles the `.java` and `.kt` sources
together, and emits a modular jar alongside a generated POM - there is no build
script to write.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis auto-detects the MODULAR_TO_MAVEN layout (a `module-info.java`, no
`pom.xml`), resolves the Kotlin compiler, compiles the module, and emits a
modular jar alongside a generated POM.

Kotlin is an official plugin of the hook point `language`. The build resolves it
by itself, as `module/build.jenesis.plugin.kotlin`, in every module that holds a
`.kt` source, with no line in `jenesis.plugins.properties` and no other
configuration.

Layout
------

The module declares its dependency in `module-info.java` and lays the mixed
sources out under one package:

    demo/demo-46-kotlin
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- sources
    |   |-- module-info.java       requires kotlin.stdlib; exports sample; exports sample.pure
    |   |-- messages.properties    a root resource, read by Greeter at run time
    |   |-- META-INF/build.jenesis
    |   |   `-- dokka.properties  configures Dokka, when the build documents the module
    |   `-- sample
    |       |-- Greeter.java       a Java type in the exported 'sample' package; loads the resource
    |       |-- Sample.kt          Kotlin that calls Greeter
    |       `-- pure
    |           `-- Pure.kt        a pure-Kotlin package, exported with no Java type
    `-- test
        |-- module-info.java       module sample.kotlin.test (@jenesis.test sample.kotlin)
        |-- sampletest/SampleTest.java   asserts the greeting comes from the resource
        `-- sampletest/ShoutTest.kt      calls an internal function of sample.kotlin

How Jenesis builds it
---------------------

The Kotlin plugin finds the `.kt` sources and drives the Kotlin compiler as part
of the standard build, with `javac` participating for `module-info.java` and the
companion `.java` file. The module exports both a mixed package and a package
that holds only Kotlin.

Compile order and exporting a Kotlin package
--------------------------------------------

The plugin compiles Kotlin first, then `javac`. `kotlinc` reads the
`.java` sources for symbol resolution, so `Sample.kt` can call `Greeter`, but it
emits only Kotlin classes; `javac` then compiles `Greeter.java` and
`module-info.java`. When `javac` validates the `exports` directives, it sees the
already-compiled Kotlin classes through `--patch-module`, so a package that
holds only Kotlin classes can be exported.

That is what `sample.pure` shows: it contains a single Kotlin class and no Java
type, yet `module-info.java` exports it. The order is what makes this work -
`javac` validates `exports <package>` against the classes already in the module,
so the Kotlin classes must exist first. `kotlinc` and `scalac` can read `.java`
as source, so their plugins run ahead of `javac`; `groovyc` cannot, which is why a
Groovy-only exported package still needs a Java type.

Packaging a resource in a mixed-language module
-----------------------------------------------

`messages.properties` sits at the source root, outside any package. In a
single-language module the one compiler copies non-source files into the jar
alongside the classes; here two compilers run, so neither owns that step and a
dedicated resource step copies every non-source file instead - `messages.properties`
lands at the jar root either way. `Greeter.java` reads it back with
`Greeter.class.getResourceAsStream("/messages.properties")` and `Sample.kt` returns
it with a Kotlin suffix, so one greeting crosses Java -> resource -> Kotlin. The
`sample.kotlin.test` module asserts the result, so the build proves the resource was
packaged into the mixed jar and is loadable. (Files under `META-INF/` are always
copied this way too, except the `META-INF/versions/` multi-release overlay, which is
compiled - see `../demo-12-java-multi-release`.)

Tests that see internal declarations
------------------------------------

`Sample.kt` declares `internal fun shout`, visible within its own module alone, and
`ShoutTest.kt` in the test module calls it. A test module is the tested module's
friend, as Gradle and the Kotlin Maven plugin make it: the build hands Kotlin the
tested module's jar as a friend path, so its internal declarations compile in the
tests and nowhere else.

A multi-release overlay in Kotlin
---------------------------------

The module compiles for Java 21, as `@jenesis.release 21` declares, and holds a
second `Pure.kt` below `sources/META-INF/versions/25/`, exactly where
`java-multi-release` keeps a newer Java class. Kotlin compiles it for Java 25 into
the jar's `META-INF/versions/25/`, beside the Java 21 class of the same name, and
the jar declares `Multi-Release: true`:

    sample/pure/Pure.class                          Java 21
    META-INF/versions/25/sample/pure/Pure.class     Java 25

A JVM of release 25 or later loads the overlay, an older one the base class, which
the test module asserts against the release it runs on. A Java source in the same
folder compiles against the Kotlin classes of its release, and Dokka documents the
base sources alone.

Documenting the Kotlin sources
------------------------------

With `-Djenesis.project.documentation=true`, Jenesis documents the module and
archives the documentation as its javadoc jar. The Kotlin plugin documents the
Kotlin sources with Dokka, resolved and pinned in a `kotlin:dokka` group of its
own, in a `kotlin/` folder beside the Javadoc of the Java sources:

    java -Djenesis.project.documentation=true build/jenesis/Make.java

`sources/META-INF/build.jenesis/dokka.properties` configures Dokka with the options
the Dokka Gradle plugin and the `dokka-maven-plugin` take:

    module-name=Kotlin demo
    source-link=https://github.com/jenesis/jenesis/tree/main/demo/demo-46-kotlin/sources
    footer-message=Documented by Dokka, built by Jenesis

The pages name the module `Kotlin demo`, link every declaration to its line on
GitHub, and carry the footer. The other values are `documented-visibilities`,
`suppress-obvious-functions`, `suppress-inherited-members`, `skip-empty-packages`,
`skip-deprecated`, `report-undocumented`, `suppressed-packages`, `fail-on-warning`,
`jdk-version`, `source-link-suffix`, `external-link.<name>` with its
`external-link.<name>.package-list`, and `format=html|javadoc`; an
unknown value fails the build, and so does Dokka when it fails, as javadoc does.

Compiling incrementally
-----------------------

With `-Djenesis.kotlin.incremental=true`, Kotlin compiles incrementally, as
Gradle compiles it by default:

    java -Djenesis.kotlin.incremental=true build/jenesis/Make.java

The first build compiles every source. After an edit, the next build recompiles
the sources the change reaches and keeps every other class: a changed function body
recompiles its own file, and a changed signature also the files that use it. A
module that depends on this one recompiles only what uses a changed declaration.
The compiler runs in a JVM of its own that the build keeps warm, so it stays loaded
from one module to the next, and from one build to the next under the build daemon.
A `-J` line of `process-kotlinc.properties` configures that JVM.

This needs Kotlin 2.3 or newer, whose compiler is driven through the Kotlin Build
Tools API, `org.jetbrains.kotlin:kotlin-build-tools-impl`. The module pins it in the
`kotlin:tool` group beside the compiler, at the compiler's version.

Pinning
-------

The module's closure is pinned with `@jenesis.pin` tags on the module
declaration: the module's own dependencies in the default `main` group (the Java
module `kotlin.stdlib`, plus the non-modular transitives as
`org.jetbrains.kotlin/kotlin-stdlib` and `org.jetbrains/annotations`) and the
Kotlin compiler toolchain in the tool group of the plugin, `kotlin:tool` (each
`kotlin:tool/maven/...` coordinate with a version and SHA-256 checksum, the Build Tools
API included), and Dokka in its `kotlin:dokka` group. Because the groups are separate, the running compiler is
locked independently of the `kotlin.stdlib` the module ships against. Run
`java build/jenesis/Make.java pin` to record or refresh the tags; re-running
leaves the declaration unchanged.
