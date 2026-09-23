Bundle demo
===========

Ship a modular Java app as a single `bundle.zip`: its runtime launch closure (the
app jar plus its dependencies) in one `jars/` store, with an argument file that *is* the
launch - a form every JVM already understands. It is
the lightweight counterpart of the jpackage app-image in
`../demo-07-java-modular-executable`: where jpackage bundles a `jlink`-trimmed Java
runtime into a self-contained launcher, the bundle carries *only your jars*, meant to
be dropped onto a stock JRE base image (`eclipse-temurin:25-jre`) that supplies the
JVM. No native tooling, no bundled runtime, no launch script generated.

Run it
------

From this directory, pass the arguments you want the app to receive:

    java build/Demo.java Ada Lovelace

`Demo.java` runs the build with the stock
`new Project<>(Path.of("."), new InferredMultiProjectAssembler())` - the bundle target is
selected by the committed `packaging.properties` at this demo's root, which sets
`bundle=true` - then unpacks the produced `bundle.zip` and launches the app out of it
on this JDK's own `java`, exactly as a JRE-based deployment would. It prints:

    Hello, Ada Lovelace, from a Jenesis bundle.zip on a stock JRE!

(Because no SLF4J backend is bundled, SLF4J prints a one-time "no providers" notice
and uses a no-op logger; `slf4j-api` is still part of the closure on the module
path.) With no arguments it greets `world`.

What the bundle contains
------------------------

The `bundle` step writes one `bundle.zip` per module that declares a main class:

    bundle.zip
    |-- application.unix.args      the launch, as a Java argument file
    |-- application.windows.args   the same launch, with Windows path separators
    `-- jars/                      every jar of the closure, stored once

It carries exactly the runtime closure the `Execute` launcher would run, and the
descriptor is not a description of the launch but the launch itself:

    "--module-path"
    "jars/classes.jar:jars/org.slf4j-2.0.16.jar"
    "--module"
    "demo.bundle/sample.Sample"

So a deployment runs it with no reader and no parser of its own:

    cd <unpacked> && java @application.unix.args

Every path is spelled out rather than handed over as a folder, so a jar is read because
the argument file names it and never because of where it sits - and because every path
lives in a file rather than on the command line, no closure is too large to launch. The
graph's own options are in there too: an automatic module or a class-path jar adds
`--add-modules ALL-MODULE-PATH,ALL-DEFAULT`, which roots the whole module path and the
platform modules such a jar expects. This demo's closure is `demo.bundle` + `org.slf4j`,
both explicit modules, so those two lines are absent.

The paths inside are relative to the unpacked folder, which is why `Demo.java` starts the
process there - and why the `Dockerfile` below sets `WORKDIR`.

There are two files because the path separator is the one part of a launch that cannot be
written down until you know where it runs, and a bundle is built once and unpacked
wherever. Carrying both costs a few hundred bytes and keeps the bundle portable; the
separator of whoever built it would not be. A deployment picks by platform -
`application.unix.args` or `application.windows.args` - which `Demo.java` does with
`File.pathSeparatorChar`.

How it is wired
---------------

Bundling is opt-in through a `bundle` key in a `packaging.properties` file, read from
the configuration location (a module's `META-INF/build.jenesis/` folder - or
`build.jenesis/` in a Maven layout - falling back to the project-wide configuration
directory, `build.jenesis/` under the project root by default; the first match wins, so a module-local file
overrides a project-wide one). When `bundle=true`, the build writes a
`bundle.zip` after every module has been built, one for every module declaring a main
class (a `@jenesis.main` Javadoc tag, or a `<mainClass>` POM property); modules
without one are skipped. Like the `launcher` jar, the archive is a per-module
artifact left in the build tree (`.../package/bundle/output/bundle/bundle.zip`)
rather than collected into `stage/`, so `Demo.java` locates it by walking `target/`.

Layout
------

    demo/demo-08-bundle
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java       builds the bundle, unpacks it, and runs the app from it
    `-- sources/
        |-- module-info.java     module demo.bundle (@jenesis.main, requires org.slf4j, pinned)
        `-- sample/Sample.java   main(String[] args); uses org.slf4j via `import module org.slf4j;`

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout, exactly as `../demo-02-java-modular` does, and resolves the
declared `org.slf4j` module through the Jenesis module repository. Plain
`java build/jenesis/Make.java` (no bundle target) compiles and jars the module
without producing a bundle.

Deploying onto a JRE base
-------------------------

The bundle's whole point is a JRE-based image: layer your jars over an off-the-shelf
runtime instead of carrying your own. Unzipped onto a `-jre` base it needs no JDK and
no jpackage:

    FROM eclipse-temurin:25-jre
    COPY bundle/ /opt/app/
    WORKDIR /opt/app
    ENTRYPOINT ["java", "@application.unix.args"]

The trade against the self-contained app-image (`../demo-07-java-modular-executable`)
is the classic shared-base one: an app-image bundles its own `jlink`-trimmed runtime
(smallest and runtime-faithful per artifact, but each service duplicates the JVM),
while a JRE-base bundle is tiny (only your jars) and shares one content-addressed JVM
layer across every image built on it - leaner in aggregate when you run many distinct
services, at the cost of coupling to that base's JVM version. A single executable jar
(selected with `launcher=true` in `packaging.properties`, see
`../demo-07-java-modular-executable`) is a third point: the same split closure plus a
shaded launcher, so it runs with a bare `java -jar` and no launch command at all.
