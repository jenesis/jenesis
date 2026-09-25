Build profiles demo
===================

Configure a build with *profiles*: property files that switch features on as a
named set, instead of repeating long `-D` flag lists. A profile named `<name>`
lives in a `jenesis-<name>.properties` file at the project root; selecting one
turns on whatever properties it declares. This demo ships a `release` profile that, in one switch,
attaches source jars and chains to a `supply-chain` profile that enforces strict
dependency pinning.

There is no profile registry and no plugin: a profile is selected by naming it in
`jenesis.make.profiles`, and profiles compose by chaining to each other.

Build it
--------

A plain build is the development build - no extras:

    java build/jenesis/Make.java

Select the `release` profile to build for publication:

    java -Djenesis.make.profiles=release build/jenesis/Make.java stage

The release build additionally stages a `-sources.jar` next to the jar and enforces
strict dependency pinning, ready for `export`. The CycloneDX SBOM is emitted
automatically on every build (it is on by default), so the release jar carries it too.

The whole command line can live in a file as well:

    java build/jenesis/Make.java @release.args

`@<file>` stands for the arguments the file holds, one or more per line, `#` starts a
comment that runs to the end of the line, and quotes hold what would otherwise split on
whitespace. `@@<text>` is an argument that begins with an `@` rather than a file, and a
file names no further file. This is the argument file the JDK's own tools read, and the
one shipped here holds the run above:

    # the release run: the profile it switches on, and the selector it ends with
    -Djenesis.make.profiles=release
    stage

A profile names the settings that belong together; an argument file names the whole run,
settings and selectors alike. Both exist so a long command line is written once, and they
compose: the file above selects a profile.

Layout
------

    demo/demo-46-profiles
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- pom.xml                          pins commons-lang3
    |-- jenesis-release.properties       the release profile (sources + chains to supply-chain)
    |-- jenesis-supply-chain.properties  a profile that enforces strict dependency pinning
    |-- release.args                     the release run as an argument file
    `-- sources
        `-- profiles
            `-- Sample.java

How profiles work
-----------------

Property files feed the same `jenesis.*` system properties the command line sets,
so anything you can pass with `-D` can live in a profile. They are loaded before
the build is configured, by the `main` launcher:

- `jenesis.properties` at the project root is the **base file**. It is always
  loaded when present, and it is optional - this demo ships none.
- `jenesis.make.profiles` is a **comma-separated list of profile names** to
  load. Each name `<name>` resolves to a `jenesis-<name>.properties` file relative
  to the folder of the file that names it (a `.properties` suffix on the name is
  ignored, so `release` and `release.properties` both name the `jenesis-release.properties`
  file). The properties file is optional: a missing one is skipped, not an error.
- A profile **also designates a configuration folder**. For each selected profile
  `<name>`, a `<name>/` subfolder under each configuration location is searched
  ahead of the location itself (module-local before project-wide), so a profile can
  carry its own `checkstyle.xml`, `packaging.properties`, and so on. A profile may
  therefore exist as just a properties file, just a folder, or both.
- Profiles **chain**: any loaded file may itself set `jenesis.make.profiles`
  to pull in more, transitively, until everything is loaded. Here `release`
  chains to `supply-chain`:

      jenesis-release.properties        jenesis.project.sources=true
                                        jenesis.make.profiles=supply-chain
      jenesis-supply-chain.properties   jenesis.dependency.pin=strict

  so selecting `release` also applies `supply-chain`.
- A user-global `jenesis.properties` is loaded for **every** project and outranks
  what a project sets - your machine's word on how it builds. It lives in `~/.jenesis/`;
  `-Djenesis.make.global` moves that folder (default `$HOME`) or, set to an empty
  string, switches it off, and a missing file is ignored. Only the command line
  sets it, so a project can never choose which file holds your own defaults. It may declare its own
  profiles too, relative to its `.jenesis` folder.
- **Precedence**, highest first: an explicit `-D` on the command line, then your
  user-global profiles, then your user-global `jenesis.properties`, then the
  project's profiles, then the project's `jenesis.properties`. So
  `-Djenesis.project.sources=false` on a release build switches the source jar back
  off (the command line always wins), selecting the `release` profile overrides
  whatever the project's base `jenesis.properties` set, and a line in your own file
  overrides both - which is why a project's file may not set what decides which
  programs the build runs, what it trusts or what it writes outside itself.

What the release build produces
-------------------------------

    target/stage/maven/output/.../profiles-demo/1.0.0/profiles-demo-1.0.0.jar
    target/stage/maven/output/.../profiles-demo/1.0.0/profiles-demo-1.0.0-cyclonedx.json   (emitted by default)
    target/stage/maven/output/.../profiles-demo/1.0.0/profiles-demo-1.0.0-sources.jar      (release only)

The plain build produces the jar and its SBOM (the SBOM is on by default); the
`release` profile adds the source jar and enforces strict dependency pinning, without
changing a single command-line flag beyond selecting the profile.
