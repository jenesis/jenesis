Maven discovery demo
====================

The same lookup as `../demo-68-discovery-module`, for a Maven dependency. A groupId
is a reversed domain as well, so `build.jenesis:build.jenesis` belongs to
`jenesis.build`, whose file names a Maven location for the group before any Maven
remote is asked. This demo depends on Jenesis itself through its `pom.xml` and
downloads the jar from the Jenesis release on GitHub. Like the module demo, it names
no Maven remote, so nothing but the domain can answer.

Build and run it
----------------

From this directory:

    java -Djenesis.print.fetch=true build/jenesis/Execute.java

which prints where each file came from and then what the program found:

    [FETCHED]   https://github.com/jenesis/jenesis/releases/download/v0.15.4/build.jenesis-0.15.4.jar
    jenesis-make from build.jenesis-0.15.4.jar

`jenesis.properties` empties `jenesis.maven.uri` and `jenesis.module.uri`, which
name no remote at all, so a file the location does not hold is found nowhere -
there is no fallback. The 0.15.4 release attaches no POM, so the dependency is
resolved without one, as an artifact without dependencies of its own; a release that
attaches its POM serves that as well. Remove the two lines and what the location
does not hold comes from Maven Central again. Each download is checked against the
pin in `pom.xml`. As in the module demo, a pinned jar is reused from `.jenesis/artifacts/`
once downloaded; delete that folder and `target/` to watch the download again.

Layout
------

    demo/demo-69-discovery-maven
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
    |-- jenesis.properties     discovery on, and no repository
    |-- pom.xml                depends on build.jenesis:build.jenesis:0.15.4, pinned
    `-- sources
        `-- demo/discovery/Tools.java    prints the jenesis-make tool and the jar it came from

The maven key
-------------

A group is looked up in the same file a module is, the file of the shortest domain
its groupId reverses to that publishes one, so one file at the domain covers every
group below it. Its key is `maven`, beside the `module` and `moduletomaven` keys of
the same file. `jenesis.build` publishes:

    maven=https://github.com/jenesis/jenesis/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type}
    maven.latest=https://github.com/jenesis/jenesis/releases/latest/download/{artifactId}.pom
    maven.suffixes=none

The location takes the same two forms as a module's:

  * a **template**, as here, names each file. `{groupId}`, `{groupPath}` (the
    groupId with slashes), `{artifactId}`, `{version}`, `{-classifier}` and `{type}`
    are filled in, `{type}` being `pom` for the POM and `jar.asc` for a signature.
    A template lists no versions; `maven.latest`, the link the module demo
    describes for `module.latest`, names the newest one, and the template then
    answers Maven metadata naming that version alone, merged with that of the Maven
    remotes, so the newest release or a version range sees it beside theirs.
    Without the link, the Maven remotes alone name versions.
  * a **root**, a URI without placeholders, is a traditional Maven repository: Maven
    Central, a Nexus or Artifactory, or a folder on GitHub Pages in the Maven
    layout. It is read as `jenesis.maven.uri` reads a remote, with its checksums and
    your local Maven repository, and its metadata is merged with that of the Maven
    remotes, so a version range or the newest release sees the versions of both:

        maven=https://maven.example.com/releases/

`maven.since` leaves earlier versions to the Maven remotes, and `maven.suffixes`
leaves them the versions of other qualifiers, so a location that holds releases
only, such as a GitHub release, says so with `maven.suffixes=none` and is never
asked for a `-SNAPSHOT`. The rules of the file, what fails the build and the
`jenesis.repository.discovery` setting are those the module demo describes.
