Module convention repository demo
=================================

Resolve your own modules from a plain Maven repository, by the same coordinate
convention Jenesis publishes them with. A `MavenModuleRepository` reads the
convention in reverse: a `requires demo.convention.greeter` becomes
`demo.convention:demo.convention.greeter` in whatever Maven repository you
publish to - so a team that builds with Jenesis and deploys to its own Nexus,
Artifactory, GitHub Packages, or Maven Central needs no module registry and no
coordinate mapping to keep in sync.

Run it
------

From this directory:

    java build/Demo.java

`build/Demo.java` publishes `greeter/` into a Maven repository under `target/`,
builds the consumer at the project root against it, and runs the produced module:

    Published demo.convention.greeter as demo.convention:demo.convention.greeter:1.0.0 in file:///.../target/greeter/stage/maven/output/

    Running demo.convention.app against the resolved library:
    Hello, Jenesis!

    Resolving demo.convention.greeter without a version, through that metadata:
      [resolved] demo.convention.greeter-1.0.0.jar

Layout
------

    demo/demo-62-module-convention
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java      publishes greeter/, then builds the root project against it
    |-- greeter/             the library, staged into a Maven repository under target/
    |   `-- sources
    |       |-- module-info.java     module demo.convention.greeter
    |       `-- sample/greeter/Greeter.java
    `-- sources              the consumer
        |-- module-info.java     module demo.convention.app { requires demo.convention.greeter; }
        `-- sample/app/App.java

The convention
--------------

The MODULAR_TO_MAVEN layout derives the Maven coordinate of every module it
publishes from the module name alone: the **groupId is the first two dotted
segments**, the **artifactId is the full module name** (see
`../demo-57-publishing`). `module demo.convention.greeter` is therefore published
as `demo.convention:demo.convention.greeter`, and `MavenModuleRepository` derives
exactly the same pair when resolving it - one method, `MavenModuleRepository.groupId(...)`,
serves both directions, so the publisher and the consumer cannot drift apart.

A project that publishes under a group of its own - `project=com.example.tools`
in its `project.properties` - is resolved by naming that group once:

    new MavenModuleRepository().group("com.example.tools")

The artifactId stays the module name, because that is what Jenesis publishes;
a project that also overrides `artifact=` has left the convention behind and
needs an explicit `@jenesis.alias` instead.

How many segments the groupId takes is itself configuration. `jenesis.maven.segments`
sets it for the whole build - for the coordinate a module is published under as much
as for the one it is resolved by, so the two sides still cannot drift apart:

    -Djenesis.maven.segments=3

A module named `demo.convention.deep.greeter` is then published and resolved as
`demo.convention.deep:demo.convention.deep.greeter`, and `=1` flattens the group to
`demo:demo.convention.greeter`. A name with fewer segments than the count keeps
serving as its own groupId, as `greeter` does at the default of two. A repository
wired in code names it without the property, and a value below one is rejected:

    new MavenModuleRepository().segments(3)

Wiring it
---------

The repository is part of Jenesis but is **not wired anywhere by default** - the
Jenesis module repository stays the default module repository. A build opts in by
naming it, which is the one line this demo is about:

    new Project(Path.of(".")).repositories(Map.of("module", new MavenModuleRepository()))

The no-argument constructor resolves through the configured Maven repository
chain, the same `jenesis.maven.uri` (or `MAVEN_REPOSITORY_URI`) the build already
uses for its Maven dependencies, so a private repository ahead of Maven Central is
configuration, not code:

    -Djenesis.maven.uri=https://repo.example.com/releases/,https://repo1.maven.org/maven2/

This demo passes a repository explicitly instead, because the one it resolves from
is the staging tree it produced seconds earlier.

Why the demo wires two repositories
-----------------------------------

In the MODULAR_TO_MAVEN layout the two repositories divide the work: the `module`
repository answers *what a module name means* - it serves the POM the resolver
reads - and the `maven` repository serves the artifacts of the closure that POM
describes. Both point at the same place here, so the demo wires both to the staged
tree. A real build usually configures the `maven` chain once, as above, and adds
the one `module` line.

In the MODULAR layout there is no POM: the module repository serves the modular
jars directly, and this repository serves them from Maven layout just as well.

Floating versions
-----------------

A `requires` whose version is pinned - by `@jenesis.pin`, or by a
`pin-<name>.properties` BOM as here - resolves without any lookup. An unpinned
`requires` floats, and the convention answers it from the `maven-metadata.xml` a
repository manager maintains beside the artifacts: the `<release>` it names, or,
where a repository publishes no `<release>` element, the newest stable version it
lists - never a prerelease, unless the version is spelled `LATEST`. A staged tree
carries no such file, which is why the demo writes the one a repository manager
would publish before showing the floating case - and why the build itself pins.

The pin in this demo is generated by `build/Demo.java` rather than committed,
because the library it covers is built moments earlier in the same run; a project
consuming a released library commits that file, checksum and all.
