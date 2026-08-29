Benchmark demo (JMH)
====================

A JMH benchmark that the build generates, compiles and **runs** - no build
script, no shaded benchmark jar, no separate benchmark source set.

Run it
------

    java build/jenesis/Execute.java

which builds the module and runs its `@jenesis.main` class, printing a real
result table:

    Benchmark                 (length)  Mode  Cnt    Score   Error  Units
    StringBench.appended             8  avgt    2   32.485          ns/op
    StringBench.appended            64  avgt    2  219.741          ns/op
    StringBench.concatenated         8  avgt    2   86.257          ns/op
    StringBench.concatenated        64  avgt    2  767.785          ns/op

`java build/jenesis/Project.java` builds without running it.

Generation, without a generator module
--------------------------------------

JMH does not read a schema; it generates its harness classes with an **annotation
processor**, which the build already supports. So there is nothing to configure:
the processor is declared like any other plugin and resolves onto the processor
path alone.

    @jenesis.plugin jmh.generator.annprocess

The processor writes `StringBench_jmhType`, one `_jmhTest` class per benchmark
method, and the `META-INF/BenchmarkList` that JMH reads at run time - all into the
same `classes/` folder javac writes to, so the jar carries them.

Naming a library that names itself nothing
------------------------------------------

JMH 1.37 ships neither a `module-info` nor an `Automatic-Module-Name`, and neither
do its dependencies. The benchmark module has to `requires jmh.core`, so that one
needs a name the module path can carry, which `@jenesis.alias` gives it:

    @jenesis.alias jmh.core org.openjdk.jmh/jmh-core

The alias carries no version; `@jenesis.pin` states it and `pin` fills in the
checksum.

The *processor* needs no such thing. A processor is a build-time tool, not part of
the module graph, and javac takes it on either the processor module path or the
processor class path. The build asks for the module path when compiling a module
and falls back to the class path when an artifact carries no module identity at
all - which is the case here, so `jmh-generator-annprocess` and its dependencies
are named by coordinate and nothing else:

    @jenesis.plugin maven/org.openjdk.jmh/jmh-generator-annprocess

Two things a modular benchmark needs
------------------------------------

    module demo.bench {
        requires jmh.core;
        requires jdk.unsupported;

        exports demo.bench;
        exports demo.bench.jmh_generated to jmh.core;
    }

`requires jdk.unsupported` because JMH reaches for `sun.misc.Unsafe`, and
`exports demo.bench.jmh_generated to jmh.core` because JMH instantiates the
generated harness classes reflectively - a package that exists only after
generation, exported to the library that generated it.

The benchmark also asks for `.forks(0)`. JMH normally forks a fresh JVM per trial
and hands it the parent's class path, which a module path is not; running in the
host VM keeps the demo self-contained. A real measurement run should fork, which
means launching the benchmark from a class path rather than from `Execute.java`.

Layout
------

    demo-50-jmh
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    `-- sources
        |-- module-info.java     the plugin, the aliases and the pins
        `-- demo/bench/StringBench.java   two benchmarks and the Runner entry point
