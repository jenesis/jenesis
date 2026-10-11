Publishing demo
===============

Assemble a complete, correct release bundle ready for Maven Central: the main jar
plus the `-sources.jar`, `-javadoc.jar`, and a POM carrying all the metadata Central
demands. This demo does the part Jenesis owns - end to end, offline - building that
bundle from a `module-info.java` and a `project.properties`, then resolving it back
to prove it is consumable, and explains the last mile to Central (the signed upload)
rather than performing it, so nothing here touches the network or needs a secret. A
repository of your own takes the same bundle from `release` itself, which the demo
shows against a repository on this machine.

Run it
------

    java build/Demo.java

`build/Demo.java` configures publication explicitly on the `Project` builder -
`new Project(Path.of(".")).target(...).sources(true).documentation(true).version("1.0.0")` -
and runs the `stage` target. That materializes the release tree in Maven
repository layout, and `build` hands back the staging step's folder under the
`stage/maven` key:

    build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.jar
    build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.pom
    build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0-sources.jar
    build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0-javadoc.jar

The generated `.pom` carries the full Central-required metadata, assembled from the
two channels described under "Where the POM metadata comes from" below:

    <groupId>build.jenesis</groupId>
    <artifactId>build.jenesis.demo.publishing</artifactId>
    <version>1.0.0</version>
    <name>Jenesis Publishing Demo</name>
    <description>A sample library whose name and description are taken from this Javadoc ...</description>
    <url>https://github.com/jenesis/jenesis</url>
    <inceptionYear>2024</inceptionYear>
    <licenses>...</licenses>
    <developers>...</developers>
    <scm>...</scm>
    <issueManagement>...</issueManagement>

Then `Demo.java` proves the bundle is real: the staged tree is itself a Maven
repository, so it recovers the coordinate from the staged layout (hard-coding
nothing) and resolves it straight back out, reporting each artifact:

    Resolving build.jenesis:build.jenesis.demo.publishing:1.0.0 from the staged repository:
      [resolved] build.jenesis.demo.publishing-1.0.0.jar
      [resolved] build.jenesis.demo.publishing-1.0.0.pom
      [resolved] build.jenesis.demo.publishing-1.0.0-sources.jar
      [resolved] build.jenesis.demo.publishing-1.0.0-javadoc.jar

That is the whole bundle a release would carry, validated without publishing
anything.

Central requires the `-javadoc.jar`, but not that it documents anything, and rendered
documentation can make up most of a release's size. To publish the jar without it, set

    java -Djenesis.documentation.empty=true build/Demo.java

The `-javadoc.jar` is then still staged and resolved beside the others, but it holds
nothing but a file named `INTENTIONALLY_EMPTY`, and no documentation tool runs at all.

The two jobs of publishing
--------------------------

Publishing to Maven Central is two jobs: **produce a correct, complete bundle**,
and **upload it**. Jenesis owns the first; the upload to Central (with credentials
and GPG signing) is left to a dedicated release tool. This demo does the part Jenesis owns
and explains the last mile rather than performing it. What Central requires of every
artifact is exactly what trips people up: a POM carrying `name`, `description`,
`url`, `<licenses>`, `<developers>`, and `<scm>`, plus a `-sources.jar` and a
`-javadoc.jar` beside the main jar. This demo shows Jenesis assembling all of that
from a `module-info.java` and a `project.properties`.

Layout
------

    demo/demo-67-publishing
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          stages the release bundle, then resolves it back to prove it is consumable
    |-- build/Repository.java    a Maven repository on this machine that takes a release into a folder
    |-- project.properties       only what a module declaration cannot express: url, inception year, license, developer, scm, issues
    `-- sources
        |-- module-info.java     module build.jenesis.demo.publishing (MODULAR_TO_MAVEN: a POM is generated)
        `-- sample/Sample.java   the library being published

Where the POM metadata comes from
---------------------------------

Jenesis folds two channels into the per-module `metadata.properties` it feeds to
the POM emitter, so the descriptor stays the source of truth and nothing is
duplicated:

- **The `module-info.java`** supplies everything it can. The module *name* derives
  the Maven coordinate - groupId from the first two dotted segments, artifactId
  from the full name - so `module build.jenesis.demo.publishing` becomes
  `build.jenesis : build.jenesis.demo.publishing`. The module declaration's
  Javadoc supplies `<name>` (its first sentence) and `<description>` (its second
  paragraph), each as plain text: HTML, `{@code}` and `{@link}` markup are reduced to
  the words they mark. A Markdown comment (`///`) keeps its inline markup as written,
  apart from a link, which becomes its label.
- **`project.properties`**, read because it sits at the project root, carries only what a
  module declaration cannot express: `url`, `inceptionYear`, every
  `license.<id>.name|url|distribution`, every
  `developer.<id>.name|email|url|organization|organizationUrl|roles|timezone`, the
  roles comma-separated - published under `<id>`, unless `developer.<id>.id`
  names another id or is empty for none - the `scm.connection|developerConnection|url`
  block, and `issueManagement.system|url` and `ciManagement.system|url`. A file elsewhere, or several, is named with `Project.metadata(...)` or
  `-Djenesis.project.metadata`, and an empty value there reads none.

The version is stamped by `Project.version("1.0.0")`. Without one, the module is
published unversioned and its POM, which cannot omit a version, carries
`0-SNAPSHOT`. If you ever need a coordinate that does not follow from the module
name, `project=...` / `artifact=...` in `project.properties` override the derived
values for every module of the project. A module that keeps a coordinate of its own
declares it in a `project.properties` of its own configuration folder,
`META-INF/build.jenesis/` beside its sources, which is layered over the root file and
wins for every key it names - so a module keeps the coordinate its library was
published under before it took a module name, and a sibling that `requires` it lists
it in its POM under that coordinate. The point here, though, is that you usually do not have to.

Reproducibility
---------------

A published artifact should be reproducible: anyone rebuilding the same sources
should get the same bytes. To show this, `build/Demo.java` actually stages the
project **twice**, into two independent target trees (`target/first` and
`target/second`), then SHA-256-compares every staged file:

    Reproducibility: SHA-256 of the staged artifacts from two independent builds:
      [identical] d91d7fdbca6dd47d  .../build.jenesis.demo.publishing-1.0.0-javadoc.jar
      [identical] 3e724f5745988205  .../build.jenesis.demo.publishing-1.0.0-sources.jar
      [identical] 98b7cebd090a24d8  .../build.jenesis.demo.publishing-1.0.0.jar
      [identical] 34b4535a7753db42  .../build.jenesis.demo.publishing-1.0.0.pom
      -> both builds are bit-for-bit identical

The two builds share no output directory, yet every artifact - jar, POM, sources,
javadoc - hashes identically. This is not luck: Jenesis content-hashes each step
and writes deterministic outputs (jar entries carry a fixed timestamp, javadoc is
generated with `-notimestamp`), so identical inputs always produce identical
artifacts. A consumer can therefore verify that the bytes on Central were built
from the published sources.

Releasing to a Maven repository of your own
-------------------------------------------

An internal Maven repository - a Nexus, an Artifactory, a Reposilite, a Jenesis
Repository's `java` repository - takes the staged Maven tree from `release` itself
once `jenesis.release.maven.uri` names it, as its `release/maven` step. This is
what `maven-deploy-plugin` does with a `<distributionManagement>` repository, or
Gradle's `maven-publish` with one of its repositories.

`build/Repository.java` stands in for one here: a small server that keeps what is
put into it in a folder and serves it back. Start it in a second terminal, or in the
background:

    java build/Repository.java 8642 target/repository &

Then release a version into it. The address is plain `http:`, so the release has
to be allowed to use one:

    java -Djenesis.project.version=1.0.0 \
         -Djenesis.release.maven.uri=http://localhost:8642/ \
         -Djenesis.repository.insecure=true \
         build/jenesis/Make.java release

    [RELEASED]  http://localhost:8642/build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0-cyclonedx.json
    [RELEASED]  http://localhost:8642/build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.jar
    [RELEASED]  http://localhost:8642/build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.pom

Every staged file is put at its place in the Maven layout, each with the `.md5`,
`.sha1`, `.sha256` and `.sha512` checksums a Maven client verifies:

    target/repository/build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.jar
    target/repository/build/jenesis/build.jenesis.demo.publishing/1.0.0/build.jenesis.demo.publishing-1.0.0.jar.sha1
    ...
    target/repository/build/jenesis/build.jenesis.demo.publishing/maven-metadata.xml

The artifact's `maven-metadata.xml` is read from the repository first and the
release merged into it, so it lists every version released so far, the newest as
`<latest>` and the newest without `-SNAPSHOT` as `<release>`:

    <versioning>
      <latest>1.0.0</latest>
      <release>1.0.0</release>
      <versions>
        <version>1.0.0</version>
      </versions>
      <lastUpdated>20261010120036</lastUpdated>
    </versioning>

A release without a version is the `0-SNAPSHOT` of its POM, and a SNAPSHOT is
released as Maven deploys one: each file under a name of its own, stamped with
the time and a build number that counts on from the one the repository holds, and
a `maven-metadata.xml` in the version's folder naming them, so a Maven or Gradle
build that requires `0-SNAPSHOT` resolves the newest:

    java -Djenesis.release.maven.uri=http://localhost:8642/ \
         -Djenesis.repository.insecure=true \
         build/jenesis/Make.java release

    target/repository/build/jenesis/build.jenesis.demo.publishing/0-SNAPSHOT/build.jenesis.demo.publishing-0-20261010.120053-1.jar
    target/repository/build/jenesis/build.jenesis.demo.publishing/0-SNAPSHOT/maven-metadata.xml

A real repository asks for a key. `jenesis.release.maven.token` is sent as the
`Authorization` header exactly as given, so it names its scheme - `Basic` and the
Base64 of `<user>:<password>` for a Nexus or an Artifactory user, `Bearer <token>`
where the repository hands out tokens. Where the settings are not given, a release
reads the `MAVEN_RELEASE_URI` and `MAVEN_RELEASE_TOKEN` environment variables, never
`MAVEN_REPOSITORY_URI` and `MAVEN_REPOSITORY_TOKEN`, which name where a build
resolves from, so a CI job keeps its deploy key apart from the key it reads with.
Like every credential, the token may come from the command line,
`~/.jenesis/jenesis.properties` or the environment, never from a file of the
project, and it is never sent to a repository that a file of the project named.

Any answer other than success stops the release and names the address and the
status, as does an address that is not `http:` or `https:`, or plain `http:`
without `-Djenesis.repository.insecure=true`. Nothing is signed. Maven Central is
refused by name, because it takes a signed bundle through its own publishing
service - which is the next section's job:

    java -Djenesis.release.maven.uri=https://repo1.maven.org/maven2/ build/jenesis/Make.java release

    Cannot release to https://repo1.maven.org/maven2/: Maven Central takes a release signed and
    through its own publishing service, which JReleaser handles - describe that release in a
    jreleaser.yml at the project root, ...

Publishing to Maven Central
---------------------------

The remote upload and GPG signing that turn the staged bundle into a Central
release are deliberately *not* Jenesis's job. The recommended tool is
**[JReleaser](https://jreleaser.org/)**: point it at `target/stage/maven/output/`
and it signs every artifact and uploads the bundle to Maven Central. This is
exactly how Jenesis itself releases - see the repository's `jreleaser.yml`.
Central requires a detached GPG signature (`.asc`) for each file, which JReleaser
produces; for Central, Jenesis stops at the unsigned, validated bundle so its
credentials and signing keys never enter the build.

So the division of labour is: Jenesis guarantees *what* you publish is complete
and correct, and JReleaser handles *getting it there* safely.

Besides a Maven repository of your own, the build reaches a Jenesis module
repository by itself. With `jenesis.release.uri` set, `release` puts the jar of each
staged module there as its `release/jenesis` step, as the export demo shows. A
`jenesis` repository of a [Jenesis Repository](https://jenesis.build/repository/)
takes exactly that release, one put per module. A `java` repository is the other way
there: it takes Maven publishes only, and serves every modular jar deployed to its
Maven layout - by `release/maven`, JReleaser, `mvn deploy` or any other tool - by its
module name as well. A project that publishes to Maven therefore reaches module
consumers with the same upload.

That division is about credentials and signing keys, not about who types the
command, so the build does offer to run the release tool for you. A `jreleaser.yml`
at the project root adds a `release/jreleaser` step to the `release` goal:

    java build/jenesis/Make.java release

It is a rehearsal by default - JReleaser runs with `--dry-run`, performing every
local phase and skipping every remote one - and publishing takes the explicit
`-Djenesis.jreleaser.dry=false`. Of the shell's environment, JReleaser is
handed the platform's own variables and every `JRELEASER_*` one, which is where
it reads its credentials. The tool itself is expected on the `PATH`
rather than resolved as a pinned dependency, because unlike a compiler or a linter
a release tool shapes nothing about the artifact: it transmits a staged tree that
is already finished, so its version changes how the upload happens and never what
was uploaded. It is environmental setup, like the JDK or `git`.

The goal also contributes one thing to a release run by somebody else. Its
`release/jreleaser/environment` step writes `environment/jreleaser.properties` carrying
`JRELEASER_PROJECT_VERSION`, the version `jenesis.project.version` stamped, and the `release`
goal hands that variable to the JReleaser process it starts - so the version is
stated once, by the build, instead of being passed separately to the build and to
the release tool and drifting. That step runs no process and has no side effect,
which makes it safe for a CI job that then hands off to a dedicated release action
and reads the version from the file.
Note it only applies to a configuration that leaves `project.version` unset: a
version written in the `jreleaser.yml` wins outright and the file is ignored.
The build infers no version of its own: a `pom.xml` build whose configuration names
none releases with `-Djenesis.project.version` naming the version it publishes.

For a release driven by CI, prefer a dedicated release step where your platform
offers one - JReleaser's own GitHub Action pins the tool version and wires the
secret store better than a build tool reaching for `PATH`. This repository does
exactly that, and keeps the goal for rehearsing a release locally first.

This separation is deliberate, not just convenient. Most people building the
project never release it at all - releasing is usually a job for CI or another
hardened environment that holds the credentials and signing keys, run rarely and
under tight control, while everyone else just builds. And the way you release
changes independently of the way you publish a build: you might switch release
tools, registries, or signing setups without touching the build, or change how the
build produces artifacts without touching the release pipeline. Keeping the
validated bundle (Jenesis) separate from the upload (the releaser) lets each side
evolve on its own.
