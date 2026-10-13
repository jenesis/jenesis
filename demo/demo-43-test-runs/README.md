Test runs demo
==============

A module runs the tests it compiles. This demo runs tests that another module
compiled: `suite` holds the tests, and `fast` and `slow` each run them, every
one with a system property of its own. That is how a test suite shipped in a
jar - a conformance kit, a TCK - is run against an implementation, and how the
same tests run twice with other JVM flags. A second run is not a setting of a
module; it is a module of its own.

Build it
--------

From this directory:

    java -Djenesis.print.tests=true build/jenesis/Make.java

    ModeTest ran in fast mode
    ModeTest ran in slow mode

The two lines come from one test class, run once by each module. The order in
which they appear varies, because the two modules test in parallel.

Layout
------

    demo/demo-43-test-runs
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- suite/
    |   |-- module-info.java     @jenesis.test abstract; requires org.junit.jupiter.api
    |   `-- suite/ModeTest.java  reads the system property demo.mode
    |-- fast/
    |   |-- module-info.java     @jenesis.test; requires demo.suite
    |   `-- META-INF/build.jenesis/
    |       |-- test.properties          scan=demo.suite
    |       `-- process-test.properties  -Ddemo.mode\=fast=
    |-- slow/                    the same, with -Ddemo.mode\=slow=
    `-- maven/                   the same project as a pom.xml build

There is no `pom.xml` at the root, so every `module-info.java` is a module of
the build. The modules ship pinned.

Tests in a jar
--------------

`suite` is a test module like those of `test-framework`, but it is declared
`@jenesis.test abstract`, so its tests never run on their own: the build
compiles them into its jar and stops there. `fast` compiles no test at all. It
requires `demo.suite`, which puts that jar on its test path, and names it in the
`scan` key of its `test.properties`:

    scan=demo.suite

The test classes of the scanned jar then run among the module's own, chosen by
the same rules: the default naming (`*Test`, `*Tests`, `Test*`, `*IT`, ...),
`-Djenesis.test.filter`, `-Djenesis.test.exclude` and `-Djenesis.test.tag`. A
module that has tests of its own runs both. The key takes several dependencies,
comma-separated, each named by its module name or as `<groupId>/<artifactId>`;
one that is not on the test path fails the build, naming the dependency and
what to declare.

A scanned module on the module path is resolved and its packages are opened to
the test framework, as the module's own are. That is why `ModeTest` may be
package-private here.

A scanned jar is an input of the test run like any other: when `suite` changes,
`fast` and `slow` test again, and when it does not, both keep their results.
With `-Djenesis.test.incremental`, as `test-selection` shows, a change to the
jar re-runs only the scanned tests it reaches.

The same tests, run twice
-------------------------

`fast` and `slow` differ only in their `process-test.properties`, which `javac-arguments`
introduced for the compiler:

    fast/META-INF/build.jenesis/process-test.properties   -Ddemo.mode\=fast=
    slow/META-INF/build.jenesis/process-test.properties   -Ddemo.mode\=slow=

Each module runs the test JVM with its own flags, its own `environment-test.properties`
and its own reports, and is selected, cached and skipped on its own. A run on
other JVM flags, with other tags or with an agent attached is one more small
module that scans the module whose tests it repeats. The tests of a module with
main code are a module of their own already, `@jenesis.test <module>`, which a
sibling requires and scans in the same way; when that test module is not
abstract, its tests also run once as they are.

The pom.xml build
-----------------

`maven/` holds the same three modules as a Maven project. There, `suite` keeps
its tests in `src/main/java`, so they ship in its jar, and `fast` and `slow`
declare the dependency on it. The build reads no plugin block of a `pom.xml`, so
Surefire's `<dependenciesToScan>`, which the POMs keep for Maven, is not what
selects the tests: each module names the dependency in the `scan` key of its
`src/test/build.jenesis/test.properties`, as `<groupId>/<artifactId>`:

    scan=demo/suite

A module whose tests are all scanned needs no test sources of its own:

    cd maven
    java -Djenesis.print.tests=true build/jenesis/Make.java

An entry scans every jar of that dependency on the test path, its `tests` jar
included. So the tests of a module repeat with other flags in a sibling that
declares a `<type>test-jar</type>` dependency on it and scans it, where Maven
would add a second Surefire execution.
