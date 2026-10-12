Reproducible build demo
=======================

Build a jar and check it against a SHA-256 recorded in the demo. The digest was
taken once, on one machine. CI runs the same check on Linux, macOS and Windows,
each on whatever update of JDK 25 its runner provides, so a match on every runner
shows that nothing about the machine that builds the jar - its operating system,
its time zone, its umask or its clock - reaches the jar.

The module resolves nothing, and nothing in it is there for the sake of the check:
the same bytes are the default, not a setting.

Run it
------

From this directory:

    java build/Demo.java

which builds the module and prints

    demo.reproducible-0-SNAPSHOT.jar has the recorded SHA-256 6da78ffee2facbe32ce1693c001c2b678b47c325369a32f691eaadfab1495c25

Layout
------

    demo/demo-71-reproducible
    |-- build/Demo.java            builds, then compares the jar's SHA-256 with the recorded one
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

Changing the recorded time
--------------------------

The time is part of the bytes. Name another one and the check fails, reporting the
digest the jar has now:

    java -Djenesis.archive.timestamp=2026-01-01T00:00:00Z build/Demo.java

A release can record the time of its commit instead, with
`-Djenesis.archive.timestamp=$(git log -1 --format=%cI)`.

When the digest does not match
------------------------------

A deliberate change changes the digest: to the sources, to the module's
documentation comment (whose first paragraph becomes the manifest's
`Implementation-Title` and second the SBOM's description), or to what Jenesis
writes into a jar. Record the new digest in `build/Demo.java`. A
mismatch on only one runner means something about that runner reached the jar;
`unzip -Z -v` of the jar from two machines, side by side, shows which entry
differs.

Building twice and comparing
----------------------------

The recorded digest checks the one jar. To check every step of the build, build
it twice:

    java -Djenesis.project.reproduce=target-reproduced build/jenesis/Make.java

builds into `target` as usual, then builds the same again into
`target-reproduced`, which must be absent or empty, consulting and filling no
build cache. It compares what every step wrote in the two builds and ends with

    The build reproduced: 19 steps wrote the same output into target-reproduced as into target - fingerprint SHA-256:401a4f13dd00c7b7d7eeee6579468ca7200b1e62fa3380a036c1e1a678f0bb49

and deletes `target-reproduced` again. The second build writes into another
folder on purpose: a step that records where it writes differs between the two
builds, as it would on another machine. The fingerprint is a digest of what every
step wrote, so two machines that print the same one built the same bytes in every
step, not only in the jar.

Some outputs carry a time by nature: JaCoCo's coverage data, the report of PIT
and those of SpotBugs, PMD, CodeNarc and japicmp. The steps that write them are left out of the
comparison and of the fingerprint, as are the steps that are handed what they
wrote, and the summary counts them.

`build/Planted.java` builds the same module with one step added. It copies the
sources and writes the current time beside them, into
`demo/reproducible/built.txt`, which the jar then holds:

    java -Djenesis.project.reproduce=target-reproduced build/Planted.java

fails, naming the step that wrote a different output from the same input first,
and the steps that were handed that output after it:

    The build did not reproduce: building again into target-reproduced wrote a different output than building into target
    Caused by these steps, whose output differs although they were handed the same input in both builds, in the files named:
      build/modules/compose/module/module-sources/produce/assemble/stamp: sources/demo/reproducible/built.txt
    As a consequence, these steps were handed a different input:
      build/modules/compose/module/module-sources/produce/assemble/binary/artifacts/jar
      build/modules/compose/module/module-sources/produce/assemble/binary/classes
      build/modules/compose/module/module-sources/produce/assemble/binary/compiled/compile/javac

Both folders are kept, so `diff -r` of the `output` folder of a step named in
`target` and in `target-reproduced` shows what differs. Delete
`target-reproduced` before checking again.
