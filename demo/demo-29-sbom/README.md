Software bill of materials demo
===============================

Emit a CycloneDX software bill of materials (SBOM) for a project. A small Maven
project depends on one library; every build records every resolved component, its
version, content hash, and declared license into a CycloneDX document. There is no
build script and no SBOM plugin to configure: the SBOM is emitted by default, and an
optional `sbom.properties` file selects the format (or turns it off).

This is a supply-chain counterpart to the dependency-graph view (the
`dependencies` selector): where that prints the graph, this freezes it into a
publishable, machine-readable artifact.

Build it
--------

From this directory:

    java build/jenesis/Make.java

A plain build emits the SBOM: it is on by default. The project ships an optional
`sbom.properties` that sets `format=json`, which is also the default when no file is
present. Set `format=xml` for the XML form instead, or `format=none` to turn the SBOM
off; any other value fails the build. To suppress the SBOM without a file, pass
`-Djenesis.sbom.cyclonedx=false` (a default-`true` boolean override).

Layout
------

    demo/demo-29-sbom
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- LICENSE                    a placeholder license text, placed in every jar
    |-- jenesis.properties         jenesis.project.resources=LICENSE:META-INF/LICENSE
    |-- pom.xml                    project metadata (name, license, developers, scm) + pins commons-lang3
    |-- sbom.properties            format=json (also xml, or none to disable)
    `-- sources
        `-- sbom
            `-- Sample.java        uses commons-lang3

What the build emits
--------------------

Every build writes a CycloneDX document per module from the dependencies it
resolved, their content hashes and the licenses it captured, with no external
tool involved. It is placed three ways, each for a different consumer:

- **Embedded in the jar**, at `META-INF/sbom/<artifact>.cdx.json`, so the bill of
  materials travels inside the artifact. The jar's `MANIFEST.MF` records
  `Sbom-Format: CycloneDX` and `Sbom-Location` so a consumer can find it.
- **As a report**, collected on `stage` into `target/stage/reports/sbom/<module>/`
  alongside the other build reports.
- **As a Maven attachment**, when a Maven repository is staged (the `MAVEN` and
  `MODULAR_TO_MAVEN` layouts): `stage` drops `<artifact>-<version>-cyclonedx.json`
  next to the pom and jar, so `export` publishes it to Maven Central as the
  conventional CycloneDX attached artifact.

Where it lands
--------------

    target/build/.../assemble/binary/artifacts/jar/output/artifacts/classes.jar   (embeds META-INF/sbom/)
    target/stage/reports/sbom/<module>/sbom-demo-1.0.0.cdx.json                    (the report)
    target/stage/maven/output/.../sbom-demo/1.0.0/sbom-demo-1.0.0-cyclonedx.json   (the attachment)

The document is CycloneDX 1.6. Because the project depends on `commons-lang3`,
the SBOM lists it as a component carrying its `pkg:maven/...` package URL, its
`SHA-256` hash, and its `Apache-2.0` license, with a `dependsOn` relationship
back to the project.

The `metadata.component` (the project itself) is described from the POM: its
`description`, its `Apache-2.0` license, its developers (rendered as CycloneDX
`authors`), and its homepage and source repository (rendered as `website` and
`vcs` external references). Jenesis fills in only what the project actually
declares: a module that names no version is described without one rather than with
the placeholder its POM has to carry, since a version is not required for a valid
SBOM.

The document also carries a `serialNumber` (`urn:uuid:...`) derived
deterministically from the document's own content, so a reproducible build
reproduces the exact same SBOM, serial number included. No creation `timestamp`
is written, because that cannot be made deterministic.

The single executable jar of `demo-07-java-pom-executable` and
`demo-08-java-modular-executable` carries a document of its own at the same
`META-INF/sbom/<artifact>.cdx.json`, named by its own `MANIFEST.MF`. It lists what
the module's document lists and adds the launcher the jar shades, as a dependency
of the project, which it describes as an `application` rather than a `library`. A
launcher the module already depends on at the same version is listed once; at another
version, both are listed. The module's own document stays in its jar, inside the
executable one.

The coordinate in the jar
-------------------------

The jar also carries the POM it is published with, and its coordinate, where Maven
puts them in every jar it builds:

    META-INF/maven/build.jenesis.demo/sbom-demo/pom.xml
    META-INF/maven/build.jenesis.demo/sbom-demo/pom.properties   groupId, artifactId, version

Tools that find a jar inside an image or an archive - a scanner such as Syft, or
GraalVM's own SBOM of a native image - identify it by these files, so a jar built by
Jenesis is recognised as `pkg:maven/build.jenesis.demo/sbom-demo@1.0.0` wherever it
ends up. A module built under the pure modular layout has no Maven coordinate and
carries neither.

The license text in the jar
---------------------------

The SBOM names the project's license; the text of the license travels as a file.
`jenesis.project.resources` places files of the project among the resources of every
module, each at the path after its colon, so one line puts this demo's `LICENSE` into
every jar it builds:

    jenesis.project.resources=LICENSE:META-INF/LICENSE

The jar then carries `META-INF/LICENSE` beside `META-INF/sbom/`, and editing
`LICENSE` builds the jars again. More files follow with commas, and a folder is
placed as a folder: `NOTICE:META-INF/NOTICE,licenses:META-INF/licenses`. A module
that brings a resource of its own at the same path fails the build rather than
losing one of the two.

Pinning
-------

The single dependency is pinned in the POM the usual way, so the component hash
recorded in the SBOM is reproducible. Nothing is resolved or downloaded for the
SBOM itself.
