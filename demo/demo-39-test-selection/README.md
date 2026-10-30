test-selection - run only the tests a change can affect
========================================================

Jenesis already skips a module's test step entirely when none of that module's
inputs changed. This demo shows the finer-grained companion: within a module
that did change, run only the test classes that can actually be affected by the
change, and leave the rest cached. It is enabled by passing
`-Djenesis.test.mode=incremental`; the default, `alphabetic`, runs every test by
class name. It is meant mainly for **watching** a project during development,
where the build re-runs on every save and a fast, narrowed test pass keeps the
feedback loop tight:

    java -Djenesis.project.watch=true -Djenesis.test.mode=incremental build/jenesis/Make.java

It is a development-loop optimisation, not a correctness gate: continuous
integration should keep running the whole suite (a plain `build` with selection
off), because static selection cannot see reflection, resources or other
indirect couplings. `-Djenesis.test.mode=prioritized` keeps the whole suite and
runs the tests a change reaches first, so their outcome is printed before the
rest has run.

What is re-run
--------------

The module has two unrelated production classes, `Adder` and `Subtractor`, each
covered by its own test (`AdderTest`, `SubtractorTest`). With selection on, a
build re-runs the tests that reach the code that changed and leaves the rest
cached. A change that reaches no test runs nothing at all, and a change that is
not a change to a class - a resource, a dependency, a build setting - runs the
whole suite.

What the demo does
------------------

`build/Demo.java` runs the build four times. Each test records that it ran by
touching a marker file under `target/ran/`, and appends its name to
`target/ran/order`.

1. A first build (`-Djenesis.test.mode=incremental`) has no previous snapshot, so it
   runs the whole suite: both markers appear.
2. The driver edits `Adder` and rebuilds. Only `Adder`'s bytecode changed, and
   only `AdderTest` reaches `Adder`, so the second build re-runs `AdderTest`
   alone; `SubtractorTest` stays cached and writes no marker.
3. The driver adds `Multiplier`, a class no test reaches, and rebuilds. The
   module changed, so its test step runs, but it selects no test: neither
   marker appears.
4. The driver edits `Subtractor` and rebuilds with
   `-Djenesis.test.mode=prioritized`. Both tests run, but `SubtractorTest`,
   which reaches the change, runs before `AdderTest`, although it sorts after it.

The driver asserts exactly that, then restores `Adder` and `Subtractor` and removes `Multiplier`. Run it with:

    java build/Demo.java
