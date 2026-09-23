Native image (GraalVM) demo
============================

Compile a modular Java application ahead of time into a single standalone native
executable with GraalVM `native-image` - a binary that starts in milliseconds and
carries no Java runtime, because the runtime it needs is linked into the binary
itself. It is the ahead-of-time counterpart of `../demo-07-java-modular-executable`:
where that demo packages a `jpackage` app-image (your jar plus a trimmed JVM that
runs it), this one produces a self-contained machine-code program with no JVM at
all.

The demo deliberately reaches for **reflection**, the thing native-image's
closed-world analysis cannot see, so it shows the whole loop in a single build: a
test captures the reachability metadata, and the native build picks it up directly.
`Sample` loads its greeter by a name assembled at run time -
`Class.forName("sample." + name)` - and invokes it reflectively, so neither the class
nor its methods are statically reachable.

This demo needs the GraalVM `native-image` tool (and, for the capture, the GraalVM
tracing agent) on the build's toolchain. The plain JDK used for the other demos
does not carry them, so CI runs this one in its own job on a GraalVM distribution
rather than in the demo matrix; locally, run it with a GraalVM JDK or point
`GRAALVM_HOME` at one, as below.

Build the native image
----------------------

A single build captures the metadata and compiles the image in one pass. The demo
ships a `graal.properties` file, whose presence attaches GraalVM's tracing agent to
the test run, and a `packaging.properties` with `native=true` in the configuration
location, which the native compilation reads.
`native-image` is located the same way every external tool is - `GRAALVM_HOME` first,
then the running JDK's own `bin/` (`java.home`), then `PATH` - so either run the build
with a GraalVM JDK:

    ~/.sdkman/candidates/java/25.0.3-graal/bin/java build/jenesis/Make.java stage

or keep your usual JDK 25 and point `GRAALVM_HOME` at a GraalVM install (here one
managed by [SDKMAN](https://sdkman.io/), `sdk install java 25.0.3-graal`):

    GRAALVM_HOME=~/.sdkman/candidates/java/25.0.3-graal java build/jenesis/Make.java stage

The build compiles the modules, runs the test under the agent, then runs
`native-image` over the produced module path. The image build is the slow step (a
minute or two - it analyses the whole reachable program), after which the `stage`
goal collects the standalone binary into its canonical target directory:

    target/stage/native/output/demo.graal.image

Run it directly - there is no `java` in the command, because there is no JVM:

    target/stage/native/output/demo.graal.image            # Hello, world, from a native binary built by Jenesis (reflectively)!
    target/stage/native/output/demo.graal.image Ada        # Hello, Ada, from a native binary built by Jenesis (reflectively)!

The result is a ~15 MB ELF executable (`.exe` on Windows, a Mach-O binary on macOS)
that links `java.base` statically and starts without a runtime. The greeting comes
back through reflection - which only works because the test capture told native-image
to keep `sample.Greeter`. Keep `native=true` in `packaging.properties` but suppress the
capture with `-Djenesis.observe.native=false` (so nothing records the reflection) and
the binary fails at run time with `ClassNotFoundException: sample.Greeter`: the closed-world
analysis dropped the class it never saw referenced.

Where the metadata comes from
-----------------------------

A `graal.properties` file runs GraalVM's tracing agent during the test run, the
same way `../demo-33-code-coverage` runs JaCoCo. The `demo.graal.image.test`
module exercises exactly the reflective call `Sample` makes, so the agent records
that access, and the image built afterwards is handed what the test recorded -
each image its own test's metadata, in one build, with nothing committed in
between.

For review, the capture is readable in the test module's build output:

    target/build/modules/compose/module/<test-module>/produce/assemble/observed/native-image/report/output/nativeimage/reachability-metadata.json

which for this demo holds exactly the `sample.Greeter` constructor and `greet`
method. The committed-metadata route still exists for published artifacts: drop a
file under `sources/META-INF/native-image/` and it is copied verbatim into the jar
(files under `META-INF/` are resources), where `native-image` - and a plain JVM, and
any other tool that honours `META-INF/native-image/` - discovers it. This demo needs
no such file; the single build feeds the metadata straight through.

How the image is selected
-------------------------

Native compilation is opt-in through a single boolean `packaging.properties` key,
`native=true`. When it is set, an image is built for every module that declares a
main class with `@jenesis.main` - the same field `jpackage` reads - so the test
module produces none. The module path and the entry point come from the build
itself, the test-captured configuration directory is added, and the command run
is:

    native-image --no-fallback -H:ConfigurationFileDirectories=<captured> -o <name> --module-path <jars> --module <module>/<main-class>

`stage` collects the produced binary into `stage/native/output/`, beside
`stage/packages` for a `jpackage` image and `stage/runtime` for a `jlink` runtime.

Layout
------

    demo/demo-62-native-image
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- graal.properties     marker file; presence enables the GraalVM tracing agent
    |-- sources/
    |   |-- module-info.java     module demo.graal.image (@jenesis.main sample.Sample)
    |   `-- sample/
    |       |-- Sample.java       main; loads the greeter reflectively by a run-time name
    |       `-- Greeter.java      the reflective target (reached only via Class.forName)
    `-- test/
        |-- module-info.java     module demo.graal.image.test (@jenesis.test demo.graal.image)
        `-- imagetest/SampleTest.java   exercises the reflection so the agent records it

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout, exactly as `../demo-02-java-modular` does. The module is
named `demo.graal.image` rather than `demo.native.image` because `native` is a Java
reserved word and cannot be a module-name segment. There is no committed
`META-INF/native-image/` - the metadata is captured and consumed within the build.

native-image vs. jpackage
-------------------------

Both `../demo-07-java-modular-executable` (jpackage) and this demo turn a modular
app into something a user runs without installing a JDK, but they differ in kind:

- **jpackage** ships your bytecode plus a `jlink`-trimmed JVM. Startup is normal
  JVM startup; the program is the same bytecode, just bundled. The image is tens of
  megabytes (the JVM dominates) and needs no extra build tooling beyond the JDK.
- **native-image** compiles the program *and* the runtime it touches into one
  machine-code binary ahead of time. Startup is near-instant and the binary is
  small, but it needs GraalVM at build time, a slow closed-world compile, and
  reachability metadata for anything dynamic - the loop this demo walks through.

So the two are alternatives, not a progression: pick jpackage for a faithful,
no-extra-tooling bundle of the very JVM you tested against, and native-image when
startup latency and footprint matter more than build simplicity.
