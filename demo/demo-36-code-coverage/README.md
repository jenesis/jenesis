Code-coverage demo
===================

Measure test coverage with JaCoCo. A small Maven project has one class and a test
that exercises part of it; a `jacoco.properties` file turns coverage on, the tests
run under the JaCoCo agent, and an HTML and XML report is rendered from what they
touched. No build script and no JaCoCo configuration are needed.

Where the code-quality demos read your sources and your classes, this one watches
the tests as they run.

Build it
--------

From this directory:

    java build/jenesis/Make.java

The project ships a `jacoco.properties` marker file, so a plain build collects
coverage. Jenesis compiles the project, runs the test
under the JaCoCo agent, and renders the report. The first build downloads JUnit
and the JaCoCo tooling, so it takes a while.

Layout
------

    demo/demo-36-code-coverage
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- pom.xml                    pins JUnit; sources in sources/, tests in test/
    |-- build.jenesis/jacoco.properties         marker file; presence enables JaCoCo
    |-- build.jenesis/profiling/jfr.properties  settings=profile, under a profile
    |-- sources
    |   `-- coverage
    |       `-- Calculator.java    add(...) and subtract(...)
    `-- test
        `-- coverage
            `-- CalculatorTest.java tests add(...) only

How coverage is inferred
------------------------

The default Java assembler runs tests through an `InferredTestObservationModule`,
which bundles the observation engines that are switched on. Today that is JaCoCo,
enabled by the presence of a `jacoco.properties` file (the `jenesis.observe.jacoco`
system property defaults to `true` and can be set to `false` to suppress it):

- With no engine enabled (the default for most projects), it is a plain test run.
- With coverage on, the test step is launched with the JaCoCo agent prepended as
  a `-javaagent`, writing its execution data (`jacoco.exec`) into the test step's
  own output. A downstream report step then runs the JaCoCo CLI over that data
  and the classes of the code under test: the module the tests exercise, never
  the compiled tests themselves.

The agent instruments the run without touching your sources, and JaCoCo resolves
its agent and CLI in their own `jacoco` group, kept separate from the project's
dependencies.

Where the report lands
----------------------

    target/build/.../assemble/observed/test/executed/output/jacoco.exec
    target/build/.../assemble/observed/jacoco/report/output/reports/jacoco/index.html
    target/build/.../assemble/observed/jacoco/report/output/reports/jacoco/jacoco.xml

A `stage` build collects the report, like every other report kind, under
`target/stage/reports/jacoco/<module>/`.

Open `index.html` to browse coverage. Because `CalculatorTest` exercises
`add(...)` but not `subtract(...)`, the report shows the project as partially
covered - coverage is reported, not enforced, so the build stays green.

Pinning
-------

JUnit is pinned in the POM the usual way. JaCoCo's agent and CLI resolve a
floating `RELEASE` in the `jacoco` group; run `java build/jenesis/Make.java
pin` to record them with checksums when you want a reproducible tool chain.
The pins sit in the POM's `<!--jenesis.pin-->` comment, each line prefixed with
the group. The agent writes the data the CLI reads, so it follows the release
the group pins: pinning only the CLI by hand, as

    <!--jenesis.pin
    jacoco/maven/org.jacoco/org.jacoco.cli 0.8.14
    -->

runs the tests under the 0.8.14 agent as well, until `pin` records both.

Recording the tests with Java Flight Recorder
---------------------------------------------

A `jfr.properties` in a configuration folder records the test JVM with Java Flight
Recorder. Each line is an option of the recording, as `-XX:StartFlightRecording`
takes it; the build names the file itself and writes it into the test step's
reports. The demo ships the file under a profile, so a plain build does not record:

    java -Djenesis.make.profiles=profiling build/jenesis/Make.java stage

    target/stage/reports/output/jfr/module/tests.jfr

`settings=profile` samples more often than the JDK's default settings. Open the
recording in JDK Mission Control, or summarise it with `jfr summary`. Changing the
file runs the tests again, and `-Djenesis.observe.jfr=false` switches the recording
off.

