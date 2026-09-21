Test framework demo
===================

The tests of this module are JUnit 5 Jupiter, and nothing in the module requires
an engine to run them with - the shape of a module part-way through a migration
off JUnit 4, and of any project whose build tool used to provide the engine.
Jenesis works out which framework the tests are written against, resolves the
engines that framework needs, and runs the module's remaining JUnit 4 class on
the same platform.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Layout
------

    demo/demo-32-test-framework
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build.jenesis/explicit/test.properties   framework=junit-platform, under a profile
    |-- greeter/
    |   |-- module-info.java     module demo.greeter; ships pinned
    |   `-- sample/greeter/Greeter.java
    `-- greeter-test/
        |-- module-info.java     @jenesis.test demo.greeter; one requires, no engine
        |-- greetertest/GreeterTest.java         package-private, @org.junit.jupiter.api.Test
        `-- greetertest/LegacyGreeterTest.java   public, @org.junit.Test

There is no `pom.xml`, so the MODULAR_TO_MAVEN layout is detected and every
`requires` resolves through the module repository.

What the module declares
------------------------

`demo.greeter.test` requires exactly one thing besides the module under test:

    requires org.junit.jupiter.migrationsupport;

That module is how a JUnit 4 suite keeps its rules and assumptions working while
its tests move to Jupiter, and it reads `requires junit transitive` and
`requires org.junit.jupiter.api transitive`. So one clause puts the Jupiter API
and classic `junit` 4.13.2 on the test path, with no engine of any kind: the
Jupiter API is API-only by design, and the JUnit 4 jar is an artifact of the
migration rather than a statement about how the tests are written.

Which framework runs the tests
------------------------------

The framework follows from what the resolved dependencies say the tests are
written against, not from scanning the test classes:

| On the test path         | Framework     |
| ------------------------ | ------------- |
| `org.junit.jupiter.api`  | JUnit Platform |
| `junit`                  | JUnit 4       |
| `org.testng`             | TestNG        |

The Jupiter API wins here, so the `junit` jar the migration module brought along
does not decide it. Whatever that framework needs and the path does not carry is
then resolved for you: the Jupiter engine, the console runner, and - because a
JUnit 4 jar is on the path - the vintage engine, so the JUnit 4 class runs
rather than being found by nothing. Print the command the test step runs to see
the result:

    java -Djenesis.print.tests=true build/jenesis/Make.java

    -m org.junit.platform.console/org.junit.platform.console.ConsoleLauncher execute …
        --select-class=greetertest.GreeterTest
        --select-class=greetertest.LegacyGreeterTest
    [         2 tests found           ]
    [         2 tests successful      ]

`GreeterTest` is package-private with no explicit constructor, which is ordinary
Jupiter and rejected outright by JUnit 4's structural rules - so running it at
all is the proof that the Jupiter engine, not the `junit` jar, was chosen.

`greeter-test/module-info.java` ships pinned, and its `@jenesis.pin` tags cover
the engines and the console runner as well: a pin fixes the version of whatever
is fetched, including what was resolved on the module's behalf. Rewrite them
after changing a dependency with `java build/jenesis/Make.java pin`.

Naming them yourself
--------------------

Nothing here overrules a declaration: an artifact is resolved only where the
path does not already carry it. Require an engine and that one is used, at that
version:

    requires org.junit.vintage.engine;

The build then runs identically, with only the console and the Jupiter engine
still resolved for the module.

The framework itself is declared rather than required, because it decides which
engine is asked for at all. A `test.properties` in the module's `build.jenesis/`
location names it, next to `jacoco.properties` and the rest:

    framework=junit-platform

The values are `junit-platform`, `junit4` and `testng`; absent, the table above
decides. It cannot name the vintage engine, which is not a framework of its own
but an engine the platform runs JUnit 4 classes with - that one is a `requires`,
as above. The demo ships the file under a profile, so both paths stay runnable:

    java -Djenesis.make.profiles=explicit build/jenesis/Make.java

Being a file in the project rather than a command-line flag, the declaration is
what every checkout and every CI run reads.
