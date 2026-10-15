Scala code-quality demo
=======================

The Scala counterpart to `../demo-37-java-quality`, following the Scala demo that
precedes it. A small Scala module is linted by the Scalastyle and scalafmt
plugins, and scalafmt doubles as a formatter that verifies the source layout. As
elsewhere, there is no build script: `jenesis.plugins.properties` names the
plugins, and each runs where its `plugin-<name>.properties` is found in the
`build.jenesis/` configuration directory.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis compiles the Scala module (resolving the pinned Scala compiler) and runs
every tool it is configured for. The first build downloads the
compiler and the tools, so it takes a while.

Layout
------

    demo/demo-50-scala-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties names the Scala, Scalastyle and scalafmt plugins
    |-- build.jenesis/
    |   |-- plugin-scalastyle.properties  binds scalastyle-config.xml
    |   |-- scalastyle-config.xml  Scalastyle's configuration
    |   |-- plugin-scalafmt.properties    binds .scalafmt.conf for scalafmt's checks
    |   |-- plugin-scalafmtformat.properties  binds it for scalafmt's formatting
    |   |-- .scalafmt.conf         scalafmt's configuration
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.scala

How the linters are wired
-------------------------

`jenesis.plugins.properties` names both linters at the hook point `check`:

    scalastyle+check=build.jenesis.plugin.scalastyle
    scalafmt+check=build.jenesis.plugin.scalafmt@check

The scalafmt plugin provides a checker and a formatter, and `@check` selects the
checker. Each `plugin-<name>.properties` binds the file its tool reads into the
input `rules`:

    @rules=../build.jenesis/scalastyle-config.xml
    @rules=../build.jenesis/.scalafmt.conf

Scalastyle runs with `-c scalastyle-config.xml`; scalafmt runs against
`.scalafmt.conf`. Both inspect the `.scala` sources in parallel with
compilation and write a report under their step's output folder. The
`.scalafmt.conf` pins the formatter's own `version`, so the formatting is
reproducible no matter which scalafmt release resolves. Both linters are
report-only by default; `-Djenesis.source.scalastyle=strict` and
`-Djenesis.source.scalafmt=strict` fail the build on their findings instead,
as in the Java code-quality demo.

Formatting: verify, and how to reformat
---------------------------------------

scalafmt formats as well, as the plugin `scalafmtformat` at the hook point
`format`, whose `@format` selects the formatter of the scalafmt plugin:

    scalafmtformat+format=build.jenesis.plugin.scalafmt@format

Its `plugin-scalafmtformat.properties` binds the same `.scalafmt.conf`. It runs in
verify mode: a normal build fails if a `.scala` file is not already formatted,
but never rewrites it. To apply scalafmt and rewrite the sources in place, run
the build with the rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

This mirrors the `google-java-format` and `ktlint` rewrites in
`../demo-37-java-quality` and `../demo-46-kotlin-quality`.

Pinning
-------

The Scala compiler is pinned in its own `scala:tool` group (see `../demo-49-scala`).
Scalastyle and scalafmt each resolve a floating `RELEASE` in their own `<name>:tool`
group, and a plugin's own jars are pinned in the group of its name;
run `java build/jenesis/Make.java pin` to record them with checksums when you
want a fully reproducible tool chain.
