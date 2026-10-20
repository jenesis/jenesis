Maven plugin demo
=================

Build a Maven plugin. There is no build script: the `pom.xml` declares the module
as it would for Maven, the mojo is an ordinary class, and a
`mavenplugin.properties` in the module's configuration folder switches on the
official plugin `build.jenesis.plugin.mavenplugin`, which writes the plugin
descriptor into the jar with Maven's own plugin tools.

Build it
--------

From this directory:

    java build/jenesis/Make.java

The jar of the module now holds the descriptor beside the classes:

    META-INF/maven/plugin.xml
    META-INF/maven/demo/greet-maven-plugin/plugin-help.xml

Staged, it installs into the local Maven repository as it stands, and Maven runs
its goal in any project, as in this one, whose `pom.xml` Maven reads as well:

    java build/jenesis/Make.java stage
    mvn install:install-file \
        -Dfile=target/stage/maven/output/demo/greet-maven-plugin/1.0/greet-maven-plugin-1.0.jar \
        -DpomFile=target/stage/maven/output/demo/greet-maven-plugin/1.0/greet-maven-plugin-1.0.pom
    mvn demo:greet-maven-plugin:1.0:hello -Dgreet.name=Jenesis

which prints `Hello, Jenesis!`.

Layout
------

    demo/demo-75-maven-plugin
    |-- build/jenesis                    symlink to ../../../sources/build/jenesis
    |-- pom.xml                          the module, packaged as a jar
    `-- src/main
        |-- build.jenesis/
        |   `-- mavenplugin.properties   switches the descriptor on: goalprefix=greet
        `-- java/demo/greet/GreetMojo.java

What is described
-----------------

The mojo is written as for `maven-plugin-plugin`: `@Mojo` and `@Parameter` from
`maven-plugin-annotations`, `AbstractMojo` from `maven-plugin-api`, both in scope
`provided`, as Maven supplies them when it runs the plugin. The descriptor names
each goal, its parameters, their properties and defaults, and takes its
descriptions from the javadoc of the class and its fields. A dependency the module
compiles against alone is provided by Maven and left out of the descriptor's
dependencies, while the release of `maven-plugin-api` names the Maven version the
plugin requires and the compiled classes the Java version.

The module keeps the packaging `jar`, as Maven reads a plugin from its descriptor;
`<packaging>maven-plugin</packaging>` is not built.

| Value | Meaning |
|---|---|
| `goalprefix` | the prefix a goal is called by, as `greet` in `greet:hello`; by default the one Maven derives from the artifact ID |
| `maven` | the release of Maven whose API the descriptor is generated with, `3.9.11` by default |
| `tools` | the release of Maven's plugin tools, `3.15.1` by default |

`-Djenesis.artifact.mavenplugin=false` switches the descriptor off.
