API-compatibility demo
======================

Compare the jar you just built against the one you released last, with
[japicmp](https://siom79.github.io/japicmp/), inferred from a `japicmp.properties` file. japicmp reads
byte code, not sources, so it sees exactly what a caller compiled against the old jar would see: a
removed method, a narrowed return type, a field that changed its modifiers. No build script and no
japicmp configuration beyond the one file are needed.

The `japicmp.properties` here names nothing at all. That is the point: with no baseline declared,
japicmp compares against the last release of **this module's own coordinate**, so every build measures
the change you are about to publish against the release before it.

Build it
--------

From this directory:

    java build/Demo.java

The first build downloads japicmp, so it takes a while; later builds reuse the cache.

Why an entry point instead of `Make.java`
------------------------------------------

A compatibility check needs a *released* artifact to compare against, resolved by coordinate. A demo has
never published anything, so `build/Demo.java` publishes the previous release itself:

  - **Phase one** builds `released/` - the same coordinate at 1.0.0 - and stages it. A staged tree is
    already a Maven repository layout, so nothing has to be copied or rewritten to turn it into one.
    Staging produces artifacts rather than a server, though, and a floating version is answered by the
    `maven-metadata.xml` a repository serves beside them, so `Demo.java` writes the one file that
    advertises 1.0.0.
  - **Phase two** builds this project (1.1.0) with that repository prepended to Maven Central, through
    `Project.repositories(...)` rather than a `-D` flag. The baseline then resolves out of it.

A library that really publishes needs none of this: it names its repository once and the `RELEASE`
baseline resolves like any other dependency.

Layout
------

    demo/demo-37-api-compatibility
    |-- build
    |   |-- jenesis            symlink to ../../../sources/build/jenesis
    |   `-- Demo.java          publishes 1.0.0, then builds 1.1.0 against it
    |-- pom.xml                this module: build.jenesis.demo:api-compatibility:1.1.0
    |-- build.jenesis
    |   `-- japicmp.properties activates japicmp; names no baseline
    |-- released               the previous release, built and published by Demo.java
    |   |-- .jenesis.skip      keeps this project out of the outer build's module scan
    |   |-- pom.xml            the same coordinate at 1.0.0
    |   `-- sources
    |       `-- library
    |           `-- Library.java   greet(String), farewell(String)
    `-- sources
        `-- library
            `-- Library.java       adds greet(String, String)

Naming a baseline explicitly
----------------------------

Set `baseline` when the artifact to compare against is not this module's own coordinate - a vendored
fork checked against upstream, or a module that was renamed. It is read by how many slashes it carries,
not by any suffix:

    <groupId>/<artifactId>                          the latest release
    <groupId>/<artifactId>/<version>                that version
    <repository>/<groupId>/<artifactId>/<version>   served from a named repository

A module with no Maven coordinate has nothing to default to, so leaving the key out there fails with a
message naming it. The file is read per module, so a project-wide `japicmp.properties` with no
`baseline` gives every module its own coordinate; a `baseline` there would point every module at the
same artifact, so a per-module baseline belongs in that module's own configuration location.

The remaining keys map onto japicmp's own options and all default to off: `access`, `include`,
`exclude`, `format` (`xml`, `html`, or both), `ignore-missing-classes` (on by default, because the
baseline resolves without its own dependencies), `only-incompatible`, `only-modified`,
`semantic-versioning`, and the four `error-on-*` gates below. An unknown key fails the build and lists
the ones that exist. Anything japicmp accepts but the file does not model can be appended with a
`process-japicmp.properties`, like for every other forked tool.

Report-only, until you ask for a gate
-------------------------------------

Like the linters, japicmp records what it finds and keeps the build green. The report lands in the
step's own output:

    target/build/.../assemble/artifact/japicmp/compare/output/reports/japicmp/japicmp-report.xml

and a `stage` build collects it with every other report kind, one folder per module. The module's
`inventory.properties` records a `<module>.report.japicmp` entry pointing at it.

1.1.0 adds a `greet(String, String)` overload that 1.0.0 does not have, so the report marks
`library.Library` as `MODIFIED` with one `NEW` method, still binary-compatible. Turn a finding into a
failure by adding a gate:

    error-on-binary-incompatibility=true
    error-on-source-incompatibility=true
    error-on-modifications=true
    error-on-semantic-incompatibility=true

Try it: delete `farewell(String)` from `sources/library/Library.java`, add
`error-on-binary-incompatibility=true`, and rebuild. The removal breaks every caller that already
compiled against 1.0.0, so the build stops and names the change:

    E: There is at least one incompatibility: library.Library.farewell(java.lang.String):METHOD_REMOVED

Switching it off
----------------

`-Djenesis.artifact.japicmp=false` keeps `japicmp.properties` in place and skips the comparison,
the same opt-out every inferred tool has.

Pinning
-------

japicmp resolves in its own `japicmp` group, kept apart from the module's own dependencies, and floats
a `RELEASE` version until pinned. The baseline resolves in that same group, without its transitive
dependencies, because only its own byte code is compared. `java build/Demo.java pin` records that
closure with `SHA-256` checksums into the `<!--jenesis.pin-->` block of `pom.xml`.

**The baseline itself is not pinned, and should not be.** It is a jar this demo builds, so its checksum
is whatever the machine that ran `pin` produced - pin it and every other machine fails the digest. That
is the same reason the `internal-module` and `external-module` demos pin the plugin's `build.jenesis`
and `org.json` closure but never the plugin they compile themselves. A library that really publishes has
no such problem: its baseline was released by somebody, and pins like any other dependency.

`pin` does still offer a line for it, because it recognises a locally built artifact by the coordinate
and version this build produced (1.1.0) and the baseline is a different version (1.0.0). Drop that one
line if you re-run `pin`. CI therefore builds this demo with `-Djenesis.dependency.pin=versions` rather
than the `strict` every other demo uses: versions are pinned, checksums are not demanded.
