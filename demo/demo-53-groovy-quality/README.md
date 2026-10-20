Groovy code-quality demo
========================

The Groovy counterpart to `../demo-37-java-quality`, following the Groovy demo
that precedes it. CodeNarc lints the Groovy sources. There is no Groovy
formatter, so unlike the Java, Kotlin, and Scala demos this one is lint-only. As
elsewhere, there is no build script: CodeNarc is an official plugin the build
resolves by itself where its rule set, a `codenarc.xml`, is found in the
`build.jenesis/` configuration folder.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Groovy module (resolving the pinned Groovy compiler) and
runs CodeNarc against the sources. The first build downloads the compiler and
CodeNarc, so it takes a while.

Layout
------

    demo/demo-53-groovy-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   `-- codenarc.xml           switches CodeNarc on: its rule set
    `-- sources
        |-- module-info.java
        `-- sample
            |-- Greeter.java       a Java type in the exported package
            `-- Sample.groovy

How the linter is wired
-----------------------

As in the Java code-quality demo, the rule set switches CodeNarc on by itself
where it lies in a configuration folder. A rule set kept elsewhere is named from
a `codenarc.properties` as `configuration=<path>`, relative to it; where that
file is found, `codenarc.xml` is not read, and properties without
`configuration` fail the build, naming the key to add.

CodeNarc runs with `-rulesetfiles=file:codenarc.xml` over the `.groovy` sources
and writes an XML report under its step's output folder:

    target/build/.../verify-module-sources/check/codenarc/delegate/check/output/reports/codenarc/codenarc-report.xml

CodeNarc is report-only by default; it records findings without failing the
build, unless `-Djenesis.strict.codenarc=true` asks it to fail on any, and
`-Djenesis.check.codenarc=false` skips it. As in the Groovy demo, the exported
package keeps a Java type (`Greeter`), because `groovyc` does not read `.java`
as source and so cannot contribute a class to a package it does not also define
(see `../demo-52-groovy`).

Pinning
-------

The Groovy compiler is pinned in its own `groovy:tool` group (see
`../demo-52-groovy`). CodeNarc resolves a floating `RELEASE` in its own
`codenarc:tool` group, and the plugin's own jars are pinned in the group
`codenarc`; run `java build/jenesis/Make.java pin` to record them with checksums
when you want a fully reproducible tool chain.
