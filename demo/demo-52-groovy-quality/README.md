Groovy code-quality demo
========================

The Groovy counterpart to `../demo-37-java-quality`, following the Groovy demo
that precedes it. The CodeNarc plugin lints the Groovy sources. There is no
Groovy formatter, so unlike the Java, Kotlin, and Scala demos this one is
lint-only. As elsewhere, there is no build script: `jenesis.plugins.properties`
names the plugin, and it runs where its `plugin-codenarc.properties` is found in
the `build.jenesis/` configuration directory.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Groovy module (resolving the pinned Groovy compiler) and
runs CodeNarc against the sources. The first build downloads the compiler and
CodeNarc, so it takes a while.

Layout
------

    demo/demo-52-groovy-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties names the Groovy and CodeNarc plugins
    |-- build.jenesis/
    |   |-- plugin-codenarc.properties  binds codenarc.xml
    |   |-- codenarc.xml           the CodeNarc rule set
    `-- sources
        |-- module-info.java
        `-- sample
            |-- Greeter.java       a Java type in the exported package
            `-- Sample.groovy

How the linter is wired
-----------------------

`jenesis.plugins.properties` names CodeNarc at the hook point `check`, and
`plugin-codenarc.properties` binds its rule set into the input `rules`:

    codenarc+check=build.jenesis.plugin.codenarc
    @rules=../build.jenesis/codenarc.xml

CodeNarc runs with `-rulesetfiles=file:codenarc.xml` over the `.groovy` sources
and writes an XML report under its step's output folder:

    target/build/.../verify-module-sources/check/custom/codenarc/delegate/check/output/reports/codenarc/codenarc-report.xml

CodeNarc is report-only by default; it records findings without failing the
build, unless `-Djenesis.source.codenarc=strict` asks it to fail on any. As in the Groovy demo, the exported package keeps a Java type
(`Greeter`), because `groovyc` does not read `.java` as source and so cannot
contribute a class to a package it does not also define (see `../demo-51-groovy`).

Pinning
-------

The Groovy compiler is pinned in its own `groovy` group (see `../demo-51-groovy`).
CodeNarc resolves a floating `RELEASE` in its own `codenarc` group, and the
plugin's own jars are pinned in the group `plugin-codenarc`; run
`java build/jenesis/Make.java pin` to record it with checksums when you want a
fully reproducible tool chain.
