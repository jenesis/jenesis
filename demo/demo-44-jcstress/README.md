Concurrency stress test demo (jcstress)
=======================================

Two jcstress tests that the build generates, compiles and **runs**: one shows a
lost update between two threads, the other shows that an atomic counter has
none. As with the benchmark before it, there is no build script and no shaded
test jar.

Run it
------

    java build/jenesis/Execute.java -m quick -r target/jcstress

builds the project and runs jcstress, which starts JVMs of its own to run each
test. After about half a minute it reports the plain counter as interesting,
because both threads read zero and one increment is lost:

    .......... [OK] demo.stress.Counter.Plain

      RESULT     SAMPLES     FREQ       EXPECT  DESCRIPTION
        1, 1  62,776,726   36.22%  Interesting  Both read zero: an increment is lost
        1, 2  62,592,795   36.12%   Acceptable  One increment follows the other
        2, 1  47,944,793   27.66%   Acceptable  One increment follows the other

The atomic counter is among the tests that passed, and the HTML report is
`target/jcstress/index.html`. The arguments after `Execute.java` are jcstress's
own: `-m` picks the mode, from `sanity` (seconds) to `stress` (hours), `-t`
selects tests by a regular expression, and `-r` names the report folder.
jcstress also writes a `jcstress-results-<time>.bin.gz` file into the folder it
runs in, which `.gitignore` leaves out. `java build/jenesis/Make.java` builds
without running anything.

A forbidden outcome fails the run
---------------------------------

An outcome marked `FORBIDDEN` that happens fails the test, and the run exits
with code 1. Mark the lost update of `Counter.Plain` as forbidden:

    @Outcome(id = "1, 1", expect = FORBIDDEN, desc = "Both read zero: an increment is lost")

and run it again, with `-t Plain` to run that test alone. jcstress lists it under
`Failed tests` and the command fails, so a pipeline can run the tests with
nothing to parse.

Generation
----------

jcstress generates a harness class for each test with an **annotation
processor**, as JMH does, and `jcstress-core` carries that processor itself. The
`pom.xml` declares the jar twice: once as a dependency, which the tests compile
against and run with, and once with the type `processor`, which puts it on the
processor path:

    <dependency>
        <groupId>org.openjdk.jcstress</groupId>
        <artifactId>jcstress-core</artifactId>
        <version>0.16</version>
        <type>processor</type>
    </dependency>

The processor writes the harness classes and the `META-INF/TestList` that jcstress
reads at run time into the `classes/` folder javac writes to, so the jar carries
them. The `mainClass` property names jcstress's own entry point,
`org.openjdk.jcstress.Main`, which is what `Execute.java` runs.

Why a `pom.xml` and not a module
--------------------------------

The JMH demo runs its benchmark from the module path, because JMH can run in the
JVM that starts it. jcstress cannot: it always starts new JVMs, and it hands each
one its own `java.class.path` to find the tests and itself. That value names
the class path alone, never a module, so jcstress has to run from the class
path. A project
without a `module-info.java` is built and run there, and a `pom.xml` is the
shortest such project.

jcstress pins its threads to CPUs through JNA, which loads a native library. A
JDK warns about that unless the code is granted native access, and the
`pom.xml` grants it with a comment:

    <!--jenesis.native net.java.dev.jna/jna-->

On the class path that grant is `--enable-native-access=ALL-UNNAMED`. jcstress
hands the options of its own JVM on to every JVM it starts, so the grant reaches
those as well.

Layout
------

    demo-44-jcstress
    |-- build/jenesis                      symlink to ../../../sources/build/jenesis
    |-- pom.xml                            jcstress as a dependency and a processor, the main class, the grant
    `-- sources
        `-- demo/stress/Counter.java       the plain and the atomic counter
