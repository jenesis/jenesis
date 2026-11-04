Clojure demo
============

Build a Maven-layout project that mixes a Java type with a Clojure namespace.
You keep the namespace under `src/main/clojure`, name the folder once in
`jenesis.properties`, and Jenesis compiles the `.java` sources with `javac`,
compiles the namespace ahead of time with the Clojure the project depends on,
and packages both into one jar - there is no build script to write.

Build and run it
----------------

From this directory:

    java build/jenesis/Make.java

Jenesis reads the `pom.xml`, resolves `org.clojure:clojure`, compiles
`Greeter.java` with `javac`, then compiles the namespace `sample.welcome`
against it. To run the result:

    java build/jenesis/Execute.java

which prints:

    Hello from Clojure, compiled by Jenesis!

The `pom.xml` names `sample.welcome` as its `mainClass`, and the namespace's
`(:gen-class)` makes it a class with a `main` method, which calls the Java
`Greeter` the namespace imports.

Layout
------

    demo/demo-82-clojure
    |-- build/jenesis                 symlink to ../../../sources/build/jenesis
    |-- jenesis.properties            jenesis.maven.sources=clojure
    |-- pom.xml                       depends on org.clojure:clojure
    `-- src/main
        |-- clojure/sample
        |   `-- welcome.clj           the namespace sample.welcome
        `-- java/sample
            `-- Greeter.java          a Java type the namespace calls

A `pom.xml` module reads `src/main/java` and `src/main/resources` by default.
`jenesis.maven.sources=clojure` adds `src/main/clojure` beside them, as it would
`src/main/kotlin` for Kotlin.

How Clojure fits the compiler chain
-----------------------------------

Clojure is an official plugin of the hook point `language`, as Kotlin, Scala
and Groovy are in the demos before it. The build resolves it by itself, as
`module/build.jenesis.plugin.clojure`, in every module that holds a `.clj`
source, and `-Djenesis.language.clojure=false` leaves it out, after which such
a source fails the build.

The plugin runs after `javac`, as Groovy's does, so a namespace reaches the
module's Java types through the compiled classes. It copies every `.clj` and
`.cljc` source into the module's classes, as a Clojure library's jar holds
them, and then compiles each namespace with `clojure.lang.Compile`. The jar
therefore holds `sample/welcome__init.class` and the class `sample/welcome`
that `(:gen-class)` generates, beside `sample/welcome.clj` and
`sample/Greeter.class`.

Classes compiled ahead of time are tied to the Clojure that compiled them, so
the plugin uses the Clojure the module depends on rather than one of its own,
and a module that does not depend on `org.clojure:clojure` fails the build
naming it. Compiling a namespace also compiles every namespace it requires;
the plugin keeps the classes of a dependency's namespaces out of the jar, as
those ship in the dependency's own.

Pinning
-------

`java build/jenesis/Make.java pin` records the Clojure runtime and the plugin's
own closure, in the group `clojure`, in the `jenesis.pin` block at the end of
the `pom.xml`.
