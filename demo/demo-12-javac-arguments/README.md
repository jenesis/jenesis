Compiler arguments demo
=======================

Hand an external build tool an extra command-line argument without a build
script, by dropping a `process-<command>.properties` file in a configuration
location. This module needs one: its entry point reflects on a method's
parameter and asserts the name survived compilation, which only holds when
`javac` is given `-parameters` - a flag Jenesis does not pass by default.

The `process-javac.properties` in the `build.jenesis/` configuration directory supplies it:

    -parameters=

Run it
------

From this directory:

    java build/jenesis/Execute.java

`Execute` builds the module and launches its `@jenesis.main` entry point, which
reflects on `greet(String recipient)` and prints:

    Compiled with -parameters: greet's parameter is named 'recipient'

Delete `process-javac.properties` (or the `-parameters` line) and rebuild: the
class still compiles, but the parameter name is erased to `arg0`,
`Parameter.isNamePresent()` returns `false`, and the run fails with
`Method parameter names were not compiled in`. The file is what turns the flag on.

How it works
------------

A configuration location (`build.jenesis/` under the project root by default, or a profile subfolder)
may carry a `process-<command>.properties` file named after any external tool the
build runs: `javac`, `kotlinc`, `scalac`, `jar`, `jmod`, `jlink`, `jpackage`, or
`native-image`. `process-java.properties` applies to every forked `java` process,
while `process-test.properties` targets only the forked test JVM, merged over the
`java` file with test keys winning. Each key is a flag and its value the flag's argument; an empty
value (as here) emits a bare flag, and a value with embedded newlines repeats the
flag once per line. A value `@<key>` or `@<key>/<default>` is the setting
`jenesis.variable.<key>`, as the environment file of `demo-04` shows, so
`-Xmaxwarns=@warnings/100` takes `-Djenesis.variable.warnings=500` from the
command line and reruns `javac` when it changes.

The default assembler's `prepare` step reads the file - resolving each command's
file by **first match** across the configuration locations, so a profile's file,
or an empty one, overrides or switches off a more general one - and merges it into
the `process/<command>.properties` the build already feeds to that tool step,
where a configuration key overrides a build-generated one of the same name. Here
`javac` receives the build's own `--release 25` (from `@jenesis.release`) and the
configured `-parameters` together.

The release is the one flag the file cannot set: the module's declaration names it
for every tool that reads it, so a `--release` (or `--enable-preview`) line fails the
build and names where to declare it instead - `@jenesis.release` in `module-info.java`,
or `maven.compiler.release` and, for the tests, `maven.compiler.testRelease` in a
`pom.xml`.

This is the general, profile-aware way to give a tool an argument the inferred
build does not set for you - for instance compiling a single module with extra
`javac` flags.

Attributes of the jar's manifest
--------------------------------

The jar's manifest is the one argument of `jar` that a process file cannot set:
the build writes the manifest itself, so `--manifest` in `process-jar.properties`
is refused. A `META-INF/MANIFEST.MF` among the module's resources is the basis of
that manifest instead - `sources/META-INF/MANIFEST.MF` here, or
`src/main/resources/META-INF/MANIFEST.MF` in a Maven project:

    Manifest-Version: 1.0
    Implementation-Title: Compiler arguments demo
    Implementation-Vendor: Example Corp

After a build, the jar's `META-INF/MANIFEST.MF` holds these lines and, merged over
them, the ones the build writes, such as the location of the bill of materials.
This is where the attributes of Maven's `<manifestEntries>` and
`addDefaultImplementationEntries`, or Gradle's `jar.manifest.attributes`, go - an
`Automatic-Module-Name` for a library without a `module-info` among them. Values are
written as they are, since resources are not filtered, and a `Created-By` of the
file is kept. An attribute the build writes with another value, such as
`Multi-Release`, fails the build rather than being replaced.

Layout
------

    demo/demo-12-javac-arguments
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- build.jenesis
    |   `-- process-javac.properties
    |                              -parameters, kept out of the default javac flags
    `-- sources
        |-- META-INF
        |   `-- MANIFEST.MF        the basis of the jar's manifest
        |-- module-info.java       @jenesis.release 25, @jenesis.main sample.Sample
        `-- sample
            `-- Sample.java        reflects on its own method parameter name
