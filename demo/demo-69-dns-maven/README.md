DNS Maven resolution demo
=========================

The same lookup as `../demo-68-dns-module`, for a Maven dependency. A groupId is a
reversed domain as well, so `build.jenesis:build.jenesis` belongs to
`jenesis.build`, whose DNS names a Maven location for the group before any Maven
remote is asked. This demo depends on Jenesis itself through its `pom.xml` and
downloads the jar from the Jenesis release on GitHub.

Build and run it
----------------

From this directory:

    java -Djenesis.print.fetch=true build/jenesis/Execute.java

which prints where each file came from and then what the program found:

    [FETCHED]   https://repo1.maven.org/maven2/build/jenesis/build.jenesis/0.15.4/build.jenesis-0.15.4.pom
    [FETCHED]   https://github.com/jenesis/jenesis/releases/download/v0.15.4/build.jenesis-0.15.4.jar
    jenesis-make from build.jenesis-0.15.4.jar

A file the location does not hold is asked of the Maven remotes, and the 0.15.4
release attaches no POM, so the POM still comes from Maven Central; a release that
attaches its POM serves both. Each download is checked against the pin in
`pom.xml`. As in the module demo, a pinned jar is reused from `.jenesis/artifacts/`
once downloaded; delete that folder and `target/` to watch the download again.

Layout
------

    demo/demo-69-dns-maven
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
    |-- jenesis.properties     jenesis.dns.enabled=true
    |-- pom.xml                depends on build.jenesis:build.jenesis:0.15.4, pinned
    `-- sources
        `-- demo/dns/Tools.java    prints the jenesis-make tool and the jar it came from

The record
----------

A group's record shares the name a module's record has: `_java.` followed by
the groupId reversed, walking up to shorter names, so one record at the domain
covers every group below it. It starts with `maven=` instead, beside the module
records of the same name. `jenesis.build` publishes:

    _java.jenesis.build. TXT "maven=https://github.com/jenesis/jenesis/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type}"

The location takes the same two forms as a module's:

  * a **template**, as here, names each file. `{groupId}`, `{groupPath}` (the
    groupId with slashes), `{artifactId}`, `{version}`, `{-classifier}` and `{type}`
    are filled in, `{type}` being `pom` for the POM and `jar.asc` for a signature.
    A template serves the files of the versions you name and no Maven metadata, so
    a version range is resolved by the Maven remotes alone.
  * a **root**, a URI without placeholders, is a traditional Maven repository: Maven
    Central, a Nexus or Artifactory, or a folder on GitHub Pages in the Maven
    layout. It is read as `jenesis.maven.uri` reads a remote, with its checksums and
    your local Maven repository, and its metadata is merged with that of the Maven
    remotes, so a version range or the newest release sees the versions of both:

        _java.example.com. TXT "maven=https://maven.example.com/releases/"

`since=<version>` after the location leaves earlier versions to the Maven remotes,
and `suffixes=<suffix>[,<suffix>...]` leaves them the versions of other qualifiers,
so a location that holds releases only, such as a GitHub release, says so and is
never asked for a `-SNAPSHOT`:

    _java.jenesis.build. TXT "maven=https://github.com/jenesis/jenesis/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type} suffixes=none"

DNSSEC vouches for the record, and `jenesis.dns.enabled`, `jenesis.dns.uri` and
`jenesis.dns.secure` mean what the module demo describes.
