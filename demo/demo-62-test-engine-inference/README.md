Test engine inference demo
==========================

Build a module whose tests are JUnit 5 Jupiter but whose `module-info.java`
never requires an engine to run them with - the shape of a module part-way
through a migration off JUnit 4, and of any project that has relied on its build
tool to provision an engine. Jenesis infers the test framework from what the
tests are written against, resolves the engines that framework needs, and runs
the module's remaining JUnit 4 class on the same platform.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Layout
------

    demo/demo-62-test-engine-inference
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build.jenesis/explicit/test.properties   engine=junit-platform, under a profile
    |-- greeter/
    |   |-- module-info.java     module demo.greeter; ships pinned
    |   `-- sample/greeter/Greeter.java
    `-- greeter-test/
        |-- module-info.java     @jenesis.test demo.greeter; one requires, no engine
        |-- greetertest/GreeterTest.java         package-private, @org.junit.jupiter.api.Test
        `-- greetertest/LegacyGreeterTest.java   public, @org.junit.Test

There is no `pom.xml`, so Jenesis auto-detects the MODULAR_TO_MAVEN layout and
resolves each `requires` through the module repository.

What the module requires
------------------------

`demo.greeter.test` requires exactly one thing besides the module under test:

    requires org.junit.jupiter.migrationsupport;

That module is how a JUnit 4 suite keeps its rules and assumptions working while
its tests move to Jupiter, and its own descriptor reads
`requires junit transitive` and `requires org.junit.jupiter.api transitive`. So
one clause puts the Jupiter API and classic `junit` 4.13.2 on the test path,
readable from the test module, with **no engine of any kind** - Jupiter's API is
API-only by design, and the JUnit 4 jar is an artifact of the migration rather
than a statement about how the tests are written.

How the engine is selected
--------------------------

A module's test engine is decided by its *resolved dependencies* - not by
scanning the test classes, and not by a clause in `module-info.java`.
`TestEngine.of` walks the three engines in a fixed order, `JUnitPlatform`,
`JUnit4`, `TestNG`, and takes the first whose framework is on the path. Each
answers two separate questions:

| | `isFramework` - what are the tests written against? | `isEngine` - what can run them? |
| --- | --- | --- |
| `JUnitPlatform` | `org.junit.jupiter.api` or `org.junit.platform.engine` | `org.junit.platform.engine` |
| `JUnit4` | `junit` | `junit` |
| `TestNG` | `org.testng` | `org.testng` |

JUnit 4 and TestNG ship their API and their runner in one artifact, so for them
the two questions have one answer and `isFramework` simply inherits `isEngine`.
Jupiter splits them, which is why the distinction exists: a module can be
unambiguously JUnit 5 and still have nothing on its path able to execute a test.

Selection asks the first question, provisioning answers the second. This
module's path holds `org.junit.jupiter.api`, `org.junit.platform.commons` and
`junit`, so the first question stops at `JUnitPlatform` - and the `junit` jar
that arrived behind the migration module never gets to answer it. The second
question then finds nothing that can run Jupiter, so
`JUnitPlatform.missingCoordinates` resolves what is missing, each artifact on
its own version line: `junit-jupiter-engine` at the version of the resolved
`junit-jupiter-api`, and `junit-platform-console` at the version of the resolved
`junit-platform-commons`. Because a JUnit 4 jar shares the path, it resolves
`junit-vintage-engine` as well, so the module's JUnit 4 class runs rather than
being quietly discovered by nothing.

Nothing here overrides what the module asked for: every one of those artifacts
is resolved only where the path does not already carry it, so requiring any of
them yourself is what the build uses.

Print the command the test step runs to see the result:

    java -Djenesis.print.tests=true build/jenesis/Make.java

    -m org.junit.platform.console/org.junit.platform.console.ConsoleLauncher execute …
        --select-class=greetertest.GreeterTest
        --select-class=greetertest.LegacyGreeterTest
    [         2 tests found           ]
    [         2 tests successful      ]

`GreeterTest` is package-private with no explicit constructor, which is ordinary
Jupiter and rejected outright by JUnit 4's structural rules. Had the framework
question not been asked first, the `junit` jar would have answered the engine
question on its own and `org.junit.runner.JUnitCore` would have run both classes
as JUnit 4, failing the Jupiter one for being neither public nor holding
runnable methods.

Pinned coordinates
------------------

`greeter-test/module-info.java` ships pinned, so its `@jenesis.pin` tags also
carry the two engines and the console runner that the build resolves on the
module's behalf - pinning records every coordinate that is fetched, inferred
ones included. The module's own `requires` still name none of them, which is the
point of the demo: a pin fixes the version of whatever is resolved, it does not
request it. The pins sit on the JUnit 6 line, where the platform and Jupiter
version lines have converged; under JUnit 5 the same two coordinates would be
pinned at `1.11.x` and `5.11.x`, which is why neither version is derived from
the other.

To rewrite the pins after changing a dependency:

    java build/jenesis/Make.java pin

Naming the engines yourself
---------------------------

Inference fills gaps; it never overrules a declaration. Requiring the vintage
engine makes it explicit, and the build then uses that one at that version
rather than resolving its own:

    requires org.junit.vintage.engine;

The build runs identically, with `org.junit.vintage.engine` now on the path
because the module put it there - `requires.properties` under the test step then
lists only the console and `junit-jupiter-engine` as resolved on the module's
behalf. `junit-jupiter-engine` works the same way: `demo-04-java-modular-multi`
requires the `org.junit.jupiter` aggregate, which carries the engine, and has
the console resolved for it - two of the three named, one inferred.

Choosing the framework is the other axis, and it is declared rather than
required, because it decides which of the three engines is asked at all. A
`test.properties` in the module's `build.jenesis/` location names it, next to
`jacoco.properties` and the rest:

    engine=junit-platform

Its values are `junit-platform`, `junit4` and `testng`; absent, the table above
decides. It cannot name the vintage engine, which is not a framework of its own
but an engine the platform runs JUnit 4 classes with - that one is a `requires`,
as above. The demo ships the file under a profile so both paths stay runnable:

    java -Djenesis.make.profiles=explicit build/jenesis/Make.java

`build.jenesis/explicit/test.properties` layers over `build.jenesis/`, so the
default build above infers the framework and this one is told. Being a file in
the project rather than a command-line flag, the declaration is what every
checkout and every CI run reads, which is the point of declaring it at all.
