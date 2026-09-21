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

    Resolving through the configured chain, with no repository in code:
      [resolved] demo.convention.greeter-1.0.0.jar from the company repository, by the Maven convention
      [resolved] demo.other-1.0.0.jar from the regular module repository

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
naming it, which is the one line this demo is about, or by naming it in the module
repository chain, which the section after this one shows:

    new Project(Path.of(".")).repositories(Map.of("module", new MavenModuleRepository()))

The no-argument constructor resolves through the configured Maven repository
chain, the same `jenesis.maven.uri` (or `MAVEN_REPOSITORY_URI`) the build already
uses for its Maven dependencies, so a private repository ahead of Maven Central is
configuration, not code:

    -Djenesis.maven.uri=https://repo.example.com/releases/,https://repo1.maven.org/maven2/

This demo passes a repository explicitly instead, because the one it resolves from
is the staging tree it produced seconds earlier.

Wiring it as configuration
--------------------------

The module repository chain names the kind of each remote, so the same wiring is
configuration as well as code. An entry prefixed with `maven:` is read by the
publishing convention, a plain entry speaks the Jenesis module protocol, and the
`|<module>` suffix decides which module names reach which remote:

    -Djenesis.module.uri=maven:https://nexus.example.com/releases/|com.example,https://repo.jenesis.build/

A `requires com.example.service` is then served by
`com.example:com.example.service` in the company's own Nexus, and every other
module name falls through to the public module repository. The prefix matches a
module and the names below it, it may be repeated (`|com.example|org.tools`), and
an entry without one answers for every name that reaches it. The last section of
the demo run above is exactly this chain, with the staged tree standing in for the
company repository.

Each `maven:` entry may also name the convention it is read by, because two
prefixes need not agree on one. The count goes between the type and the URI, and
it is the number of leading segments of a module name that form the groupId:

    -Djenesis.module.uri=maven:3:https://deep.example.com/|com.example.platform,maven:https://nexus.example.com/|com.example,https://repo.jenesis.build/

`com.example.platform.store` is then resolved as
`com.example.platform:com.example.platform.store` from the first remote, while
`com.example.service` keeps two segments on the second. An entry that names no
count follows `jenesis.maven.segments`, which itself defaults to two, so the
common case stays a URI and a prefix. A count below one is rejected, and so is a
count on a `module:` entry, where no groupId is derived at all.

A `maven:` entry authenticates with the Maven credentials, `jenesis.maven.token`
or `MAVEN_REPOSITORY_TOKEN`, because it is a Maven repository, and it fronts the
local `~/.m2` cache like any other. It is handed that credential only where the
chain would hand over its own: to the first remote of the chain and never to a
fallback behind it, and only when the credential is at least as private as the
URL it would travel to.

Two rules decide that. A URL that a file the project provides supplied - its
`jenesis.properties`, or a profile beside it - is never sent a credential at all,
and such a file may not name one either: `jenesis.maven.token`,
`jenesis.module.token` and `jenesis.cache.key` are refused there, the way
`jenesis.toolchain.searchpath` already is, because a credential is yours to hand
out and not a project's. A token the environment provides is sent only to the
remotes the environment names, so a URL on the command line does not receive it,
and the built-in public repository receives nothing in any case.

A build that keeps its remotes in the environment keeps its credential with them:
name them in `MAVEN_REPOSITORY_URI`, or splice that variable into a longer chain
as `@MAVEN_REPOSITORY_URI`, and the token travels to those remotes and stops at
the rest. A build that names its remotes on the command line names the token
there too, with `-Djenesis.maven.token`, or keeps both in
`~/.jenesis/jenesis.properties`. A type also carries into a
spliced reference, so `maven:@CORP_MODULES` reads every remote that variable
names as a Maven repository, and an unknown type is rejected by name.

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
