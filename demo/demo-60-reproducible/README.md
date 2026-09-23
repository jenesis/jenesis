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

    classes.jar has the recorded SHA-256 9ffd4b090652995f57c15b8aa2f8080b8192d067811e28a46477eb3c8b275df1

Layout
------

    demo/demo-60-reproducible
    |-- build/Demo.java            builds, then compares the jar's SHA-256 with the recorded one
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
- **Generated entries.** The manifest, `META-INF/NOTICE` and the embedded SBOM
  carry no time and no machine name, and are written with the same line endings
  on every operating system.

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
documentation comment (its first sentence becomes the NOTICE, the rest the SBOM's
description), or to what Jenesis writes into a jar. Record the new digest in `build/Demo.java`. A
mismatch on only one runner means something about that runner reached the jar;
`unzip -Z -v` of the jar from two machines, side by side, shows which entry
differs.
