Java code-quality demo
======================

Wire code-quality tools into a single modular Java project. There is no build
script: each tool turns itself on when a properties file named after it, such as
`checkstyle.properties`, or the tool's own file, such as `pmd.xml`, is present in
the `build.jenesis/` configuration folder.
This demo ships eight tools, so Checkstyle and PMD lint the sources, SpotBugs
analyses the compiled classes, forbiddenapis looks for calls the project forbids
in them, the JDK's jdeps and jdeprscan for JDK internals and APIs scheduled for
removal, google-java-format verifies the source layout, and every source is
checked for the project's license header. Each tool resolves in its own group,
independently of the module's own dependencies (here, none).

This is the code-quality counterpart to the plain Java demos that precede it;
the quality tools of the other JVM languages turn on the same way.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis auto-detects the MODULAR_TO_MAVEN layout (a `module-info.java`, no
`pom.xml`), compiles the module, and runs every tool whose properties file or
own file it finds. The first build downloads the tools, so it takes a while; later builds
reuse the cache.

Layout
------

The configuration files sit in the *configuration folder*, which defaults to
`build.jenesis/` under the project root and can be moved with
`-Djenesis.project.configuration=<dir>`. A subfolder named after a profile, such
as `build.jenesis/eclipse/`, is read only under that profile:

    demo/demo-37-java-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   |-- checkstyle.properties  switches Checkstyle on: its configuration and ${...} properties
    |   |-- checkstyle.xml         the Checkstyle configuration
    |   |-- pmd.xml                switches PMD on: the PMD rule set
    |   |-- spotbugs-exclude.xml   switches SpotBugs on: its filter
    |   |-- forbiddenapis.properties  switches forbiddenapis on, naming its signatures
    |   |-- forbidden-signatures.txt  the project's own forbidden calls
    |   |-- jdeps.properties       switches jdeps on (JDK internal APIs the classes use)
    |   |-- jdeprscan.properties   switches jdeprscan on (APIs deprecated for removal)
    |   |-- javaformat.properties  selects google-java-format, with its options
    |   |-- licenseheader.properties  the license header every source starts with
    |   |-- eclipse/
    |   |   |-- javaformat.properties  selects the Eclipse formatter, under a profile
    |   |   `-- eclipse-formatter.xml  the formatter profile Eclipse exports
    |   |-- openrewrite/
    |   |   `-- rewrite.yml        switches OpenRewrite on, under a profile: its recipes
    |   `-- spdx/
    |       `-- licenseheader.properties  no text: the header is derived, under a profile
    |-- project.properties         the license and copyright the derived header names
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.java

How Jenesis infers the tools
----------------------------

Each tool is switched on by a file in a configuration folder, never by a build
script:

    checkstyle.properties       -> Checkstyle
    pmd.xml                     -> PMD
    spotbugs-exclude.xml        -> SpotBugs
    forbiddenapis.properties    -> forbiddenapis
    jdeps.properties            -> jdeps
    jdeprscan.properties        -> jdeprscan
    javaformat.properties       -> the Java formatter it selects
    licenseheader.properties    -> the license header check

jdeps and jdeprscan are tools of the JDK and the license header check is part of
the build, so none of the three resolves anything. Every other tool is an official plugin, `build.jenesis.plugin.<tool>`
from `jenesis/jenesis-plugins`, which the build resolves by itself where it finds
the tool's file; no `jenesis.plugins.properties` names it.

A linter has two ways in. The tool's own file under its conventional name -
`checkstyle.xml`, `pmd.xml`, `spotbugs-exclude.xml` - switches it on by itself and
is handed to the tool as it stands, as `pmd.xml` is here. A properties file
named after the tool switches it on as well and names the tool's own file as
`configuration`, resolved against the folder of the properties file itself, as
`checkstyle.properties` does here:

    configuration=checkstyle.xml

The build hands that file to the tool under the name the tool reads, whatever
the file is called: Checkstyle always sees `checkstyle.xml`, PMD `pmd.xml`. The
other lines of a properties file are the plugin's values, so a tool that needs
one, as Checkstyle needs `checkstyle.severity` here, is configured by its
properties.

Where a configuration folder holds the properties, the conventional file is
never read in their place: a properties file that leaves `configuration` out
fails the build, naming the key to add,

    .../build.jenesis/checkstyle.properties names no configuration - add configuration=checkstyle.xml, the file relative to checkstyle.properties

and a `configuration` naming a file that does not exist fails it as well,
naming the file it looked for. SpotBugs runs without a filter, so a
`spotbugs.properties` may leave `configuration` out and switch SpotBugs on
with no filter at all.

A discovered tool can be switched off without deleting its file through its
setting, so the file alone decides until the setting says
otherwise. A linter's setting is `report` by default, and `ignore` skips it; the
Java formatter's and the license header check's are `true` by default, and
`false` skips them:

    jenesis.source.<tool>        Checkstyle, PMD, detekt, ktlint, Scalastyle, scalafmt, CodeNarc
    jenesis.validator.<tool>     SpotBugs, forbiddenapis, jdeps, jdeprscan
    jenesis.format.java          the Java formatter javaformat.properties selects
    jenesis.format.license       the license header check

For example `-Djenesis.source.checkstyle=ignore` keeps `checkstyle.properties`
in place but skips Checkstyle, while PMD and SpotBugs still run.

Per-module configuration
------------------------

The configuration folder above is the project-wide default. In a multi-module
project each module may also carry its own configuration, which takes precedence
over the project-wide one for that module:

  - in the MAVEN layout, in a `build.jenesis/` folder next to the module's `pom.xml`;
  - in the MODULAR and MODULAR_TO_MAVEN layouts (this demo uses the latter), under
    the module's `META-INF/build.jenesis/` folder, which the compilers keep out of
    the packaged jar.

A configuration file is looked up in the module's own location first and in the
project-wide configuration folder second, so a module overrides a shared
default by placing its own `checkstyle.properties` (and so on), with the file it
names, or its own `pmd.xml`, in its own location. The lookup folders are always absolute, and the
module and project-wide folders are de-duplicated when they coincide. Set
`-Djenesis.project.configuration=` to an empty value to drop the project-wide
folder entirely and configure every module purely from its own location.

Where the tools run and what they produce
-----------------------------------------

Every linter runs in the module's `verify` phase, which starts once the module is
built, while the modules that depend on it build and test without waiting for it:
Checkstyle and PMD read the sources, and SpotBugs, forbiddenapis, jdeps and
jdeprscan are *validators* that read the compiled classes. Each tool
writes an XML report into a `reports/<tool>/` subfolder of its step's output, for
example:

    target/build/.../verify-module-sources/check/checkstyle/delegate/check/output/reports/checkstyle/checkstyle-report.xml
    target/build/.../verify-module-sources/check/pmd/delegate/check/output/reports/pmd/pmd-report.xml
    target/build/.../verify-module-sources/validate/spotbugs/delegate/check/output/reports/spotbugs/spotbugs-report.xml
    target/build/.../verify-module-sources/validate/forbiddenapis/delegate/check/output/reports/forbiddenapis/forbiddenapis.txt

A `stage` build collects every report from every module into one place, each kind
in its own subfolder: `target/stage/reports/output/<kind>/<module>/`, for example
`target/stage/reports/output/checkstyle/sources/checkstyle-report.xml`.

By default the linters are report-only: they record findings but do not fail the
build. A linter that found something says so in one line of the build's output,
with the number of findings and where its report is:

    [FINDINGS] checkstyle found 2 findings, reported in target/build/.../checkstyle/delegate/check/output/reports/checkstyle/checkstyle-report.xml

`-Djenesis.print.findings=false` leaves that line out. To turn findings into a
build failure, set the tool's property to `strict`, on the command line or in
`jenesis.properties`. For example `-Djenesis.source.checkstyle=strict` fails the
build when Checkstyle reports a violation, naming the number of findings and the
report.
Checkstyle, PMD, detekt, ktlint, Scalastyle and scalafmt fail as their own exit
code decides, so a Checkstyle finding at severity `warning` is reported but does
not fail the build; SpotBugs, CodeNarc, forbiddenapis, jdeps and jdeprscan fail on any finding their
report holds.
Report-only covers findings, not a tool that never ran: a linter that fails on
its own configuration, and so writes no complete report, fails its step either
way.

Checkstyle reads `${config_loc}` as the folder of its configuration, as the
Maven and Gradle plugins define it. A file the configuration names as
`${config_loc}/<path>` beside or below it, such as a suppressions file, is handed
to Checkstyle along with the configuration and re-runs the check when it
changes; a path that leaves the folder fails the build. Every other
`${<property>}` the configuration names is a line of `checkstyle.properties`,
which takes the place of the Maven plugin's `propertyExpansion` and Gradle's
`configProperties`; without one Checkstyle fails to load the configuration. Here
`checkstyle.xml` sets its severity to `${checkstyle.severity}`, which
`checkstyle.properties` names as `warning`:

    configuration=checkstyle.xml
    checkstyle.severity=warning

`config_loc` is the one property the file cannot set.

One configuration can be shared by several projects. `configuration` may name a
file outside the project, and Checkstyle still reads it as `checkstyle.xml`,
whatever it is called. Moved out of this demo, the file is named relative to
`build.jenesis/`, the folder of `checkstyle.properties`:

    mkdir ../shared
    mv build.jenesis/checkstyle.xml ../shared/company.xml
    sed -i "s#^configuration=.*#configuration=../../shared/company.xml#" build.jenesis/checkstyle.properties

Formatting: verify, and how to reformat
---------------------------------------

The Java formatter is selected by `javaformat.properties`: its `formatter` line
names `google`, `palantir` or `eclipse`, and its other lines are that
formatter's options. This demo's selects google-java-format:

    formatter=google
    reflow=false

The formatter runs in *verify* mode. It never rewrites your sources during a
normal build; instead it fails the build when a file is not already formatted,
which makes it a continuous-integration gate. Try it by indenting `Sample.java`
with spare spaces and rebuilding: the `format` step fails.

google-java-format takes these options:

    style=google|aosp     google-java-format's style, AOSP indenting by four
    reflow=false          leaves a string past the line limit as it is, as
                          Spotless's googleJavaFormat() does unless told otherwise
    javadoc=false         leaves Javadoc as it is
    imports=sort,remove   what is done to the imports: sort, remove the unused
                          ones, both (the default), or none

A key the formatter does not take fails the build, naming the keys it does take,
and a changed option verifies every source again. A `formatter` other than the
three fails the build as well. Palantir Java Format, `formatter=palantir`, takes
`reflow` and `imports` alike.

To apply the formatter and rewrite the sources in place, run the build with the
rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

The same switch drives the ktlint and scalafmt plugins in the language demos.
After a rewrite, a plain `java build/jenesis/Make.java` passes the verify gate
again.

The `eclipse` profile formats with the Eclipse formatter instead. Its
`build.jenesis/eclipse/` folder holds a `javaformat.properties` of its own:

    formatter=eclipse
    profile=eclipse-formatter.xml

A profile's folder is searched before `build.jenesis/`, so under the profile
this file takes the place of the one selecting google-java-format, and the two
formatters never fight over the same sources. `profile` names the formatter
profile Eclipse exports, relative to the properties file, here indenting by two
spaces, so the same source passes it:

    java -Djenesis.make.profiles=eclipse build/jenesis/Make.java

The formatter runs from `org.eclipse.jdt.core` in a plain JVM, with no Eclipse
installation, and takes no option besides `profile`. It reads the newest Java
its release knows, whatever compiler level an exported profile names. Moving the
profile file changes nothing, as long as `profile` follows it; editing a setting
in it verifies every source again.

Rewriting with recipes
----------------------

The `openrewrite` profile adds OpenRewrite. Its `build.jenesis/openrewrite/`
folder holds OpenRewrite's own file, a `rewrite.yml`, which switches OpenRewrite
on and declares the recipe the project runs:

    type: specs.openrewrite.org/v1beta/recipe
    name: demo.Modernize
    recipeList:
      - org.openrewrite.java.ChangeMethodName:
          methodPattern: java.lang.String trim()
          newMethodName: strip

The build resolves OpenRewrite by itself where it finds `rewrite.yml`, runs every
recipe it declares, and runs it before the Java formatter,
so what a recipe writes is formatted afterwards. Like the formatter it verifies
by default: a source calling `trim()` fails the build, printing the change as a
diff,

    -    return name.trim();
    +    return name.strip();
    The recipes demo.Modernize change 1 file - run with -Djenesis.format.rewrite=true to apply them

and the rewrite switch applies it:

    java -Djenesis.make.profiles=openrewrite -Djenesis.format.rewrite=true build/jenesis/Make.java

An `openrewrite.properties` takes the place of the file where more is wanted: it
names a `rewrite.yml` as `configuration=<path>`, relative to it, and the
comma-separated recipes to run instead of every one the file declares as
`recipes`. With `recipes` and no `configuration`, OpenRewrite runs a recipe of
its own, as `recipes=org.openrewrite.java.RemoveUnusedImports`; properties naming
neither fail the build, naming both keys. A recipe of OpenRewrite's own Java module needs
nothing more; a recipe library is named for the plugin to load beside
OpenRewrite, as
`@jenesis.plugin openrewrite:tool maven/org.openrewrite.recipe/rewrite-static-analysis`
in `module-info.java`, and resolves with it in the group `openrewrite:tool`. Where
the library and OpenRewrite ask for two versions of one module, the build fails
naming the `@jenesis.pin openrewrite:tool/...` line that settles it. The module's
dependencies are OpenRewrite's class path, so a recipe that matches a type of a
library the module uses finds it. A recipe that would add, delete or move a file
fails the build, and `-Djenesis.format.openrewrite=false` switches OpenRewrite off.

Forbidden APIs
--------------

The forbiddenapis plugin checks the compiled classes, the tests' included, for
calls the project forbids. Its `forbiddenapis.properties` switches it on and
names them:

    bundled=jdk-unsafe,jdk-deprecated,jdk-non-portable,jdk-system-out
    signatures=forbidden-signatures.txt

`bundled` names signatures forbiddenapis ships: `jdk-unsafe` forbids calls that
depend on the default locale, charset or time zone, `jdk-deprecated` deprecated
APIs, `jdk-non-portable` the JDK's internals and `jdk-system-out` printing to
the console. `jdk-unsafe`, `jdk-deprecated` and `jdk-internal` take the Java
release the classes are compiled for, as `jdk-unsafe-25`; the build appends it
where the name leaves it out. `signatures` names files of the project's own
signatures, comma-separated and relative to `forbiddenapis.properties`, written
as forbiddenapis reads them:

    @defaultMessage Name a thread: an unnamed one is hard to tell apart in a thread dump
    java.lang.Thread#<init>(java.lang.Runnable)

The other keys are `suppress` (annotations that silence a finding, as
`**.SuppressForbidden`), `includes` and `excludes` (patterns of class files),
`warn` (signatures reported without counting as a finding),
`allowmissingclasses` and `ignoresignaturesofmissingclasses`; any other key
fails the build. Each finding is one line of the report, naming the call, the
reason and where it was made.

JDK internals and removals
--------------------------

Two tools of the JDK itself check what the classes ask of the JDK, the tests'
included, and need nothing to resolve. An empty `jdeps.properties` runs
`jdeps --jdk-internals`, which names every JDK internal API a class uses and
what jdeps suggests instead, as for a class reading `sun.misc.Unsafe`:

    sample.Legacy -> sun.misc.Unsafe: JDK internal API (jdk.unsupported), instead: See https://openjdk.org/jeps/260

Its one key, `accept`, names the internal classes or packages a project uses on
purpose, as `accept=sun.misc`, which are then no finding. An empty
`jdeprscan.properties` runs `jdeprscan --for-removal`, which names every use of
an API the JDK has scheduled for removal, as of `Thread.stop()`:

    sample/Legacy uses deprecated method java/lang/Thread::stop()V (forRemoval=true)

`removal=false` reports every deprecated API instead, and `release=<n>` checks
against the deprecations of that Java release. Both run the tool of the JDK the
build runs on, so building on a newer JDK, as
`-Djenesis.toolchain.version=<n>` does, finds what that JDK encapsulated or
scheduled for removal before the code runs on it.

License headers
---------------

A `licenseheader.properties` holds the header every source starts with, as the
text of its `text` key, `\n` separating the lines and a trailing `\` continuing
the value on the next line:

    text=Copyright 2026 the Jenesis demo authors.\n\n\
      Licensed under the Apache License, Version 2.0.

The file takes no other key. Without `text`, the header is derived from what the
project declares: the one license of its `pom.xml` or its `project.properties`,
named by its SPDX identifier, and its copyright where one is declared. The demo's
`project.properties` declares both:

    license.apache_2_0.name=Apache License, Version 2.0
    license.apache_2_0.url=https://www.apache.org/licenses/LICENSE-2.0.txt
    copyright=2026 the Jenesis demo authors

and its `spdx` profile holds an empty `licenseheader.properties`, so

    java -Djenesis.make.profiles=spdx -Djenesis.format.rewrite=true build/jenesis/Make.java

starts every source with

    // SPDX-FileCopyrightText: 2026 the Jenesis demo authors
    // SPDX-License-Identifier: Apache-2.0

A project that declares no license, or several, or one without a known SPDX
identifier fails the build, naming what it found; `spdx.properties` maps a license
name or URL to its identifier with `alias/<license name>=<id>`.

Every Java source, `module-info.java` and `package-info.java` included, and
every Kotlin, Scala and Groovy source must start with it as a block comment, or
as the file stands where it is written as a comment already. The header is taken
exactly as it stands, year included: a source without it, or with a different
one, fails the build, naming the file. Generated sources are not checked.

The rewrite switch writes the header as it stands: it replaces a leading comment
that is a license header, and inserts one where there is none, so a rewrite gives
the same sources on every day it runs:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

`-Djenesis.format.license=false` skips the check, as
`-Djenesis.format.java=false` skips the Java formatter.

Pinning
-------

Each tool resolves in its own group (`checkstyle:tool`, `pmd:tool`, `spotbugs:tool`,
`forbiddenapis:tool`, `googlejavaformat:tool`, and `eclipse:tool` under the `eclipse`
profile), kept separate from the module's `main`-group dependencies, and floats a
`RELEASE` version until pinned; a plugin's own jars are pinned apart, in the group
of its name. The license header check, jdeps and jdeprscan need no tool. The `dependencies`
selector therefore shows none of them as a dependency of the module;
`-Djenesis.tree.tools=true` prints each tool's closure apart, under a heading
naming its group. Running
`java build/jenesis/Make.java pin` records every resolved tool jar with its
`SHA-256` into `@jenesis.pin` tags, exactly as the other demos pin their
compilers; a tool only a profile turns on is pinned by running `pin` with that
profile active. These closures are large (PMD's CLI bundle alone pulls in well over a
hundred artifacts), which is why the `module-info.java` here carries so many pin
lines: the demo builds under strict pinning, so the whole tool chain is
checksum-verified.
