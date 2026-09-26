Java preview features demo
==========================

Use a preview feature of Java: a language feature or an API a JDK ships for trying
out, which it compiles and runs only when told to. The module here switches over a
`long` with primitive type patterns, which Java 25 previews:

    return switch (value) {
        case int small -> value + " fits an int (" + small + ")";
        case long large -> value + " needs a long (" + large + ")";
    };

Declare it
----------

The release a module is compiled for names the preview features as well:

    /**
     * @jenesis.release 25-preview
     * @jenesis.main sample.Sample
     */
    module demo.preview {
        exports sample;
    }

A module compiled against one that uses preview features has to enable them too,
so the test module declares `@jenesis.release 25-preview` as well. Leave it at `25`
and the build stops before compiling it:

    Compiling against demo.preview-0-SNAPSHOT.jar, which uses the preview features of Java 25,
    enables them as well: declare the release as 25-preview, ...

A `pom.xml` enables them beside its release, with the property the Maven compiler
reads as well:

    <maven.compiler.release>25</maven.compiler.release>
    <maven.compiler.enablePreview>true</maven.compiler.enablePreview>

Preview features belong to one Java version, so only a JDK 25 compiles this module.
A build on another JDK fails and names the toolchain that compiles it,
`-Djenesis.toolchain.version=25`, as `../demo-06-toolchain` selects one.

Build and run it
----------------

From this directory:

    java build/jenesis/Execute.java              # 42 fits an int (42)
    java build/jenesis/Execute.java 4294967296   # 4294967296 needs a long (4294967296)

Every run of the module enables the preview features without being asked: the tests,
`Execute`, a bundle from `../demo-09-bundle`, a `jpackage` image and a program `jpx`
runs from `../demo-67-jpx`. The jar records the release in its manifest, so a run
started from the jar alone knows as well:

    Jenesis-Preview: 25

The documentation is generated with the preview features too, so `javadoc` reads the
same sources `javac` compiled.

A runtime image enables them for good
-------------------------------------

`packaging.properties` in the module's `META-INF/build.jenesis/` links a runtime
image, as `../demo-08-java-modular-executable` does. The image's own `java` enables
the preview features, so it runs the module with no option:

    java build/jenesis/Make.java stage
    target/stage/runtime/output/module-sources/bin/java -m demo.preview/sample.Sample

An executable jar, as the `launcher` profile of `../demo-08-java-modular-executable`
builds one, is started by `java -jar`, and a jar cannot enable preview features for the
JVM that opens it, so it runs as `java --enable-preview -jar <jar>`.

Before you publish
------------------

A class that uses a preview feature runs only on the Java version it was compiled
for, and only with preview features enabled. A library built this way binds every
user to that JDK, until the feature is final and the library is compiled without
`-preview`.

Layout
------

    demo/demo-69-java-preview
    |-- build/jenesis                      symlink to ../../../sources/build/jenesis
    |-- sources/
    |   |-- META-INF/build.jenesis/
    |   |   `-- packaging.properties       jlink=true
    |   |-- module-info.java               @jenesis.release 25-preview
    |   `-- sample/Sample.java             the switch over a long
    `-- test/
        |-- module-info.java               @jenesis.release 25-preview, @jenesis.test demo.preview
        `-- sampletest/SampleTest.java
