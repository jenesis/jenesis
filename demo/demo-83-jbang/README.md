JBang demo
==========

A build written as a [JBang](https://www.jbang.dev) script: one Java file that names
the JDK it needs and the released `build.jenesis` it builds with, and a `main` that
drives the build through the same API the tools of the earlier demos run on. JBang
resolves the engine from Maven Central, so this project carries no `build/jenesis/`
folder at all - the only build code in it is the script.

What to write
-------------

The project is a module and a class, and the script beside them:

    demo/demo-83-jbang
    |-- build/Demo.java          the build, as a JBang script
    `-- sources/
        |-- module-info.java     module demo.jbang, with @jenesis.main sample.Sample
        `-- sample/Sample.java

The script opens with two JBang directives. To the Java compiler they are comments,
so the file stays plain Java:

    //JAVA 25+
    //DEPS build.jenesis:build.jenesis:0.18.2

`//JAVA 25+` asks for a JDK of version 25 or newer, and JBang downloads one where
the machine has none. `//DEPS` names the engine by its Maven coordinate. The rest is
a program that stages the project and runs what it staged:

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

The first run compiles the script and resolves the engine; later runs reuse both.
The build prints its steps as any Jenesis build does, and the script ends with the
staged jar and the line the built module prints:

    [STARTED]   Building in '/path/to/demo-83-jbang/target' in daemon mode...
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

The engine is the **published** one, the jar the `//DEPS` line names, as JBang
resolved it. Nothing is compiled from a `build/jenesis/` folder, so moving the build
to another release is an edit of that one line.

The `jbang-catalog.json` at the root of the `jenesis/jenesis` repository offers the
released commands to a project that has no build program of its own:

    jbang jenesis-make@jenesis/jenesis stage
    jbang jenesis-exec@jenesis/jenesis Ada Lovelace
    jbang jpx@jenesis/jenesis org.junit.platform.console@6.1.3 --version

Each alias runs the released engine as it stands, as the installed `jenesis-make`,
`jenesis-exec` and `jpx` commands do. Selectors and `-Djenesis.*` settings follow
the alias as they follow the command.
