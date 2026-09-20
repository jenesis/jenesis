Test engine inference demo
==========================

Build a Maven-style project whose tests are JUnit 5 Jupiter but whose `pom.xml`
never declares an engine to run them with - the shape of a great many existing
Maven projects, because `maven-surefire-plugin` has auto-provisioned a matching
engine for them for several major versions. Jenesis infers the test framework
from what the tests are written against, resolves the engines that framework
needs, and runs a JUnit 4 class in the same module on the same platform.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Layout
------

    demo/demo-62-test-engine-inference
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build.jenesis/explicit/test.properties   engine=junit-platform, under a profile
    |-- pom.xml              junit-jupiter-api and truth, test-scoped; ships pinned
    |-- sources/sample/Greeter.java
    `-- test/sample
        |-- GreeterTest.java         package-private, @org.junit.jupiter.api.Test
        `-- LegacyGreeterTest.java   public, @org.junit.Test

What the project declares
-------------------------

The two test-scoped dependencies are `org.junit.jupiter:junit-jupiter-api` and
`com.google.truth:truth`. Neither brings a JUnit Platform engine: the Jupiter
API is API-only by design, and Truth pulls in classic `junit:junit` 4.13.2 for
its own assertion API. The resolved test path therefore holds
`org.junit.jupiter.api`, `org.junit.platform.commons` and `junit` - a JUnit 5
project with a JUnit 4 jar on it and nothing that can execute either.

How the engine is selected
--------------------------

A module's test engine is decided by its *resolved dependencies* - not by scanning
the test classes, and not by an entry in the `pom.xml`. `TestEngine.of` walks the
three engines in a fixed order, `JUnitPlatform`, `JUnit4`, `TestNG`, and takes the
first whose framework is on the path. Each answers two separate questions:

| | `isFramework` - what are the tests written against? | `isEngine` - what can run them? |
| --- | --- | --- |
| `JUnitPlatform` | `org.junit.jupiter.api` or `org.junit.platform.engine` | `org.junit.platform.engine` |
| `JUnit4` | `junit` | `junit` |
| `TestNG` | `org.testng` | `org.testng` |

JUnit 4 and TestNG ship their API and their runner in one artifact, so for them
the two questions have one answer and `isFramework` simply inherits `isEngine`.
Jupiter splits them, which is why the distinction exists: a project can be
unambiguously JUnit 5 and still have nothing on its path able to execute a test.

Selection asks the first question, provisioning answers the second. This module's
path holds `org.junit.jupiter.api`, `org.junit.platform.commons` and `junit`, so
the first question stops at `JUnitPlatform` - and the `junit` jar that arrived
behind Truth never gets to answer it. The second question then finds nothing that
can run Jupiter, so `JUnitPlatform.missingCoordinates` resolves what is missing,
each artifact on its own version line: `junit-jupiter-engine` at the version of
the resolved `junit-jupiter-api`, and `junit-platform-console` at the version of
the resolved `junit-platform-commons`. Because a JUnit 4 jar shares the path, it
resolves `junit-vintage-engine` as well, so a JUnit 4 class in this module runs
rather than being quietly discovered by nothing.

Nothing here overrides what the project asked for: every one of those artifacts
is resolved only where the path does not already carry it, so declaring any of
them yourself is what the build uses.

Print the command the test step runs to see the result:

    java -Djenesis.print.tests=true build/jenesis/Make.java

    org.junit.platform.console.ConsoleLauncher execute …
        --select-class=sample.GreeterTest --select-class=sample.LegacyGreeterTest
    [         2 tests found           ]
    [         2 tests successful      ]

`GreeterTest` is package-private with no explicit constructor, which is ordinary
Jupiter and rejected outright by JUnit 4's structural rules. Had the framework
question not been asked first, the `junit` jar would have answered the engine
question on its own and `org.junit.runner.JUnitCore` would have run both classes
as JUnit 4, failing the Jupiter one for being neither public nor holding runnable
methods.

Pinned coordinates
------------------

`pom.xml` ships pinned, so `<dependencyManagement>` also carries the two engines
and the console runner that the build resolves on the project's behalf - pinning
records every coordinate that is fetched, inferred ones included. The project's
own `<dependencies>` still declare none of them, which is the point of the demo:
managed versions constrain what may be resolved, they do not request it.

To rewrite the pins after changing a dependency:

    java build/jenesis/Make.java pin

Naming the engines yourself
---------------------------

Inference fills gaps; it never overrules a declaration. Adding the vintage engine
to `<dependencies>` makes it explicit, and the build then uses that one at that
version rather than resolving its own:

    <dependency>
        <groupId>org.junit.vintage</groupId>
        <artifactId>junit-vintage-engine</artifactId>
        <version>5.11.3</version>
        <scope>test</scope>
    </dependency>

The build runs identically, with `org.junit.vintage.engine` now on the path
because the project put it there - `requires.properties` under the test step then
lists only the console and `junit-jupiter-engine` as resolved on the project's
behalf. `junit-jupiter-engine` works the same way: `demo-03-java-pom-multi`
declares the `junit-jupiter` aggregate, which carries the engine, and has the
console resolved for it - two of the three named, one inferred.

Choosing the framework is the other axis, and it is declared rather than
depended on, because it decides which of the three engines is asked at all. A
`test.properties` in the module's `build.jenesis/` location names it, next to
`jacoco.properties` and the rest:

    engine=junit-platform

Its values are `junit-platform`, `junit4` and `testng`; absent, the table above
decides. It cannot name the vintage engine, which is not a framework of its own
but an engine the platform runs JUnit 4 classes with - that one is a dependency,
as above. The demo ships the file under a profile so both paths stay runnable:

    java -Djenesis.make.profiles=explicit build/jenesis/Make.java

`build.jenesis/explicit/test.properties` layers over `build.jenesis/`, so the
default build above infers the framework and this one is told. Being a file in
the project rather than a command-line flag, the declaration is what every
checkout and every CI run reads, which is the point of declaring it at all.
