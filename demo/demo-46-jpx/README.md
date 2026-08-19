jpx demo
========

Runs a **published** program without building anything: `jpx` resolves a released
module (or Maven artifact), installs its runtime dependency closure once, and
launches its entry point - an `npx` for the module path. The program here is the
JUnit Platform Console Launcher, asked for its `--version`, named twice: once by
its Java **module name** and once by its **Maven coordinate**. Both name a
version, and both are verified against the installation's SHA-256 before the JVM
starts. Two further runs exercise that hash: one supplies only the first 32 hex
characters of it, and one supplies a digest that does not match and is refused.

This is the only demo with nothing of its own to compile. There is no `pom.xml`,
no `module-info.java` and no `sources/` folder: everything it runs was released by
somebody else.

Build and run it
----------------

From this directory:

    java build/Demo.java

which prints, for each run, the command it stands for, where it installed, and
what it launched:

    jpx --hash=9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8 org.junit.platform.console@6.1.3 --version
      [verified] target/jpx/org.junit.platform.console@6.1.3 as org.junit.platform.console/org.junit.platform.console.ConsoleLauncher over 9 modules
    JUnit Platform Console Launcher 6.1.3
    JVM: 25.0.3 (Eclipse Adoptium OpenJDK 64-Bit Server VM 25.0.3+9-LTS)
    OS: Linux 7.0.0-28-generic amd64

    jpx --hash=9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8 org.junit.platform:junit-platform-console@6.1.3 --version
      [verified] target/jpx/org.junit.platform--junit-platform-console@6.1.3 as org.junit.platform.console.ConsoleLauncher over 9 jars on the class path
    JUnit Platform Console Launcher 6.1.3
    JVM: 25.0.3 (Eclipse Adoptium OpenJDK 64-Bit Server VM 25.0.3+9-LTS)
    OS: Linux 7.0.0-28-generic amd64

    [... and the module name once more, with a 32-character prefix ...]

    jpx --hash=9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01c0ffee org.junit.platform.console@6.1.3 --version
      [blocked]  Checksum mismatch for org.junit.platform.console@6.1.3: expected a digest starting with 9b60...c0ffee but computed 9b60...cfd3e8

The demo drives the `Jpx` API (`jpx.install(<target>).verify(<hash>).launch(...)`)
so it can install into its own `target/jpx/` folder and leave `~/.jenesis/jpx/`
untouched. That is why it names the whole record - storage folder, repositories,
resolvers, digest function and placement: the short constructor,
`new Jpx(PathPlacement.INFERRED)`, is the command line's and installs under the
home folder, so a folder of your own arrives together with the repositories and
resolvers that go with it. The command line does the same thing:

    java build/jenesis/Jpx.java \
        --hash=9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8 \
        org.junit.platform.console@6.1.3 --version

    java build/jenesis/Jpx.java \
        --hash=9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8 \
        org.junit.platform:junit-platform-console@6.1.3 --version

The two forms
-------------

The target grammar is `<name>[@<version>][/<main-class>]`, and the name decides
how it resolves:

1. **By module name** - `org.junit.platform.console@6.1.3`. The module repository
   is asked for that module's POM, and the dependency graph is read from Maven
   metadata, exactly as the `modular_to_maven` layout resolves a `requires`
   clause. Nothing about the artifact's Maven identity is spelled out.
2. **By Maven coordinate** - `org.junit.platform:junit-platform-console@6.1.3`.
   The `<groupId>:<artifactId>` pair is resolved directly. A Java module name can
   never contain a colon, so the two forms need no flag to tell them apart.

Both install the same nine jars and therefore the same checksum, which is the
point: naming the module and naming its coordinate are two spellings of one
artifact. They install side by side, under folders named for what was asked for
(`org.junit.platform.console@6.1.3` and
`org.junit.platform--junit-platform-console@6.1.3`), because the name is part of
what a run reproduces.

What the name also decides is how the program runs. A module name is run as a
module: every jar that describes one goes on the module path, and the entry point
starts as `-m org.junit.platform.console/...`, which is why the first run reports
nine modules. A coordinate names an artifact rather than a module, so the same
nine jars go on the class path in full and the main class is named directly -
`over 9 jars on the class path` in the second run.

Dropping `@6.1.3` from either form resolves the latest release instead - handy at
a prompt, but then neither the version nor the checksum is fixed, so this demo
names both.

Version and hash
----------------

Each installation is a flat folder of jars beside a `jpx.properties` descriptor:

    name=org.junit.platform.console
    version=6.1.3
    mainClass=org.junit.platform.console.ConsoleLauncher
    mainModule=org.junit.platform.console
    modulepath=apiguardian-api-1.1.2.jar,jspecify-1.0.0.jar,junit-platform-commons-6.1.3.jar,...
    checksum=SHA-256/9b60dfc3d10f0b4fdf69050eec7b7332f5c395f7e36fad5747ff421e01cfd3e8

`--hash=<prefix>` recomputes that digest over every jar the descriptor lists -
each file's name and content hash, in sorted order - and refuses to launch on a
mismatch. It therefore covers the whole closure rather than one artifact, and it
is checked on every run, not only on the run that installed it, so a jar swapped
underneath an existing installation is caught too. The descriptor is written last,
so an interrupted download is recognized and redone, and concurrent installs of
one target coordinate through a file lock.

The value is matched as a **prefix**, which the demo's third run shows by passing
only `9b60dfc3d10f0b4fdf69050eec7b7332` - the first 32 hex characters, which is
also the shortest accepted. Anything shorter is refused outright rather than
checked loosely: 32 hex characters is 128 bits, the floor at which a prefix still
pins the bytes. A leading `SHA-256/` is stripped, so the `checksum` line can be
pasted in verbatim. The fourth run supplies a full-length digest ending in
`c0ffee` instead of `cfd3e8` - the shape a transcription slip takes - and is
blocked before the JVM starts:

    Checksum mismatch for org.junit.platform.console@6.1.3: expected a digest
    starting with 9b60...c0ffee but computed 9b60...cfd3e8

Nothing about that failure depends on the version being wrong or the download
having gone astray: the same message is what a tampered jar produces, because the
check is over bytes rather than over names.

Where it resolves from
----------------------

By default `jpx` reaches the same repositories the build does: the Jenesis module
repository at `https://repo.jenesis.build/` for module names, Maven Central for
coordinates, both fronted by the local exports in `~/.jenesis/` and by `~/.m2/`.
`JENESIS_REPOSITORY_URI` and `MAVEN_REPOSITORY_URI` (or
`-Djenesis.module.uri` / `-Djenesis.maven.uri`) redirect them at a mirror or an
internal repository.

Two further flags round it out, neither used here: `--modular` resolves purely
over module descriptors, walking `requires` clauses like the `modular` layout, and
runs the whole closure on the module path; `--docker[=<image>]` runs the launched
process in a container while resolution and installation stay on the host, so the
containerized run needs no network and no credentials.
