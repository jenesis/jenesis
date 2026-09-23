Export demo
===========

`stage` lays a build out as repositories under `target/stage`; `export` installs
them into the local repositories of this machine. The modular tree goes into the
local module repository (`~/.jenesis`) and the Maven tree into the local Maven
repository (`~/.m2/repository`), so another project on the same machine requires
the module like any other dependency, before anything is published.

The Maven tree carries the generated POM, so the export is not only for Jenesis:
Maven, Gradle's `mavenLocal()` and every other tool that reads the local Maven
repository consume the module from there as well.

Layout
------

    demo/demo-60-export
    |-- library                       the project that is exported
    |   |-- build/jenesis             symlink to ../../../../sources/build/jenesis
    |   `-- sources
    |       |-- module-info.java      module demo.greeter, with no version
    |       `-- demo/greeter/Greeter.java
    `-- app                           another project, requiring what library exported
        |-- build/jenesis             symlink to ../../../../sources/build/jenesis
        `-- sources
            |-- module-info.java      module demo.app { requires demo.greeter; }
            `-- demo/app/Main.java

The greeting names the version of `demo.greeter` that `app` ran against, so each
step below shows which build it resolved.

Run it
------

Left alone, `export` writes into `~/.jenesis` and `~/.m2/repository`. The demo
points both at a temporary folder instead, so it leaves your own repositories
untouched; the local Maven repository must exist before a build names it:

    LOCAL=$(mktemp -d)
    mkdir -p "$LOCAL/jenesis" "$LOCAL/m2"
    SETTINGS="-Djenesis.module.local=$LOCAL/jenesis -Djenesis.maven.local=$LOCAL/m2"

    cd library
    java $SETTINGS build/jenesis/Make.java export

`library` names no version, so the module stays unversioned and its POM carries
the `0-SNAPSHOT` placeholder:

    $LOCAL/jenesis/demo.greeter/demo.greeter.jar
    $LOCAL/jenesis/demo.greeter/demo.greeter.pom
    $LOCAL/m2/demo/greeter/demo.greeter/0-SNAPSHOT/demo.greeter-0-SNAPSHOT.jar
    $LOCAL/m2/demo/greeter/demo.greeter/0-SNAPSHOT/demo.greeter-0-SNAPSHOT.pom

The other project requires it by its module name, and nothing else:

    cd ../app
    java $SETTINGS build/jenesis/Execute.java

    Hello, app, from demo.greeter (unversioned)

From another build tool
-----------------------

The same export is an ordinary Maven artifact, under the coordinate the module
name derives - the first two segments as the groupId, the whole name as the
artifactId. A Maven project declares it as any other dependency:

    <dependency>
        <groupId>demo.greeter</groupId>
        <artifactId>demo.greeter</artifactId>
        <version>0-SNAPSHOT</version>
    </dependency>

Maven reads `~/.m2/repository` by default, and `-Dmaven.repo.local=$LOCAL/m2`
points it at the demo's folder. Gradle reads it once a build declares
`repositories { mavenLocal() }`.

A module without a version is not fixed
---------------------------------------

An unversioned export is convenient while two projects change together, but it
names no build in particular: `app` requires whichever build of `demo.greeter`
was exported last. Strict pinning therefore refuses it:

    java -Djenesis.dependency.pin=strict $SETTINGS build/jenesis/Make.java

To depend on one build, export it with a version and pin that version:

    cd ../library
    java -Djenesis.project.version=1.0.0 $SETTINGS build/jenesis/Make.java export

    /**
     * An application requiring the library that the sibling project exported.
     *
     * @jenesis.release 25
     * @jenesis.main demo.app.Main
     * @jenesis.pin demo.greeter 1.0.0
     */
    module demo.app {
        requires demo.greeter;
    }

`pin` then adds the checksum of that build beside the version, and the strict
build accepts it:

    cd ../app
    java $SETTINGS build/jenesis/Make.java pin
    java -Djenesis.dependency.pin=strict $SETTINGS build/jenesis/Execute.java

    Hello, app, from demo.greeter 1.0.0

Exporting again
---------------

A build resolves its dependencies again when what it declares changes, not when
another project exports a new build of one of them. After exporting `library`
again under the same version, or without one, ask `app` for a clean build to take
the new export:

    java -Djenesis.executor.rebuild=true $SETTINGS build/jenesis/Execute.java
