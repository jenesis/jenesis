DNS module resolution demo
==========================

Let the owner of a module's domain say how the module is published. A module name
is a reversed domain, so `build.jenesis` belongs to `jenesis.build`, and with DNS
resolution switched on Jenesis asks that domain's DNS about the module before it
asks any module repository. This demo requires Jenesis itself: `jenesis.build` says
which Maven artifact the module is, and where that artifact is published - the
Jenesis release on GitHub - so the module resolves without the module repository
`../demo-67-module-convention` resolved from.

Build and run it
----------------

From this directory:

    java -Djenesis.print.fetch=true build/jenesis/Execute.java

which prints where each file came from and then what the program found:

    [FETCHED]   https://repo1.maven.org/maven2/build/jenesis/build.jenesis/0.15.4/build.jenesis-0.15.4.pom
    [FETCHED]   https://github.com/jenesis/jenesis/releases/download/v0.15.4/build.jenesis-0.15.4.jar
    jenesis-make from build.jenesis-0.15.4.jar

No module repository is asked: the module's POM and its jar are both those of the
Maven artifact `jenesis.build` maps it to. The 0.15.4 release attaches no POM, so
the POM comes from Maven Central, where a release that attaches its POM serves both.
The jar is checked against the pin in `module-info.java` as any download is, so DNS
chooses where a module comes from but never what it is. A pinned module that
was downloaded once is reused from `.jenesis/artifacts/` without asking anyone;
delete that folder and `target/` to watch the download again.

Layout
------

    demo/demo-68-dns-module
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
    |-- jenesis.properties     jenesis.dns.enabled=true
    `-- sources
        |-- module-info.java       module demo.dns.module { requires build.jenesis; }
        `-- demo/dns/Tools.java    prints the jenesis-make tool and the jar it came from

Switching it on
---------------

DNS is asked only where you switch it on, in `jenesis.properties` as here or on the
command line:

    jenesis.dns.enabled=true

Every module and every Maven group the build resolves is then looked up first, and
one without a record resolves exactly as it would without the setting. The lookups
go to Cloudflare's DNS-over-HTTPS resolver; your own `~/.jenesis/jenesis.properties`
or the command line can name another one that answers `application/dns-json`, but a
project cannot:

    jenesis.dns.uri=https://dns.google/resolve

The records
-----------

A module's record is a TXT record at `_java.` followed by the module's name
reversed. For `net.bytebuddy.agent` Jenesis asks `_java.agent.bytebuddy.net` and
then `_java.bytebuddy.net`, and the first name holding a record answers. A module
record comes in two kinds: one says which Maven artifact the module is published as,
the other where its files are.

**Which Maven artifact.** `jenesis.build` publishes its modules to Maven by the
coordinate convention, and says so:

    _java.jenesis.build. TXT "coordinate=build.jenesis:{module}"
    _java.jenesis.build. TXT "maven=https://github.com/jenesis/jenesis/releases/download/v{version}/{artifactId}-{version}{-classifier}.{type}"

`coordinate=` takes `<groupId>:<artifactId>[:<extension>[:<classifier>]]`, a Maven
coordinate as Maven writes one, without its version. `{module}` is the module's name,
so this one record maps every module below `jenesis.build`: `build.jenesis` is
`build.jenesis:build.jenesis`. That artifact is then resolved as
any Maven dependency is, which is where the second record comes in: it is the
`maven=` record of the group `build.jenesis`, shown in `../demo-69-dns-maven`, and it
names the GitHub release. Without one, the artifact comes from the Maven remotes,
Maven Central by default, and a missing version is the newest release its Maven
metadata names. A domain that publishes these two records needs no module
repository at all.

Where artifacts are named after a pattern other than the module name, `{-suffix}`
follows it: it is the part of the module's name below the record's domain, its
labels joined by dashes after a leading one, so nothing for `net.bytebuddy` and
`-agent` for `net.bytebuddy.agent`. One record maps all of Byte Buddy's modules:

    _java.bytebuddy.net. TXT "coordinate=net.bytebuddy:byte-buddy{-suffix}"

and a module whose suffix names no artifact, `net.bytebuddy.utility` as
`byte-buddy-utility`, resolves nothing from it. A coordinate without placeholders
belongs to one module only, the one whose own name holds it, for an artifact that
follows no pattern:

    _java.example.com.     TXT "coordinate=com.example:example-core"
    _java.cli.example.com. TXT "coordinate=com.example:command-line"

**Where its files are.** A module that is not published to Maven names its location
with `javamodule=` instead, in one of two forms:

  * a **template** names each file:

        _java.example.com. TXT "javamodule=https://example.com/releases/v{version}/{module}-{version}{-classifier}.{type}"

    `{module}`, `{-suffix}`, `{version}` and `{type}` are filled in, and
    `{-classifier}` becomes `-sources` for the sources jar and nothing for the jar
    itself. Only a named
    version resolves, since a template cannot list the versions there are, and a
    template without `{-classifier}` or `{type}` serves only the plain jar. Where a
    `.sha512`, `.sha256` or `.sha1` is published beside a file, the file is checked
    against it.
  * a **root**, a URI without placeholders, is a Jenesis module service such as
    `https://repo.jenesis.build/`, asked as `jenesis.module.uri` would ask it.

A more specific name wins whichever kind it holds, and one name holding both a
`coordinate=` and a `javamodule=` record fails the build, as do two records of one
kind, an attribute other than `since`, or a placeholder the kind does not know.

Either kind can be followed, after a space, by the versions it serves. `since=`
names the first one:

    _java.jenesis.build. TXT "coordinate=build.jenesis:{module} since=0.15.0"

and an earlier version is left to the module repository, ordered as Maven orders
versions, so `1.2.3-rc.1` comes before `1.2.3` and `1.10.0` after it. `suffixes=`
names the qualifiers it serves, the part of a version after its first dash, with
`none` for a version without one:

    _java.jenesis.build. TXT "coordinate=build.jenesis:{module} suffixes=none,rc"

serves `1.2.3`, `1.2.3-rc.1` and `1.2.3-RC2`, since a suffix matches the leading
word of a qualifier ignoring case, and leaves `1.2.3-SNAPSHOT` to the module
repository. Without `suffixes=`, every version is served. A request that names no
version cannot be checked against either attribute, so a record holding one leaves
such a request to the module repository.

Trusting the answer
-------------------

A record counts only when the resolver vouches for it through DNSSEC, which
`jenesis.build` is signed with; an unsigned record fails the build rather than
being followed. Signing your zone is the fix. For a zone you cannot sign, your own
`~/.jenesis/jenesis.properties` or the command line may accept unsigned records,
but a project cannot:

    jenesis.dns.secure=false

A location is read over `https` only, and the pin decides whether what arrives is
accepted, however its location was found.
