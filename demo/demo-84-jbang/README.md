JBang demo
==========

A build written as a [JBang](https://www.jbang.dev) script: one Java file that names
the JDK it needs and the engine it builds with, and a `main` that drives the build
through the same API the tools of the earlier demos run on. JBang supplies the
engine, so a project built this way carries no `build/jenesis/` folder at all - the
only build code in it is the script.

What to write
-------------

The project is a module and a class, and the script beside them:

    demo/demo-84-jbang
    |-- build/Demo.java          the build, as a JBang script
    `-- sources/
        |-- module-info.java     module demo.jbang, with @jenesis.main sample.Sample
        `-- sample/Sample.java

The script opens with two JBang directives. To the Java compiler they are comments,
so the file stays plain Java. In a project of your own they read:

    //JAVA 25+
    //DEPS build.jenesis:build.jenesis:0.18.2

`//JAVA 25+` asks for a JDK of version 25 or newer, and JBang downloads one where
the machine's own is older. `//DEPS` names a released engine by its Maven
coordinate, which JBang resolves from Maven Central.

This demo's script differs in its second line. Every demo of this repository builds
with the engine of its checkout, the others through a `build/jenesis` link into
`sources/build/jenesis`, and this one by naming the same sources to JBang:

    //JAVA 25+
    //SOURCES ../../../sources/build/**/*.java

JBang compiles those sources together with the script, so the demo runs the engine
it sits beside rather than a release. Outside this repository there are no such
sources, and the `//DEPS` line is the one to write.

The rest is a program that stages the project and runs what it staged:

    Make.Result staged = new Make("build.jenesis.Project").build("stage");
    if (staged.code() != 0) {
        System.exit(staged.code());
    }
    Path module = staged.outputs().get("stage/modular").resolve("demo.jbang");

`Make` reads `jenesis.properties`, the profiles and the `-Djenesis.*` properties of
the JVM it runs in, builds the selectors it is handed, and answers the exit code
with the folder each step wrote. The script then starts `java` on the staged
module, handing on its own arguments.

Run it
------

From this directory, with `jbang` on the path:

    jbang build/Demo.java Ada Lovelace

The first run compiles the script, here with the engine's sources, which takes some
seconds; later runs reuse the result until a source changes.
The build prints its steps as any Jenesis build does, and the script ends with the
staged jar and the line the built module prints:

    [STARTED]   Building in '/path/to/demo-84-jbang/target' in daemon mode...
    ...
    [EXECUTED]  stage/modular in 0.01 seconds
    [EXECUTED]  stage/maven in 0.01 seconds
    [EXECUTED]  stage/project in 0.01 seconds
    [COMPLETED] Finished in 0.22 seconds
    Staged target/stage/modular/output/demo.jbang/demo.jbang.jar
    Hello, Ada Lovelace, from a module that a JBang script built and staged!

A setting goes before the script, where JBang hands it to the JVM as a system
property, and whatever follows the script is the script's own:

    jbang -Djenesis.project.version=1.0.0 build/Demo.java

Which engine runs
-----------------

With a `//DEPS` line, the engine is the **published** one, the jar the line names,
as JBang resolved it, and moving the build to another release is an edit of that
line. In this demo it is the engine of the checkout, compiled by JBang. Either way
nothing is read from a `build/jenesis/` folder.

The `jbang-catalog.json` at the root of the `jenesis/jenesis` repository offers the
released commands to a project that has no build program of its own:

    jbang jenesis-make@jenesis/jenesis stage
    jbang jenesis-exec@jenesis/jenesis Ada Lovelace
    jbang jpx@jenesis/jenesis org.junit.platform.console@6.1.3 --version

The catalog names the newest release and moves with every release. JBang fetches a
catalog again once its copy is twelve hours old, and at once under `--fresh`:

    jbang --fresh jenesis-make@jenesis/jenesis

A build that is to stay on one release names the coordinate instead of the alias,
as the script above does in its `//DEPS` line:

    jbang --java 25+ --main build.jenesis.Make build.jenesis:build.jenesis:0.18.2 stage

Each alias runs the released engine as it stands, as the installed `jenesis-make`,
`jenesis-exec` and `jpx` commands do. Selectors and `-Djenesis.*` settings follow
the alias as they follow the command.

`jbang app install` puts an alias on the path as a command of the alias's own
name, the name the same command has when a package manager installs it:

    jbang app install jenesis-make@jenesis/jenesis

What this route does not check
------------------------------

  * The installed `jenesis` command verifies a project's `build/jenesis/` against the
    sources its release published before it runs them. Nothing of the kind happens
    here: the engine is the release the script or the alias names, and a
    `build/jenesis/` folder, with the version it records, is not read.
  * Where JBang downloads the engine, for a `//DEPS` line or an alias, it fetches the
    jar over HTTPS with the SHA-1 published beside it, but runs the jar even where
    the two differ, and it checks no signature. This demo's own script downloads no
    engine.
  * Whatever JBang resolves - the engine, which is one jar that depends on nothing,
    and anything else a script names in `//DEPS`, with its transitive dependencies -
    lands on the class path and is not covered by the versions and checksums the
    project pins.
  * An alias is whatever `jbang-catalog.json` on the default branch of
    `jenesis/jenesis` says when it runs, and JBang asks for no confirmation. The
    `//DEPS` line of a script, or a coordinate on the command line, names the
    release without the catalog.

A build runs the project's code and its plugins whichever engine runs it, so this is
about which engine runs and how it was obtained.
