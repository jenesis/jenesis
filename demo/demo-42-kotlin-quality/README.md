Kotlin code-quality demo
========================

The Kotlin counterpart to `../demo-34-java-quality`, following the Kotlin demo
that precedes it. A small pure-Kotlin module is linted by detekt and ktlint, and
ktlint doubles as a formatter that verifies the source layout. As with the Java
demo, no build script is involved: each tool turns on because its configuration
file is present in the `build.jenesis/` configuration directory.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Kotlin module (resolving the pinned Kotlin compiler) and
runs every tool whose configuration file it finds. The first build downloads the
compiler and the tools, so it takes a while.

Layout
------

    demo/demo-42-kotlin-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- detekt.yml                 activates detekt
    |-- .editorconfig              activates ktlint (lint and format)
    |-- ktfmt/ktfmt.properties     activates ktfmt, under a profile
    |-- jenesis-ktfmt.properties   that profile's settings: no ktlint formatting
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.kt

How Jenesis infers the tools
----------------------------

The matched configuration files select the Kotlin tools the same way the Java
demo selects its own:

    detekt.yml       -> detekt (lints the Kotlin sources)
    .editorconfig    -> ktlint (lints the sources, and formats them)

detekt runs with `--config detekt.yml`; ktlint receives `.editorconfig` as
`--editorconfig`, so the located file's rules apply as defaults no matter which
configuration folder it lives in (an `.editorconfig` beside the sources still
wins for the properties it sets, per editorconfig layering). Both lint the `.kt` sources in parallel with compilation and write an XML
report under their step's output folder. By default they are report-only;
`-Djenesis.source.detekt=strict` and `-Djenesis.source.ktlint=strict`
fail the build on their violations instead, as in the Java code-quality demo.

Formatting: verify, and how to reformat
---------------------------------------

The same `.editorconfig` also wires ktlint as a *formatter* in verify mode: a
normal build fails if a `.kt` file is not already ktlint-formatted, but never
rewrites it. To apply ktlint and rewrite the sources in place, run the build
with the rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

This is the Kotlin equivalent of the `google-java-format` rewrite shown in
`../demo-34-java-quality`. The formatters for the other JVM languages work the
same way.

Formatting with ktfmt instead
-----------------------------

A `ktfmt.properties` formats the Kotlin sources with ktfmt rather than ktlint.
It may be empty, which formats in ktfmt's default Meta style, or name ktfmt's
options:

    style=kotlinlang      meta, google or kotlinlang
    imports=sort          keeps unused imports; sort,remove, the default,
                          removes them as well

Two formatters would fight over the same sources, so a `ktfmt.properties` beside
an `.editorconfig` that still drives ktlint's formatting fails the build, naming
`-Djenesis.format.ktlint=false`. The `ktfmt` profile does exactly that: its
`build.jenesis/ktfmt/` folder adds the `ktfmt.properties`, and
`jenesis-ktfmt.properties` switches ktlint's formatting off, while ktlint still
lints:

    java -Djenesis.make.profiles=ktfmt build/jenesis/Make.java

ktfmt verifies like the other formatters and rewrites under the same switch.
`-Djenesis.format.ktfmt=false` leaves it out.

Pinning
-------

The Kotlin compiler is pinned in its own `kotlinc` group, locked independently of
the `kotlin.stdlib` the module ships against (see `../demo-41-kotlin`). detekt,
ktlint and ktfmt each resolve a floating `RELEASE` in their own group; run
`java build/jenesis/Make.java pin` to record them with checksums when you want
a fully reproducible tool chain.
