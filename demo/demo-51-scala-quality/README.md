Scala code-quality demo
=======================

The Scala counterpart to `../demo-37-java-quality`, following the Scala demo that
precedes it. A small Scala module is linted by Scalastyle and scalafmt, and
scalafmt doubles as a formatter that verifies the source layout. As elsewhere,
there is no build script: each tool is an official plugin the build resolves by
itself where the tool's own file - `scalastyle-config.xml`, `.scalafmt.conf` - is
found in the `build.jenesis/` configuration folder.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Scala module (resolving the pinned Scala compiler) and runs
every tool it is configured for. The first build downloads the
compiler and the tools, so it takes a while.

Layout
------

    demo/demo-51-scala-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   |-- scalastyle-config.xml  switches Scalastyle on: its configuration
    |   `-- .scalafmt.conf         switches scalafmt on: its configuration
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.scala

How the linters are wired
-------------------------

As in the Java and Kotlin code-quality demos, each tool's own file switches it
on by itself where it lies in a configuration folder. A file kept elsewhere is
named from a `scalastyle.properties` or `scalafmt.properties` as
`configuration=<path>`, relative to the properties; where those are found, the
conventional file is not read, and properties without `configuration` fail the
build, naming the key to add.

Scalastyle runs with `--config scalastyle-config.xml`; scalafmt runs against
`.scalafmt.conf`. Both inspect the `.scala` sources in the module's `verify`
phase and write a report under their step's output folder. The
`.scalafmt.conf` pins the formatter's own `version`, so the formatting is
reproducible no matter which scalafmt release resolves. Both linters are
report-only by default; `-Djenesis.source.scalastyle=strict` and
`-Djenesis.source.scalafmt=strict` fail the build on their findings instead,
and `ignore` skips them, as in the Java code-quality demo.

Formatting: verify, and how to reformat
---------------------------------------

The same `.scalafmt.conf` switches scalafmt on as a formatter as well. It runs in verify mode: a normal build fails if a
`.scala` file is not already formatted, but never rewrites it. To apply scalafmt
and rewrite the sources in place, run the build with the rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

This mirrors the google-java-format and ktlint rewrites in
`../demo-37-java-quality` and `../demo-47-kotlin-quality`.
`-Djenesis.format.scalafmt=false` switches scalafmt's formatting off, while
scalafmt still checks.

Pinning
-------

The Scala compiler is pinned in its own `scala:tool` group (see `../demo-50-scala`).
Scalastyle and scalafmt each resolve a floating `RELEASE` in their own `<name>:tool`
group, and a plugin's own jars are pinned in the group of its name;
run `java build/jenesis/Make.java pin` to record them with checksums when you
want a fully reproducible tool chain.
