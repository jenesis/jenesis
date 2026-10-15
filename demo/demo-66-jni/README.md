JNI demo
========

Build a small C library beside the Java code that calls it, the way a project
on Maven or Gradle runs `javac -h` and a C compiler from a plugin of the build.
The library is compiled by a plugin of the project at `binary/compiled` and
lands in the module's jar, from where the module loads it.

Running it needs a C compiler: `cc` on Linux and macOS, `gcc` (MinGW) on
Windows.

Run it
------

From this directory:

    java build/jenesis/Execute.java

which compiles the Java code, generates the JNI header, compiles the C library
against it and prints:

    2 + 3 = 5, added in C

The jar holds the library below a folder named for the platform it was built on:

    native/linux-amd64/libadder.so
    sample/adder/Adder.class
    sample/adder/Main.class

Layout
------

    demo/demo-66-jni
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties   jni+binary/compiled=./jni
    |-- jni/                 the plugin that builds the library
    |   |-- .jenesis.skip
    |   |-- module-info.java
    |   |-- demo/jni/JniFactory.java
    |   `-- demo/jni/JniModule.java
    |-- c/adder.c            the C implementation of the native method
    `-- sources/             the application, module demo.adder
        |-- module-info.java     grants itself native access
        |-- META-INF/build.jenesis/plugin-jni.properties   library=adder, @c=../c
        `-- sample/adder/
            |-- Adder.java       declares the native method, loads the library from its jar
            `-- Main.java

The plugin
----------

`JniModule` wires two steps. The first is the build's own `javac` step, asked for
the headers alone:

    new Javac(ProcessHandler.Factory.of()).headers(true)

It compiles the module's sources as the module's compiler does, but hands on
nothing but `headers/`, with one header per class that declares a native method,
here `sample_adder_Adder.h`. The second step runs the C compiler over the files
bound as the input `c`, with the JDK's `include/` folders and those headers on
its include path, and writes the library below `classes/`. What a plugin at
`binary/compiled` writes below `classes/` joins the module's jar beside what
`javac` compiled.

The C sources are bound in `plugin-jni.properties` with `@c=../c`, resolved from
the module's folder, so the plugin reads them and nothing else of the project.
A change to `adder.c` rebuilds the library, and a change to the Java code
regenerates the header.

Native access
-------------

Loading a library is a restricted method, as `native-access` shows for the foreign
function API. The application runs its own module, so it grants native access
to itself:

    @jenesis.native demo.adder

and runs without the JVM's warning.

One library per platform
------------------------

A library built here serves the machine that built it. A project that publishes
a library for several platforms builds each on its own machine and publishes it
as a variant of the jar, as `classifiers` shows: the plugin writes the library into a
folder that the module's `classifiers.properties` names, instead of below
`classes/`.
