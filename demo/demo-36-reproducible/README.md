Reproducible build demo
=======================

Build the module twice, into two folders, and compare what every step of the two
builds wrote. A build that reproduces wrote the same bytes in every step, and a
fingerprint over all of them says so in one line that two machines can compare.

The module resolves nothing, and nothing in it is there for the sake of the check:
the same bytes are the default, not a setting.

Building twice and comparing
----------------------------

From this directory:

    java -Djenesis.project.reproduce=target-reproduced build/jenesis/Make.java

builds into `target` as usual, then builds the same again into
`target-reproduced`, which must be absent or empty, consulting and filling no
build cache. It compares what every step wrote in the two builds and ends with

    The build reproduced: 20 steps wrote the same output into target-reproduced as into target - fingerprint SHA-256:401a4f13dd00c7b7d7eeee6579468ca7200b1e62fa3380a036c1e1a678f0bb49

and deletes `target-reproduced` again. The second build writes into another
folder on purpose: a step that records where it writes differs between the two
builds, as it would on another machine. The fingerprint is a digest of what every
step wrote, so two machines that print the same one built the same bytes in every
step, not only in the jar.

Layout
------

    demo/demo-36-reproducible
    |-- build/Planted.java         the same build with a step added that writes the current time
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    `-- sources
        |-- module-info.java       demo.reproducible, compiled for release 25
        `-- demo/reproducible
            `-- Greeting.java

What keeps the bytes the same
-----------------------------

- **Entry order.** The `jar` tool sorts the files of every folder it adds, so the
  order does not follow the order in which a file system lists them.
- **Time.** Every entry records the same time, `1980-02-01T00:00:00Z`. A zip entry
  cannot record a time before 1980, and a month after that limit no time zone
  reads it as 1979.
- **Permissions.** No entry records Unix permissions, so the umask of the machine
  does not matter.
- **The compiled module declaration.** Without `--release`, `javac` writes the
  JDK's update, such as `25.0.3`, into `module-info.class` for `java.base`. The
  module declares `@jenesis.release 25`, so it records `25` on every update and
  vendor of JDK 25; a module that declares no release compiles for the release of
  the JDK running the build, with the same effect.
- **Generated entries.** The manifest and the embedded SBOM carry no time and no
  machine name, and are written with the same line endings on every operating
  system.

The recorded time is part of the bytes, so naming another one with
`-Djenesis.archive.timestamp=2026-01-01T00:00:00Z` prints another fingerprint. A
release can record the time of its commit instead, with
`-Djenesis.archive.timestamp=$(git log -1 --format=%cI)`.

When a step does not reproduce
------------------------------

`build/Planted.java` builds the same module with one step added. It copies the
sources and writes the current time beside them, into
`demo/reproducible/built.txt`, which the jar then holds:

    java -Djenesis.project.reproduce=target-reproduced build/Planted.java

The comparison is a debugging aid, so it does not fail the build. It names the
step that wrote a different output from the same input first, and the steps that
were handed that output after it:

    The build did not reproduce: 4 of 21 steps wrote a different output into target-reproduced than into target, which is kept to compare - fingerprint SHA-256:...
    Caused by these steps, whose output differs although they were handed the same input in both builds, in the files named:
      build/modules/compose/module/build-module-sources/produce/assemble/stamp: sources/demo/reproducible/built.txt
    As a consequence, these steps were handed a different input:
      build/modules/compose/module/build-module-sources/produce/assemble/binary/artifacts/jar
      build/modules/compose/module/build-module-sources/produce/assemble/binary/classes
      build/modules/compose/module/build-module-sources/produce/assemble/binary/compiled/compile/javac

Some outputs differ by nature, such as a test run recording coverage or a report
that records when it was written; they are listed the same way.
`-Djenesis.print.reproduce=false` prints the first line alone. Both folders are
kept, so `diff -r` of the `output` folder of a step named in `target` and in
`target-reproduced` shows what differs. Delete `target-reproduced` before checking
again.
