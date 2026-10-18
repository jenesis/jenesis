Kotlin code-quality demo
========================

The Kotlin counterpart to `../demo-37-java-quality`, following the Kotlin demo
that precedes it. A small pure-Kotlin module is linted by the detekt and ktlint
plugins, and ktlint doubles as a formatter that verifies the source layout. As
with the Java demo, no build script is involved: `jenesis.plugins.properties`
names the plugins, and each runs where its `plugin-<name>.properties` is found in
the `build.jenesis/` configuration directory.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Kotlin module (resolving the pinned Kotlin compiler) and
runs every tool it is configured for. The first build downloads the
compiler and the tools, so it takes a while.

Layout
------

    demo/demo-47-kotlin-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties names the Kotlin, detekt, ktlint and ktfmt plugins
    |-- build.jenesis/
    |   |-- plugin-detekt.properties  binds detekt.yml
    |   |-- detekt.yml             detekt's configuration
    |   |-- plugin-ktlint.properties  binds .editorconfig for ktlint's checks
    |   |-- plugin-ktlintformat.properties  binds it for ktlint's formatting
    |   |-- .editorconfig          ktlint's rules
    |-- ktfmt/plugin-ktfmt.properties  runs ktfmt, under a profile
    |-- jenesis-ktfmt.properties   that profile's settings: no ktlint formatting
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.kt

How the linters are wired
-------------------------

`jenesis.plugins.properties` names both linters at the hook point `check`, as the
Java demo names Checkstyle and PMD:

    detekt+check=build.jenesis.plugin.detekt
    ktlint+check=build.jenesis.plugin.ktlint@check

The ktlint plugin provides a checker and a formatter, and `@check` selects the
checker. Each `plugin-<name>.properties` binds the file its tool reads into the
input `rules`:

    @rules=../build.jenesis/detekt.yml
    @rules=../build.jenesis/.editorconfig

detekt runs with `--config detekt.yml`; ktlint receives `.editorconfig` as
`--editorconfig`, so its rules apply as defaults wherever the file lives (an
`.editorconfig` beside the sources still wins for the properties it sets, per
editorconfig layering). Both lint the `.kt` sources in the module's `verify` phase,
which no other module waits for, and write an XML report under their step's output folder. By default they are
report-only; `-Djenesis.source.detekt=strict` and `-Djenesis.source.ktlint=strict`
fail the build on their violations instead, as in the Java code-quality demo.

Formatting: verify, and how to reformat
---------------------------------------

ktlint formats as well, as the plugin `ktlintformat` at the hook point `format`,
whose `@format` selects the formatter of the ktlint plugin:

    ktlintformat+format=build.jenesis.plugin.ktlint@format

Its `plugin-ktlintformat.properties` binds the same `.editorconfig`. It runs in
verify mode: a normal build fails if a `.kt` file is not already
ktlint-formatted, but never rewrites it. To apply ktlint and rewrite the sources
in place, run the build with the rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

This is the Kotlin equivalent of the `google-java-format` rewrite shown in
`../demo-37-java-quality`. The formatters for the other JVM languages work the
same way.

Formatting with ktfmt instead
-----------------------------

The ktfmt plugin formats the Kotlin sources with ktfmt rather than ktlint:

    ktfmt+format=build.jenesis.plugin.ktfmt

Its `plugin-ktfmt.properties` may be empty, which formats in ktfmt's default
Meta style, or name ktfmt's options:

    style=kotlinlang      meta, google or kotlinlang
    imports=sort          keeps unused imports; sort,remove, the default,
                          removes them as well

Two formatters would fight over the same sources, so the `ktfmt` profile, whose
`build.jenesis/ktfmt/` folder adds the `plugin-ktfmt.properties`, switches
ktlint's formatting off in `jenesis-ktfmt.properties` with
`jenesis.plugin.ktlintformat=false`, while ktlint still lints:

    java -Djenesis.make.profiles=ktfmt build/jenesis/Make.java

ktfmt verifies like the other formatters and rewrites under the same switch.

Pinning
-------

The Kotlin compiler is pinned in its own `kotlin:tool` group, locked independently of
the `kotlin.stdlib` the module ships against (see `../demo-46-kotlin`). detekt,
ktlint and ktfmt each resolve a floating `RELEASE` in their own `<name>:tool` group, and a
plugin's own jars are pinned in the group of its name; run
`java build/jenesis/Make.java pin` to record them with checksums when you want
a fully reproducible tool chain.
