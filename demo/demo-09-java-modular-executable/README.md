Executable Java (modular) demo
==============================

Take a modular Java app and ship it as a self-contained native application image -
a launcher with its own bundled Java runtime that a user can run without installing
a JDK, and one that stays small because the runtime is trimmed to the module graph.
The project is just a `module-info.java` plus a single Java source with a `main`
method and one real module dependency (`org.slf4j`), and Jenesis packages it with
the JDK's `jpackage`. It is the executable counterpart of `../demo-02-java-modular`,
and its POM-based sibling is `../demo-08-java-pom-executable`.

Run it
------

From this directory, pass the arguments you want the packaged application to
receive on its command line:

    java build/Demo.java Ada Lovelace

`Demo.java` builds the `stage` goal with the stock
`new Project(Path.of(".")).assembler(new InferredMultiProjectAssembler())` - packaging is
selected by the committed `packaging.properties` at this demo's root, which sets
`jpackage=app-image` - then reads the image folder from the `stage/packages` entry
of the map that `build("stage")` returns (a fixed build target) and launches the
produced platform launcher with your arguments. The packaged app prints:

    Hello, Ada Lovelace, from a packaged Java module built by Jenesis!

(Because no SLF4J backend is bundled, SLF4J prints a one-time "no providers" notice
and uses a no-op logger - the `slf4j-api` jar is still bundled and on the app's
classpath.) With no arguments it greets `world`. Building the plain
`java build/jenesis/Make.java` compiles and jars the module exactly as
`../demo-02-java-modular` does, without producing an image.

Declaring the entry point with `@jenesis.main`
----------------------------------------------

For the build to package a runnable image it first has to know the main class. In
the modular layout that is declared with a `@jenesis.main` Javadoc tag on
`module-info.java`:

    /**
     * @jenesis.release 25
     * @jenesis.main sample.Sample
     */
    module demo.modular.executable {
        exports sample;
    }

That one tag is what marks the module as runnable, for launching it and for
packaging it alike. A POM project has no `module-info.java`; its equivalent is a
`<mainClass>` property, shown in `../demo-08-java-pom-executable`.

Layout
------

    demo/demo-09-java-modular-executable
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java       stages an app-image with jpackage, then runs it
    |-- build/DemoNative.java stages a fully bundled native installer (deb/exe/dmg) and reports it
    `-- sources/
        |-- module-info.java     module demo.modular.executable (@jenesis.main, requires org.slf4j, pinned)
        `-- sample/Sample.java   main(String[] args); uses org.slf4j via `import module org.slf4j;`

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout, exactly as `../demo-02-java-modular` does. Because `org.slf4j`
is a real module on the module path, `Sample.java` pulls it in with a single
module import - `import module org.slf4j;` - the same style the project's own
test sources use (`import module org.junit.jupiter.api;`). The produced image
bundles `slf4j-api.jar` next to the application jar: jpackage packages the whole
runtime closure, not just your own code.

How packaging fits the build
----------------------------

Packaging is opt-in through a `packaging.properties` file. Jenesis reads it from the
same configuration location the inferred linters, formatters, and SBOM use: a
module's `META-INF/build.jenesis/` folder (or `build.jenesis/` in a Maven layout),
falling back to the project-wide configuration directory (`build.jenesis/` under
the project root by default). The first match wins, so a module-local file selects packaging for one
module while a project-wide one selects it for all modules at once. When its
`jpackage` key is set, the build produces an application image after every
module has been built, one for every module that declares a main class (modules
without one are skipped). The `jpackage` value is the `jpackage --type` (`app-image`,
`deb`, `rpm`, `dmg`, `pkg`, `exe`, `msi`); an absent or empty value means no jpackage
step, so the type is always explicit - this demo commits a `packaging.properties` at
its root with `jpackage=app-image`, a self-contained launcher plus bundled runtime
that needs no platform-native tooling. `--name` / `--main-jar` / `--main-class` are
derived automatically (the name from the module's coordinate, here
`demo.modular.executable`).

Each produced image is then collected by the `STAGE` module's `packages` step
into `stage/packages/`, the staging analogue of `stage/maven` and `stage/modular`:

    target/stage/
    |-- modular/output/...                          the published modular jar
    `-- packages/output/demo.modular.executable/
        |-- bin/demo.modular.executable             the launcher
        `-- lib/                                     app jars + bundled runtime

Rooting the module path for a non-self-contained graph
------------------------------------------------------

This demo's closure is a *self-contained module graph*: every jar is an explicit
named module (`demo.modular.executable` and `org.slf4j`), so the generated launcher's
`-m demo.modular.executable/sample.Sample` resolves the whole module path through the
main module's `requires`, and jpackage adds nothing extra to it.

When the closure is *not* self-contained, jpackage gives the launcher
`--java-options --add-modules=ALL-MODULE-PATH,ALL-DEFAULT` so it roots the entire module
path along with the default platform set. Two things break self-containment, and both
land on the module path with no edge from the main module's `requires`:

- an **automatic module** - a jar with an `Automatic-Module-Name` but no `module-info`
  (many libraries, much of Spring) - declares no `requires` of its own, so a named
  module it uses only internally (say a Spring jar's transitive `commons-logging`)
  is never pulled into the graph; and
- a **plain jar** - no descriptor at all - which the JDK turns into a filename-derived
  automatic module on the module path, with the same problem.

Without the flag such a module is left unresolved and the app fails at run time with
`NoClassDefFoundError`. `ALL-DEFAULT` comes along for the same reason one step further
out: a `-m` launch roots the initial module alone, while a jar that declares no
`requires` of its own was written expecting the whole platform to be there, so the
platform modules nobody named are rooted too. jpackage stages every jar into the one `input/` directory it
uses as the module path, so - unlike the `bundle` and `Execute` paths - there is no
separate class path to weigh: the closure either resolves from `requires` or is rooted
wholesale. The `Execute` launcher, the `bundle` step (which records the decision as a
`javaOptions` entry for its consumer) and `native-image` apply the same rule, so only a
self-contained graph launches without `--add-modules ALL-MODULE-PATH,ALL-DEFAULT`.

Stage a `.jmod` and a `jlink` runtime image
-------------------------------------------

`jpackage` links a runtime into that app-image for you. You can also produce the
lower-level artifacts on their own - the `.jmod`
and the linked runtime image - with two boolean keys in `packaging.properties`. Both
are modular-only: a `.jmod` and a custom runtime are built from *modules*, so a
classpath project (`../demo-08-java-pom-executable`) has nothing to pack or link.
With `jmod=true` and `jlink=true` in `packaging.properties`, run:

    java build/jenesis/Make.java stage

`jmod=true` packs the module into a `.jmod`,
the modular-package format that - unlike a jar - can also carry native libraries,
legal files, and `bin/`/`conf/` content. It is staged beside the modular jar in the
module-repository layout:

    target/stage/modular/output/demo.modular.executable/
    |-- demo.modular.executable.jar
    `-- demo.modular.executable.jmod

`jlink=true` links a **custom runtime image** holding only the modules this app
needs, staged under `stage/runtime` (the analogue of `stage/packages`):

    target/stage/runtime/output/module-sources/
    |-- bin/java          the runtime's own launcher
    `-- lib/ conf/ ...    a standard, trimmed JDK runtime layout

The image is trimmed to exactly `demo.modular.executable`, `org.slf4j`, and
`java.base` (about 61 MB here, against ~300 MB for a full JDK), and it runs straight
from its own `bin/java` with no JDK installed:

    target/stage/runtime/output/module-sources/bin/java -m demo.modular.executable/sample.Sample Ada Lovelace
    # Hello, Ada Lovelace, from a packaged Java module built by Jenesis!

The difference from the app-image is only the wrapper: `jpackage` adds a native
launcher (`bin/demo.modular.executable`) and an installer-friendly layout around the
same kind of trimmed runtime, while `jlink` leaves you the bare runtime image to
launch with `java -m`. All three steps can also be chained -
`jmod -> jlink -> jpackage` - so that extra content packed into the `.jmod` rides
through the linked runtime into the final app.

The runtime also redistributes `org.slf4j`, so it carries that library's licence.
Whatever links modules into a runtime - `jlink`, and `jpackage` for a modular
application - links this module from its `.jmod`, which takes the legal notices of
the module's own jar and of every jar it needs at run time; the runtime keeps them
under its `legal/` folder, in a folder named after each jar:

    target/stage/runtime/output/module-sources/legal/demo.modular.executable/
    `-- org.slf4j-2.0.16/LICENSE.txt

This holds without `jmod=true` as well: the `.jmod` is then built for linking
alone and not staged. `-Djenesis.legal.notices` names the jar entries taken as
notices - by default `META-INF/NOTICE`, `META-INF/LICENSE`, the `META-INF/license/`
and `META-INF/licenses/` folders, a root `LICENSE` and `about.html`, in any case and
with any extension.

Bundle the jars for a JRE-based image
-------------------------------------

`jpackage` bundles a trimmed runtime into the image. The lighter alternative is to
ship only your jars onto an off-the-shelf JRE base (the shared-base trade discussed
at the end of this page). For that, a `bundle=true` line in `packaging.properties`
writes a single `bundle/bundle.zip` for every module with a main class:

    java build/jenesis/Make.java

    bundle.zip
    |-- application.unix.args      the launch, as a Java argument file
    |-- application.windows.args   the same launch, with Windows path separators
    `-- jars/                      every jar of the closure, stored once

The zip carries exactly the runtime closure `Execute` would launch, in one store, and the
argument file is not a description of the launch but the launch itself - naming the module
path, the class path, the graph's options and the entry point, run as
`java @application.unix.args` from the folder it was unpacked into. There are two of them
because the path separator is the only part of a launch a bundle cannot know in advance. Every path
is spelled out rather than handed over as a folder, so a jar is read because the
argument file names it and never because of where it sits; and because the whole command
lives in a file, no closure is too large to launch. An automatic module or a class-path
jar adds `--add-modules ALL-MODULE-PATH,ALL-DEFAULT` to root the whole
module path and the default platform set,
exactly as the jpackage section above describes. Here the closure is
`demo.modular.executable` + `org.slf4j`, both explicit modules, so those lines are absent.

What `process-java.properties` gives the JVM of the module - an `--add-reads` its module
path needs, a system property - leads both argument files, as it leads the JVM `Execute`
starts. `stage` collects the zip into `stage/packages/` as `<artifact>.zip`, beside what
jpackage writes there, so `export` and `release` ship it like any other package.
Unzipped onto a JRE base, the bundle needs no JDK and no jpackage:

    FROM gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629
    COPY bundle/ /opt/app/
    WORKDIR /opt/app
    ENTRYPOINT ["java", "@application.unix.args"]

For a non-modular project the argument file names a `--class-path` and the main class
instead of a module (see `../demo-08-java-pom-executable`); the launch command is the
same either way, which is the point of shipping the command rather than a description
of it.

A generated Dockerfile
----------------------

That Dockerfile does not have to be written by hand either: a `docker` key in
`packaging.properties` generates it, taking the base image as its value the way
`jpackage` takes its type. This demo commits the hardened, digest-pinned JRE image of
`../demo-08-java-pom-executable` as a `docker` profile:

    java -Djenesis.make.profiles=docker build/jenesis/Make.java stage

    target/stage/docker/output/module-sources/
    |-- Dockerfile
    |-- application.args           the launch, as a Java argument file
    `-- jars/                      the app jar and slf4j-api

Because the module declares `mainModule`, the jars carrying a module descriptor are named
on the module path and the entry point launches the module, not a class - and the whole
command travels in the argument file, led by what `process-java.properties` gives the JVM
of the module as in the bundle, so the `ENTRYPOINT` is the same three words however large
the closure grows:

    FROM gcr.io/distroless/java25-debian13:nonroot@sha256:ca60da1345c0f17b6d019049e6749e15f10fd3c0da86dec938d2b4ec565d0629
    LABEL ...                      the metadata, as in ../demo-08-java-pom-executable
    COPY jars/ /app/jars/
    COPY application.args /app/
    WORKDIR /app
    ENTRYPOINT ["java", "@/app/application.args"]

The build never runs a container tool, so no Docker installation is involved in
producing this. The staged folder is a complete build context:

    docker build -t sample target/stage/docker/output/module-sources

An image to build on
--------------------

The module path and the class path in `application.args` each end with a folder under
`/app/extensions/`:

    "--class-path"
    "/app/extensions/classpath/*"
    "--module-path"
    "/app/jars/demo.modular.executable-0-SNAPSHOT.jar:/app/jars/org.slf4j-2.0.16.jar:/app/extensions/modulepath"

The build creates neither folder, and `java` skips a folder that does not exist, so the image runs
as if they were not named. An image built `FROM` this one creates them by copying jars in:
`modulepath/` for a jar that is a module, `classpath/` for one that is not. Every jar of the
application stays named, and is found before anything in those folders, so a jar added there can
extend the application but never replace one of its modules. Adding a jar needs no change to the
argument file and no `ENTRYPOINT` of its own. The module system activates a module: when a module
uses a service, `java` resolves every module on the module path that provides it, and
`ServiceLoader` finds them - on the class path, through its `META-INF/services` entries.

This demo uses that already. `org.slf4j` looks up its logging backend as a service, and none is
shipped, so `sample` runs with slf4j's no-op logger:

    docker run --rm sample Ada

    SLF4J(W): No SLF4J providers were found.
    ...
    Hello, Ada, from a packaged Java module built by Jenesis!

An image built from it adds a provider, and nothing else:

    FROM sample
    COPY slf4j-simple-2.0.16.jar /app/extensions/modulepath/

Download the jar from Maven Central into an empty folder, then write that Dockerfile beside it:

    curl -O https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar
    docker build -t sample-logging .
    docker run --rm sample-logging Ada

    [main] INFO sample.Sample - greeting Ada
    Hello, Ada, from a packaged Java module built by Jenesis!

Your own application offers the same hook the same way. One of its modules declares an
interface and `uses` it, and an extension's module-info says
`provides <interface> with <class>`. A module that provides no such service is only resolved
when you name it, and a system property is set on the command line. Both are options for `java`,
and `java` reads more of them from the `JDK_JAVA_OPTIONS` variable, ahead of the `ENTRYPOINT`'s
command line. An image extends the variable rather than replacing it, so it keeps what every
image below it added:

    ENV JDK_JAVA_OPTIONS="${JDK_JAVA_OPTIONS} --add-modules com.example.extension -Dcom.example.enabled=true"

The variable may also name an argument file, as `@/app/extension.args`, which the image copies in.
`docker run -e JDK_JAVA_OPTIONS=...` sets it for a single container. `java` prints a `NOTE` line
naming the variable whenever it is set.

A project without `mainModule` names the same two folders (see `../demo-08-java-pom-executable`).
Its application runs on the class path, so a module in `modulepath/` is resolved only when
`--add-modules` names it.

A single executable jar with the launcher
-----------------------------------------

The `bundle.zip` still needs a launch command. A `launcher=true` line in
`packaging.properties` goes one step further and produces a **single executable jar**
you run with `java -jar foo.jar` - without flattening the dependencies into a fat
jar, so modularity survives. The target resolves the published
`build.jenesis:build.jenesis.launcher` from Maven Central and shades it into the jar:

    java build/jenesis/Make.java

    demo.modular.executable.jar
    |-- META-INF/MANIFEST.MF                      Main-Class: build.jenesis.launcher.Launcher
    |-- META-INF/jenesis/application.properties   mainClass, mainModule, modulepath, classpath
    |-- build/jenesis/launcher/*.class            the launcher (the jar's own unnamed module at run time)
    `-- jars/<dep>.jar/...                        each dependency, exploded

The launcher's `Main-Class` reads `META-INF/jenesis/application.properties`, resolves the jars `modulepath`
names into a fresh `ModuleLayer` and the ones `classpath` names into the unnamed module
of the same loader, and invokes the entry point - reconstructing what
`java -p modulepath -cp classpath -m demo.modular.executable/sample.Sample` would do,
all in process. Because each dependency keeps its own subfolder nothing is merged, so
`module-info`s and `META-INF/services` do not collide. `build/DemoLauncher.java` builds
this and runs the produced jar for you:

    java build/DemoLauncher.java Ada Lovelace

Because the launcher is shaded into the artifact you ship, it is pinned like any
other dependency - this module's `module-info.java` carries a
`@jenesis.pin launcher/maven/build.jenesis/build.jenesis.launcher <version> SHA-256/...`
tag (in its own `launcher` group), so the exact launcher bytes are verified and the
build is reproducible, and `pin` refreshes it the same way it pins everything else.

Unlike `jpackage` and `bundle`, this carries no JVM and no `jlink` runtime - it is a
plain jar that runs on any JDK 25 - and unlike the `bundle.zip` it needs no launch
script. `stage` collects it into `stage/packages/` as `<artifact>.jar`. It carries the
`--add-reads`, `--add-exports`, `--add-opens` and `--enable-native-access` lines of
`process-java.properties`, which the launcher applies to the modules it defines. Any other
JVM option does not travel with it, because `java -jar` reads none from the jar it runs:
the build names each one it leaves out, and an application that needs one passes it to
`java -jar` or ships as a bundle. (A bundle with no `mainClass` is instead a self-contained Java agent; see the
launcher's own documentation.)

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
      demo.modular.executable_0_amd64.deb (13 MiB)
    Unlike the app-image, this is a deliverable to install with the platform's package manager, not a directory to launch in place.

`jpackage` also takes several types, separated by commas, and builds one package per
type, all staged side by side in `stage/packages`:

    jpackage=app-image,deb

    target/stage/packages/output/
    |-- demo.modular.executable/              the app-image
    `-- demo.modular.executable_0_amd64.deb   the installer

Each type is a jpackage run of its own, and each is handed only the options it takes:
what `process-jpackage.properties` sets reaches every type, and a
`process-jpackage-<type>.properties` beside it adds options for one type alone, such
as `--linux-deb-maintainer` in `process-jpackage-deb.properties`.

This package is much smaller than the classpath sibling `../demo-08-java-pom-executable`
produces (tens of megabytes): because this is a modular application, jpackage's internal
`jlink` trims the bundled runtime down to the module graph (`demo.modular.executable`,
`org.slf4j`, `java.base`), rather than bundling a full runtime.

Producing a native installer needs the platform's packaging tooling on the PATH (Linux:
`dpkg-deb`/`fakeroot` for `deb`, `rpmbuild` for `rpm`; Windows: the WiX Toolset; macOS:
the bundled `productbuild`/`hdiutil`). For that reason it is run locally rather than in
CI, where `Demo.java`'s app-image - which needs no native tooling - covers the packaging
path.

Where to go from here?
----------------------

The `app-image` is self-contained - it bundles its own (`jlink`-trimmed) Java
runtime - so a deployable container needs no JDK, only a minimal base with a C
library. The generated Dockerfile from above can ship exactly that: a
`docker.jpackage=<type>` line beside `docker=<image>` puts a jpackage package into
the image instead of the jars, and the base image then needs no Java at all. This
demo commits it as a `container` profile, on the distroless image that holds just the C
library, hardened and pinned by its digest as the JRE image is:

    docker=gcr.io/distroless/cc-debian13:nonroot@sha256:e792ab3d241a468a4fd7519ddbbebe66b49b5f365771716ea688ad40b6c6f1c2
    docker.jpackage=app-image

    java -Djenesis.make.profiles=container build/jenesis/Make.java stage

    target/stage/docker/output/module-sources/
    |-- Dockerfile
    `-- demo.modular.executable/   the app-image: bin/, lib/app/ and lib/runtime/

The image is the app-image under `/app`, started by its own launcher:

    FROM gcr.io/distroless/cc-debian13:nonroot@sha256:e792ab3d241a468a4fd7519ddbbebe66b49b5f365771716ea688ad40b6c6f1c2
    LABEL ...                      the metadata, as before
    COPY ["demo.modular.executable/", "/app/"]
    WORKDIR /app
    ENTRYPOINT ["/app/bin/demo.modular.executable"]

The type is one a Linux image can run: `app-image`, copied in as it is, or `deb` or
`rpm`, installed with the base image's package manager - `apt-get` for a `deb`, and
`dnf`, `yum`, `zypper` or plain `rpm` for an `rpm` - which also installs the system
libraries the package declares. Those two need a base that has the package manager,
which a distroless one does not, and the image then starts the launcher the package
installed, through `/app/launcher`:

    docker=debian:stable-slim@sha256:eb593cf2c358cacef45ca0a424bbc7d30cfa3466265fc2662b9466a0ca6ba1c5
    docker.jpackage=deb

The type is added to those `jpackage` builds, and it is staged only when `jpackage`
lists it as well: this profile stages no package, `jpackage=deb` beside
`docker.jpackage=app-image` stages the installer and hands the image an app-image, and
`jpackage=app-image,deb` beside `docker.jpackage=deb` stages both and builds each once.
jpackage packages for the platform it runs on, so this needs a build on **Linux** -
on macOS or Windows the build stops and says so, and `-Djenesis.project.docker=true`
runs it in a Linux container instead. The staged folder is again a complete build
context (Podman reads the same file):

    docker build -t demo-modular-executable target/stage/docker/output/module-sources
    docker run --rm demo-modular-executable Ada Lovelace

or, identically, with Podman:

    podman build -t demo-modular-executable target/stage/docker/output/module-sources
    podman run --rm demo-modular-executable Ada Lovelace

Either prints the same greeting the local launcher does, and because the bundled
runtime is trimmed to the module graph (`demo.modular.executable`, `org.slf4j`,
`java.base`) the resulting image stays small. The `/app/extensions/` folders of the
jar-based image have no counterpart here: the application and its runtime are fixed
when jpackage links them.

This is where a modular project pays off for deployment. jpackage runs `jlink`
over the resolved module graph, so it ships only the part of the standard library
those modules actually need. Measured with Temurin 25.0.3, this app-image is about
57 MB (its trimmed runtime about 56 MB), against about 138 MB for the classpath
sibling `../demo-08-java-pom-executable`, which has to bundle a full runtime - less
than half the size, and the gap is almost entirely the JVM. For reference, the
full Temurin 25.0.3 JDK is about 303 MB and `java.base` alone links to about
60 MB, so a modular runtime sits near that floor. So a modular project tends to
produce a markedly smaller deployment image than one that ships plain jars against
an off-the-shelf JVM.

One caveat, since these app-images bundle the JVM inside the application layer:
that "smaller" is per artifact. Two *different* services packaged this way share
only the OS base layer - each carries its own runtime - so at scale you duplicate
the JVM across services. The alternative is a common JRE base, such as the distroless
`java25-debian13` of the `docker` profile, with only your jars layered on top: image
layers are content-addressed, so that one JVM layer is stored and pulled once and
shared by every image built on it, and at run time containers sharing it also share its
read-only pages in the host page cache (per-process heap and metaspace stay private
either way). None of this is Docker-specific: it is OCI-image and Linux-kernel behaviour, so Podman shares base
layers and their page cache the same way - rootless Podman on `fuse-overlayfs`
still deduplicates the layer on disk, with a thin FUSE indirection on top. The
trade is a larger but shared runtime and coupling to that base's JVM version. The
self-contained image avoids that coupling in the strongest way: jpackage links the
bundled runtime with `jlink` from the very JDK that compiled the code and ran the
tests, so the application is shipped on **exactly the same JVM** it was built and
verified against - not whatever patch version or distribution a shared base happens
to provide. So a trimmed self-contained image is smallest, simplest, and
runtime-faithful as a single deliverable,
while a shared base is often leaner in aggregate when you run many distinct
services; replicas of one service share the JVM regardless.
