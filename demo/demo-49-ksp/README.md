KSP demo
========

Run a Kotlin symbol processor over a module's sources, the way Gradle's
`com.google.devtools.ksp` plugin does: Moshi's code generator writes a JSON
adapter for every class annotated `@JsonClass(generateAdapter = true)`, and the
module compiles it with its own sources.

Run it
------

From this directory:

    java build/jenesis/Execute.java

which generates the adapter, compiles it beside the Kotlin sources and prints:

    {"x":1,"y":2} written by GeneratedJsonAdapter(Point).nullSafe()

The jar holds the generated adapter and the ProGuard rules the processor writes
for it:

    sample/Point.class
    sample/PointJsonAdapter.class
    META-INF/proguard/moshi-sample.Point.pro

Layout
------

    demo/demo-49-ksp
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build.jenesis/ksp.properties   one option per line
    |-- pom.xml                  names the processor and pins every group
    `-- sources/sample/
        |-- Point.kt             @JsonClass(generateAdapter = true)
        `-- Main.kt              serialises a Point through Moshi

Declaring a processor
---------------------

KSP is an official plugin: the build resolves it as
`module/build.jenesis.plugin.ksp` and runs it in every module whose
configuration folder holds a `ksp.properties`, beside the Kotlin plugin that
compiles the module's `.kt` sources, as in `../demo-46-kotlin`. Neither needs a
line in `jenesis.plugins.properties`, and `-Djenesis.generate.ksp=false`
switches KSP off.

The processors are named like a compiler plugin, by coordinate in the group
`ksp:processor`, here as a comment in the `pom.xml`:

    <!--jenesis.plugin ksp:processor maven/com.squareup.moshi/moshi-kotlin-codegen-->

or with `@jenesis.plugin ksp:processor maven/com.squareup.moshi/moshi-kotlin-codegen` in a
`module-info.java`. Each line of `ksp.properties` is an option handed to the
processors, as Gradle's `ksp { arg(...) }` hands them:

    moshi.generated=javax.annotation.processing.Generated

so the generated adapter carries that annotation.

KSP reads the module's Kotlin and Java sources and resolves their types against
the module's dependencies, which is why the processor sees `@JsonClass`. What it
writes is compiled with the module's sources, and the resources it writes, as
the ProGuard rules, join the jar.

Pinning
-------

The plugin resolves in the group `ksp`, KSP itself in `ksp:tool` and the
processors in `ksp:processor`: a processor brings a Kotlin standard library of
its own, here Kotlin 1.8, which KSP does not use. `pin` writes every group into
the `pom.xml`, as the `jenesis.pin` comment at its end shows.
