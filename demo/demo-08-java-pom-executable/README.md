Executable Java (POM-based) demo
================================

Take a Maven-layout Java app and ship it as a self-contained native application
image - a launcher with its own bundled Java runtime that a user can run without
installing a JDK. The project is just a `pom.xml` plus a single Java source with a
`main` method and one real Maven dependency (`commons-lang3`), and Jenesis packages
it with the JDK's `jpackage`. It is the executable counterpart of
`../demo-01-java-pom`, and it has a modular sibling that ships the same kind of app
from a `module-info.java`.

Run it
------

From this directory, pass the arguments you want the packaged application to
receive on its command line:

    java build/Demo.java ada lovelace

`Demo.java` builds the `stage` goal with the stock
`new Project(Path.of(".")).assembler(new InferredMultiProjectAssembler())` - packaging is
selected by the committed `build.jenesis/packaging.properties`, which sets
`jpackage=app-image` - then reads the image folder from the `stage/packages` entry
of the map that `build("stage")` returns (a fixed build target) and launches the
produced platform launcher with your arguments. The packaged app prints:

    Hello, Ada lovelace, from a packaged Maven project built by Jenesis!

(`commons-lang3`'s `StringUtils.capitalize` upper-cased the leading `a`, proving the
bundled dependency is on the launched app's classpath.) With no arguments it greets
`World`. Building the plain `java build/jenesis/Make.java` (the default `build`
target) compiles and jars the project exactly
as `../demo-01-java-pom` does, without producing an image.

Declaring the entry point
--------------------------

For the build to package a runnable image it first has to know the main class. In
the modular layout that comes from a `@jenesis.main` Javadoc tag on
`module-info.java`. A POM project has no
`module-info.java`, so the Maven-layout equivalent is a `<mainClass>` property in
the POM:

    <properties>
        <mainClass>sample.Sample</mainClass>
    </properties>

Either way the build records `main=sample.Sample` in the module's
`module.properties`. That single field is what both the `Execute` launcher and
the `package` step key off to treat the module as runnable.

Layout
------

    demo/demo-08-java-pom-executable
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java       stages an app-image with jpackage, then runs it
    |-- build/DemoNative.java stages a fully bundled native installer (deb/exe/dmg) and reports it
    |-- pom.xml              Maven coordinates, <sourceDirectory>, <mainClass>, one <dependency> (pinned)
    `-- sources/sample/Sample.java   main(String[] args); uses commons-lang3 StringUtils

`Sample.java` calls `org.apache.commons.lang3.StringUtils`, so the produced image
bundles `commons-lang3.jar` next to the application jar - jpackage packages the
whole runtime closure, not just your own code.

How packaging fits the build
----------------------------

Packaging is opt-in through a `packaging.properties` file. Jenesis reads it from the
same configuration location the inferred linters, formatters, and SBOM use: a
module's `build.jenesis/` folder (or `META-INF/build.jenesis/` in a modular layout),
falling back to the project-wide configuration directory (`build.jenesis/` under
the project root by default). The first match wins, so a module-local file selects packaging for one
module while a project-wide one selects it for all modules at once. When its
`jpackage` key is set, the build produces an application image after every
module has been built, one for every module that declares a main class (modules
without one are skipped). The `jpackage` value is the `jpackage --type` (`app-image`,
`deb`, `rpm`, `dmg`, `pkg`, `exe`, `msi`); an absent or empty value means no jpackage
step, so the type is always explicit - this demo commits a `build.jenesis/packaging.properties`
with `jpackage=app-image`, a self-contained launcher plus bundled runtime
that needs no platform-native tooling. `--name` / `--main-jar` / `--main-class` are
derived automatically (the name from the artifactId, here `java-pom-executable`).

Each produced image is then collected by the `STAGE` module's `packages` step
into `stage/packages/`, the staging analogue of `stage/maven` and `stage/modular`:

    target/stage/
    |-- maven/output/...                 the published jar + pom
    `-- packages/output/java-pom-executable/
        |-- bin/java-pom-executable      the launcher
        `-- lib/                          app jars + bundled runtime

Bundle the jars for a JRE-based image
-------------------------------------

`jpackage` bundles a whole runtime into the image. The lighter alternative is to ship
only your jars onto an off-the-shelf JRE base. For that, a `bundle=true` line in
`packaging.properties` writes a single `bundle/bundle.zip` for every module with a
main class:

    java build/jenesis/Make.java

    bundle.zip
    |-- application.unix.args      the launch, as a Java argument file
    |-- application.windows.args   the same launch, with Windows path separators
    `-- jars/                      the app jar and commons-lang3

Every jar is stored once under `jars/`, and the argument file is the launch itself - here a
`--class-path` and a main class, because this is a non-modular project:

    "--class-path"
    "jars/build.jenesis.demo%2Fjava-pom-executable%2F1.0.0.jar:jars/org.apache.commons.lang3-3.14.0.jar"
    "sample.Sample"

What `process-java.properties` gives the program's JVM leads both argument files, as it
leads the JVM `Execute` starts, and `stage` collects the zip into `stage/packages/` as
`<artifact>.zip`, so `export` and `release` ship it like any other package.

Unzipped onto a JRE base, it needs no JDK, no jpackage and no descriptor reader:

    FROM gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629
    COPY bundle/ /opt/app/
    WORKDIR /opt/app
    ENTRYPOINT ["java", "@application.unix.args"]

The modular sibling names its jars on `--module-path` and launches a module instead.

A generated Dockerfile
----------------------

Writing that Dockerfile by hand is the one manual step left, so a `docker` key in
`packaging.properties` generates it. Its value is the base image, the one thing the
build cannot infer, and this demo commits Google's distroless Java 25 image as a `docker`
profile:

    docker=gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629

That is a hardened base: it holds a JRE and the C library it runs on and nothing else - no
shell, no package manager - and its `nonroot` variant starts the application as an
unprivileged user (uid 65532) rather than as root. The `@sha256:` digest pins it: Docker
pulls exactly the image the digest names and ignores the tag beside it, which is there for
the reader, so the build context yields the same base on every machine, and a tag that is
republished upstream changes nothing until you move the digest yourself. The current one is
what `docker buildx imagetools inspect gcr.io/distroless/java25-debian13:nonroot` prints as
its `Digest`.

    java -Djenesis.make.profiles=docker build/jenesis/Make.java stage

    target/stage/docker/output/module/
    |-- Dockerfile
    |-- application.args           the launch, as a Java argument file
    `-- jars/                      the app jar and commons-lang3

The generated file is the one written by hand above, with the entry point taken from the
module's main class. Every jar of the application is named rather than globbed, and the command
travels in the argument file, so the `ENTRYPOINT` stays this size however many jars the
application resolves. The class path ends with one glob, `/app/extensions/classpath/*`, and the
module path is `/app/extensions/modulepath`. The build creates neither folder, and `java` skips a
folder that does not exist. An image built `FROM` this one copies jars into them to extend the
application: on the class path they come after its own jars, so they can add classes and
`META-INF/services` entries, but a class the application already has is always loaded from the
application's own jar.

    FROM gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629
    LABEL "org.opencontainers.image.base.name"="gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629" \
          "org.opencontainers.image.title"="java-pom-executable" \
          "org.opencontainers.image.version"="1.0.0" \
          "org.opencontainers.image.documentation"="https://jenesis.build" \
          ...
    COPY jars/ /app/jars/
    COPY application.args /app/
    WORKDIR /app
    ENTRYPOINT ["java", "@/app/application.args"]

The copies come before the `WORKDIR`, so they create `/app` and it belongs to root, as
everything they copy does. On a base that runs as an unprivileged user, as this one does,
the application can read what the image starts but can neither replace its argument file
nor add a jar to its extension folders.

The `LABEL` describes the image with the standard `org.opencontainers.image.*` keys, taken from
what the project declares: its name, description, version and URL, its source repository and
revision, its organization, developers and licences - what its SBOM names as well. A licence
is written as an SPDX identifier, and only when every licence has one. Every standard key is
written, empty where the project declares nothing, so that none is inherited from a base
image that sets its own, as `eclipse-temurin` sets `version` and `created`.

`created` is left empty unless you name the time, since a clock reading would make every build
differ. The time the archives record is the one it takes, when you set it explicitly - to the
time of the commit that is built, for example:

    java -Djenesis.make.profiles=docker -Djenesis.archive.timestamp=$(git log -1 --format=%cI) \
        build/jenesis/Make.java stage

A `docker.label.<name>=<value>` line in `packaging.properties` adds a label of your own, or
replaces a standard one. An empty value suppresses a standard label: it is written empty, so the
base image's value is not inherited either. `docker.label.org.opencontainers.image.created=`
keeps an image without a creation time even where `jenesis.archive.timestamp` is set. This demo's
`docker` profile adds the documentation:

    docker=gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629
    docker.label.org.opencontainers.image.documentation=https://jenesis.build

The build never runs a container tool, so nothing here needs Docker installed. The
staged folder is a complete build context, and creating the image is one command:

    docker build -t sample target/stage/docker/output/module

There is no second configuration file, and deliberately so: a Dockerfile inherits
`ENV`, `WORKDIR`, `USER` and `EXPOSE` from its base image, so anything else the image
needs belongs in a base image you control rather than in build configuration. The
build only adds your application layer and the command that starts it.

A single executable jar with the launcher
-----------------------------------------

A `launcher=true` line in `packaging.properties` turns the bundle into a **single
executable jar** you run with `java -jar foo.jar`, by shading the published
`build.jenesis:build.jenesis.launcher` into the jar root as its `Main-Class` and
exploding each dependency into a `jars/<jar>/` subfolder, with `classpath` in the jar's
`META-INF/jenesis/application.properties` naming them (this app is non-modular, so everything
is class path). The application sees what `classpath` names and nothing else of the jar: not
the launcher's classes, not the descriptor and not the jar's own manifest, so an
`application.properties` of its own - the file a framework such as Spring Boot reads its
configuration from - is the one it finds. `build/DemoLauncher.java` activates the
committed `launcher` profile with `Project.profiles(...)`: the profile's
`build.jenesis/launcher/packaging.properties` (`launcher=true`) outranks the module's
own `packaging.properties`, then the demo builds and runs the produced jar:

    java build/DemoLauncher.java ada lovelace

`stage` collects the jar into `stage/packages/` as `<artifact>.jar`. Every jar it stores
keeps its directory entries, so a scan of a package on the class path finds them as it
would in the original jar. Of `process-java.properties`, the jar carries the
`--add-reads`, `--add-exports`, `--add-opens` and `--enable-native-access` lines, which the
launcher applies as it builds the module graph. Any other JVM option does not travel with
it, because `java -jar` reads none from the jar it runs: the build names each one it
leaves out, and an application that needs one passes it to `java -jar` or ships as a
bundle.

The launcher is shaded into the artifact, so it is pinned like any dependency - the
`pom.xml` carries a `<!--jenesis.pin launcher/maven/build.jenesis/build.jenesis.launcher
... -->` block (its own `launcher` group, kept out of `<dependencyManagement>` because
it is not an application dependency). The modular sibling keeps each modular
dependency in its own subfolder and reconstructs them on the module path at run time.

Fully bundled native installer
------------------------------

`Demo.java` builds an `app-image` - a directory you launch in place. Its sibling
`build/DemoNative.java` instead builds the platform's **native installer**: the single
artifact you hand to a user to install. It follows the same shape as `Demo.java` - a
`packaging.properties` selects the packaging type, the fixed `stage` target is built,
the result is read from the fixed `stage/packages` key - and two things differ: the
type is computed for the host and written to a `packaging.properties` in a throwaway
temp directory that `DemoNative.java` points the build at with
`Project.configuration(...)` (rather than the committed `jpackage=app-image`), and a
native installer is a deliverable to install, not a program to launch in place, so it
reports the produced package rather than running it.

    java build/DemoNative.java

The packaging type is chosen for the host - `deb` on Linux, `exe` on Windows, `dmg` on
macOS. On Linux it prints:

    Built a fully bundled deb installer under target/stage/packages/output:
      java-pom-executable_1.0.0_amd64.deb (38 MiB)
    Unlike the app-image, this is a deliverable to install with the platform's package manager, not a directory to launch in place.

The installer carries the whole bundled runtime, which is why it is tens of megabytes.
Because this is a classpath (non-modular) application, jpackage bundles a full runtime;
the modular sibling produces a much smaller package, since
there jpackage's internal `jlink` can trim the runtime to the module graph.

Producing a native installer needs the platform's packaging tooling on the PATH (Linux:
`dpkg-deb`/`fakeroot` for `deb`, `rpmbuild` for `rpm`; Windows: the WiX Toolset; macOS:
the bundled `productbuild`/`hdiutil`). For that reason it is run locally rather than in
CI, where `Demo.java`'s app-image - which needs no native tooling - covers the packaging
path.

Where to go from here?
----------------------

The `app-image` is self-contained - it bundles its own Java runtime - so a
deployable container needs no JDK, only a minimal base with a C library, such as the
distroless `cc` image, hardened and pinned as the JRE image above is. Stage a
**Linux** app-image (run `java build/Demo.java` on Linux or in CI), then copy it
into an image with a small `Dockerfile` (Podman reads the same file):

    FROM gcr.io/distroless/cc-debian13:nonroot@sha256:e792ab3d241a468a4fd7519ddbbebe66b49b5f365771716ea688ad40b6c6f1c2
    COPY target/stage/packages/output/java-pom-executable /opt/app
    ENTRYPOINT ["/opt/app/bin/java-pom-executable"]

From this directory, build and run it with Docker:

    docker build -t java-pom-executable .
    docker run --rm java-pom-executable ada lovelace

or, identically, with Podman:

    podman build -t java-pom-executable .
    podman run --rm java-pom-executable ada lovelace

Either prints the same greeting the local launcher does.

Because this is a **classpath** application, the bundled runtime is a *full* Java
runtime: jpackage cannot prove which standard-library modules you do not use, so
it ships them all. Measured with Temurin 25.0.3 that runtime is about 136 MB and
the whole app-image about 138 MB, so the container carries a JVM the size of an
off-the-shelf JRE. A modular application would be far
smaller, because it bundles only the runtime its module graph resolves.

Whichever way you size it, this self-contained image carries one guarantee a
shared base image gives up: jpackage builds the bundled runtime from the very JDK
that compiled the code and ran the tests, so the app ships on **exactly the same
JVM** it was built and verified against - not whatever patch version or
distribution a base image happens to provide. The flip side - that a self-contained
image cannot share its JVM layer across *different* services, where a common
JRE base such as the distroless one of the `docker` profile can (deduplicated on disk and in the page
cache) - applies here too.
