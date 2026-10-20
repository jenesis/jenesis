Kotlin code-quality demo
========================

The Kotlin counterpart to `../demo-37-java-quality`, following the Kotlin demo
that precedes it. A small pure-Kotlin module is linted by detekt and ktlint, and
ktlint doubles as a formatter that verifies the source layout. As with the Java
demo, no build script is involved: each tool is an official plugin the build
resolves by itself where the tool's own file - `detekt.yml`, `.editorconfig` - is
found in the `build.jenesis/` configuration folder.

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
    |-- jenesis-ktfmt.properties   the ktfmt profile's settings: no ktlint formatting
    |-- build.jenesis/
    |   |-- detekt.yml             switches detekt on: its configuration
    |   |-- .editorconfig          switches ktlint on: its rules
    |   `-- ktfmt/
    |       `-- ktfmt.properties   switches ktfmt on, under a profile
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.kt

How the linters are wired
-------------------------

As `pmd.xml` does in the Java demo, each tool's own file switches it on by
itself where it lies in a configuration folder. A project that keeps its
`.editorconfig` elsewhere, or under another name, names it from a
`ktlint.properties` instead, relative to that file:

    configuration=../config/.editorconfig

Where such properties are found, the `.editorconfig` of the configuration folder
is not read, and properties without `configuration` fail the build, naming the
key to add. An `.editorconfig` at the project root, where an editor reads it,
switches nothing on. `detekt.properties` names `detekt.yml` alike.

detekt runs with `--config detekt.yml`; ktlint receives `.editorconfig` as
`--editorconfig`, so its rules apply as defaults wherever the file lives (an
`.editorconfig` beside the sources still wins for the properties it sets, per
editorconfig layering). Both lint the `.kt` sources in the module's `verify`
phase, which no other module waits for, and write an XML report under their
step's output folder. By default they are report-only;
`-Djenesis.strict.detekt=true` and `-Djenesis.strict.ktlint=true` fail the
build on their violations instead, and `-Djenesis.check.detekt=false` and
`-Djenesis.check.ktlint=false` skip them, as in the Java code-quality demo.

Formatting: verify, and how to reformat
---------------------------------------

The same `.editorconfig` switches ktlint on as a formatter as well. It runs in verify mode: a normal build fails if a `.kt`
file is not already ktlint-formatted, but never rewrites it. To apply ktlint and
rewrite the sources in place, run the build with the rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

This is the Kotlin equivalent of the google-java-format rewrite shown in
`../demo-37-java-quality`. `-Djenesis.format.ktlint=false` switches ktlint's
formatting off, while ktlint still lints.

Formatting with ktfmt instead
-----------------------------

ktfmt formats the Kotlin sources rather than ktlint. It has no file of its own:
a `ktfmt.properties` switches it on, and may be empty, which formats in ktfmt's
default Meta style, or name ktfmt's options:

    style=kotlinlang      meta, google or kotlinlang
    imports=sort          keeps unused imports; sort,remove, the default,
                          removes them as well

The demo's `ktfmt.properties` holds `style=kotlinlang` and sits in the profile
folder `build.jenesis/ktfmt/`. Two formatters would fight over the same
sources, so a `ktfmt.properties` found where ktlint formats fails the build,
asking for `jenesis.format.ktlint=false`. The profile's
`jenesis-ktfmt.properties` sets exactly that, so ktfmt formats while ktlint
still lints:

    java -Djenesis.make.profiles=ktfmt build/jenesis/Make.java

ktfmt verifies like the other formatters, rewrites under the same switch, and
is switched off by `-Djenesis.format.ktfmt=false`.

Pinning
-------

The Kotlin compiler is pinned in its own `kotlin:tool` group, locked independently of
the `kotlin.stdlib` the module ships against (see `../demo-46-kotlin`). detekt,
ktlint and ktfmt each resolve a floating `RELEASE` in their own `<name>:tool` group, and a
plugin's own jars are pinned in the group of its name; run
`java build/jenesis/Make.java pin` to record them with checksums when you want
a fully reproducible tool chain.
