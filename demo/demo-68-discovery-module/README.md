Module discovery demo
=====================

Let the owner of a module's domain say how the module is published. A module name
is a reversed domain, so `build.jenesis` belongs to `jenesis.build`, and with
discovery switched on Jenesis reads a small file that the domain publishes before
it asks any module repository. This demo requires Jenesis itself and builds on the
module path alone: `jenesis.build` says where the module's jar is - the Jenesis
release on GitHub - so the module resolves without the module repository
`../demo-67-module-convention` resolved from, and without Maven. The demo names no
repository at all, so nothing but the domain can answer.

Build and run it
----------------

From this directory:

    java -Djenesis.print.fetch=true build/jenesis/Execute.java

which prints where each file came from and then what the program found:

    [FETCHED]   https://github.com/jenesis/jenesis/releases/download/v0.15.4/build.jenesis-0.15.4.jar
    jenesis-make from build.jenesis-0.15.4.jar

One file is downloaded, the module's jar, straight from the release, and nothing
else is asked: `jenesis.properties` empties `jenesis.maven.uri` and
`jenesis.module.uri`, which name no remote at all, so what the domain does not
answer is found nowhere - there is no fallback. Remove those two lines and a module
the domain does not answer for resolves from Maven Central and `repo.jenesis.build`
again, as it would without discovery. The jar is
checked against the pin in `module-info.java` as any download is, so the domain
chooses where a module comes from but never what it is. A pinned module that was
downloaded once is reused from `.jenesis/artifacts/` without asking anyone; delete
that folder and `target/` to watch the download again.

Layout
------

    demo/demo-68-discovery-module
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
    |-- jenesis.properties     discovery on, the modular layout, and no repository
    `-- sources
        |-- module-info.java             module demo.discovery.module { requires build.jenesis; }
        `-- demo/discovery/Tools.java    prints the jenesis-make tool and the jar it came from

Switching it on
---------------

Discovery is used only where you switch it on, in `jenesis.properties` as here or on
the command line. This demo's file switches it on, builds on the module path, and
names no repository beside it:

    jenesis.repository.discovery=true
    jenesis.project.layout=modular
    jenesis.maven.uri=
    jenesis.module.uri=

Every module and every Maven group the build resolves is then looked up first, and
one whose domain publishes nothing resolves exactly as it would without the setting.
Each domain is asked once per build, however many modules and artifacts it serves.

The file
--------

A domain describes what it publishes in a `java.util.Properties` file, in UTF-8, at
`https://<domain>/.well-known/java-repository.properties`. For `net.bytebuddy.agent`
Jenesis reads the file of `bytebuddy.net`, the domain its vendor owns, and the file
of `agent.bytebuddy.net` only where `bytebuddy.net` publishes none. The first file
found speaks for every name below its domain - whoever owns a domain controls its
subdomains anyway - so one request answers for all of a vendor's modules, and a key
that file does not hold is absent rather than asked of a subdomain. A file that says
`delegate=true` lets its subdomains speak for themselves: their files are read as well,
the most specific one holding a key answers, and its own entries stand for the
subdomains whose files do not hold the key. `jenesis.build` publishes:

    module=https://github.com/jenesis/jenesis/releases/download/v{version}/{module}-{version}{-classifier}.{type}
    module.latest=https://github.com/jenesis/jenesis/releases/latest/download/{module}.jar
    module.suffixes=none
    moduletomaven=build.jenesis:{module}
    maven=https://github.com/jenesis/jenesis/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type}
    maven.latest=https://github.com/jenesis/jenesis/releases/latest/download/{artifactId}.pom
    maven.suffixes=none

A module is answered by two keys, one for each way a build resolves modules.

**`module` says where a module's files are.** Its value is a location, in one of two
forms:

  * a **template** names each file, as here. `{module}`, `{-suffix}`, `{version}`
    and `{type}` are filled in, and `{-classifier}` becomes `-sources` for the
    sources jar and nothing for the jar itself. A template without `{-classifier}`
    or `{type}` serves only the plain jar. Where a `.sha512`, `.sha256` or `.sha1` is
    published beside a file, the file is checked against it.
  * a **root**, a URI without placeholders, is a Jenesis module service such as
    `https://repo.jenesis.build/`, asked as `jenesis.module.uri` would ask it.

**`moduletomaven` says which Maven artifact a module is.** Its value is a Maven
coordinate, written `<groupId>:<artifactId>[:<extension>[:<classifier>]]` as Maven
writes one, without its version. `{module}` is the module's name, so this one line
maps every module below `jenesis.build`: `build.jenesis` is
`build.jenesis:build.jenesis`. That artifact is then resolved as any Maven dependency
is, which is where the `maven` key comes in: it names where the group
`build.jenesis` is published, as `../demo-69-discovery-maven` shows. Without one,
the artifact comes from the Maven remotes, Maven Central by default, and a missing
version is the newest release its Maven metadata names.

Where artifacts follow a pattern other than the module name, `{-suffix}` follows it:
it is the part of the module's name below the file's domain, its labels joined by
dashes after a leading one, so nothing for `net.bytebuddy` and `-agent` for
`net.bytebuddy.agent`. One line maps all of Byte Buddy's modules:

    moduletomaven=net.bytebuddy:byte-buddy{-suffix}

and a module whose suffix names no artifact, `net.bytebuddy.utility` as
`byte-buddy-utility`, resolves nothing from it. A coordinate without placeholders
belongs to one module only, the one whose own domain publishes the file.

Several projects under one domain
---------------------------------

A key may name, in brackets, the module or artifact it is for, where one is
published apart from the rest. `jenesis.build` serves three projects, each from
GitHub releases of its own with versions of its own, so beside the keys above it
publishes, for the launcher:

    module[build.jenesis.launcher]=https://github.com/jenesis/jenesis-launcher/releases/download/v{version}/{module}-{version}{-classifier}.{type}
    module[build.jenesis.launcher].latest=https://github.com/jenesis/jenesis-launcher/releases/latest/download/{module}.jar
    maven[build.jenesis.launcher]=https://github.com/jenesis/jenesis-launcher/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type}
    maven[build.jenesis.launcher].latest=https://github.com/jenesis/jenesis-launcher/releases/latest/download/{artifactId}.pom

`module` and `moduletomaven` select by module name, `maven` by artifact ID, and the
keys beside a selected key - `.latest`, `.since`, `.suffixes` - carry its selector.
A name ending in `*` selects every name that starts with the rest, as
`maven[byte-buddy-*]` does. The exact name wins over the longest such prefix, and
either over the key for all. A coordinate a selected key names applies to its
module wherever it sits below the domain, so a module whose artifact breaks the
pattern takes one line of its own:

    moduletomaven[com.example.legacy]=com.example:example-classic

Which key answers
-----------------

This demo builds with `jenesis.project.layout=modular`, which resolves a module from
its jar and the `requires` of its `module-info`, so it asks `module` first. A build
that resolves modules through Maven - the layout a `module-info.java` gets by
default - asks `moduletomaven` first. Run the demo that way:

    java -Djenesis.project.layout=modular_to_maven -Djenesis.print.fetch=true build/jenesis/Execute.java

and the module resolves as the Maven artifact it is mapped to, `build.jenesis:build.jenesis`,
whose POM is read before its jar. The `maven` key names where both are, so a release
that attaches its POM serves the module whole. One that does not fails the build with
`No POM found for build.jenesis`, since no Maven remote is left to ask - with
`jenesis.maven.uri` back at its default, the POM would come from Maven Central.

A key that does not answer leaves the request to the other: a template without a
`module.latest` cannot name the newest release, so a module asked for without a
version is then answered by `moduletomaven` and the Maven metadata even on the module
path. A domain that publishes both keys therefore needs no module repository at all.

The newest version
------------------

A template cannot list the versions there are, so a module asked for without a
version - a `requires` that no pin names - is answered through `module.latest`, a
link to the newest release. Jenesis sends the link a `HEAD` request, follows no
redirect, and reads the version from where the link redirects to, matched against
the template up to the end of the path segment that holds `{version}`. GitHub
redirects `releases/latest/download/<name>` to `releases/download/v<version>/<name>`
of the newest release, whatever `<name>` is, so the link above names `0.15.4` for
`build.jenesis` without downloading anything. A Jenesis module service, such as
`https://repo.jenesis.build/module/{module}/{module}.jar`, names the version in its
`Jenesis-ModuleVersion` header instead, which is read first. The version is then
checked against `module.since` and `module.suffixes` like any other, and a link that
answers `404` leaves the request to the module repository. A link may also name a
`maven-metadata.xml`, wherever it is hosted, whose release becomes the newest version:

    module.latest=https://maven-repository.example.com/releases/build/jenesis/{module}/maven-metadata.xml

Which versions are served
-------------------------

Two keys beside a key say which versions it serves. `<key>.since` names the first:

    moduletomaven=build.jenesis:{module}
    moduletomaven.since=0.15.0

and an earlier version is left to the module repository, ordered as Maven orders
versions, so `1.2.3-rc.1` comes before `1.2.3` and `1.10.0` after it.
`<key>.suffixes` names the qualifiers it serves, the part of a version after its
first dash, with `none` for a version without one:

    module.suffixes=none,rc

serves `1.2.3`, `1.2.3-rc.1` and `1.2.3-RC2`, since a suffix matches the leading
word of a qualifier ignoring case, and leaves `1.2.3-SNAPSHOT` to the module
repository. Without these keys, every version is served. A request that names no
version cannot be checked against either, so a key restricted by one leaves such a
request to the module repository.

What fails the build
--------------------

A file that gives a key no value, lists a suffix that is not a word of letters and
digits, uses a placeholder its key does not know, names a coordinate in `module` or
a location in `moduletomaven`, or names a coordinate that is not one fails the build
with the file named. Of a key named twice the last one counts, as
`java.util.Properties` reads the file, and a key Jenesis does not know is ignored,
so a file can carry more than this demo shows.

A file that cannot be fetched counts as absent - a domain without the file, a host
that does not exist, or one a proxy cannot reach - so the build carries on with the
module repository. A certificate that does not verify fails the build instead.

Trusting the file
-----------------

The file is always read from its domain over `https`, so the domain's certificate
vouches for it. A location it names is read over `https` as well, never from a
`file:`, `jar:` or any other kind of URI, and a download is never redirected to
one. The file only says where a module comes from: a pinned module is still checked
against its pin, and a module with a declared signature against its signature, so a
domain that changed hands can break a build but never change what it accepts.
