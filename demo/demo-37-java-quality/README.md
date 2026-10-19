Java code-quality demo
======================

Wire code-quality tools into a single modular Java project. There is no build
script: a tool of the build turns itself on when its configuration file is present
in the `build.jenesis/` configuration directory, and a plugin runs where
`jenesis.plugins.properties` names it and its `plugin-<name>.properties` is
found there. This demo ships eight tools, so the Checkstyle and PMD plugins lint
the sources, the SpotBugs plugin analyses the compiled classes, the
forbiddenapis plugin looks for calls the project forbids in them, the JDK's jdeps and
jdeprscan for JDK internals and APIs scheduled for removal,
the google-java-format plugin verifies the source layout, and every source is checked
for the project's license header. Each tool resolves in its own group,
independently of the module's own dependencies (here, none).

This is the code-quality counterpart to the plain Java demos that precede it;
the same config-file inference applies to the other JVM languages, whose quality
tools turn on the same way.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis auto-detects the MODULAR_TO_MAVEN layout (a `module-info.java`, no
`pom.xml`), compiles the module, and runs every tool whose configuration file it
finds. The first build downloads the tools, so it takes a while; later builds
reuse the cache.

Layout
------

The configuration files sit in the *configuration directory*, which defaults to
`build.jenesis/` under the project root and can be moved with
`-Djenesis.project.configuration=<dir>`:

    demo/demo-37-java-quality
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties names the Checkstyle, PMD and formatter plugins
    |-- jenesis-eclipse.properties the eclipse profile's settings: no google-java-format
    |-- build.jenesis/
    |   |-- plugin-checkstyle.properties  binds checkstyle.xml, and its ${...} properties
    |   |-- checkstyle.xml         the Checkstyle configuration
    |   |-- plugin-pmd.properties  binds pmd.xml
    |   |-- pmd.xml                the PMD rule set
    |-- plugin-spotbugs.properties binds spotbugs-exclude.xml
    |-- spotbugs-exclude.xml       the SpotBugs filter
    |-- plugin-forbiddenapis.properties  forbiddenapis's signatures, binding the file below
    |-- forbidden-signatures.txt   the project's own forbidden calls
    |-- jdeps.properties           activates jdeps (JDK internal APIs the classes use)
    |-- jdeprscan.properties       activates jdeprscan (APIs deprecated for removal)
    |-- plugin-googlejavaformat.properties  google-java-format's options
    |-- licenseheader.properties   the license header every source starts with
    |-- eclipse/
    |   |-- plugin-eclipse.properties  runs the Eclipse formatter, under a profile
    |   `-- eclipse-formatter.xml  the formatter profile Eclipse exports
    |-- spdx/
    |   `-- licenseheader.properties  no text: the header is derived, under a profile
    |-- project.properties         the license and copyright the derived header names
    `-- sources
        |-- module-info.java
        `-- sample
            `-- Sample.java

How Jenesis infers the tools
----------------------------

Each tool of the build is inferred from the presence of its configuration file,
never from a build script:

    jdeps.properties        -> jdeps
    jdeprscan.properties    -> jdeprscan

Checkstyle and PMD are plugins. `jenesis.plugins.properties` names each at the
hook point `check`, which runs it beside the module's own checks:

    checkstyle+check=build.jenesis.plugin.checkstyle
    pmd+check=build.jenesis.plugin.pmd

SpotBugs and forbiddenapis read the compiled classes, at the hook point
`binary/validate`:

    spotbugs+binary/validate=build.jenesis.plugin.spotbugs
    forbiddenapis+binary/validate=build.jenesis.plugin.forbiddenapis

The Java formatters are plugins as well, at the hook point `format`:

    googlejavaformat+format=build.jenesis.plugin.googlejavaformat
    eclipse+format=build.jenesis.plugin.eclipse

A plugin runs in a module where its `plugin-<name>.properties` is found, and that
file binds what the tool reads. `@rules=<path>` hands the file at the path,
relative to the module's folder, to the plugin's input `rules`:

    @rules=../build.jenesis/pmd.xml

That binding is what lets a filter of the configuration directory reach SpotBugs,
which runs *after* `javac` and so only ever sees compiled classes.

A discovered tool can be switched off without deleting its configuration file
through its property, so file discovery alone decides until the property says
otherwise. A linter's property is `report` by default, and `ignore` skips it; the
license header check's is `true` by default, and `false` skips it:

    jenesis.source.<tool>        the plugins Checkstyle, PMD, detekt, ktlint, Scalastyle, scalafmt, CodeNarc
    jenesis.validator.<tool>     SpotBugs, forbiddenapis, jdeps, jdeprscan
    jenesis.format.license       the license header check

For example `-Djenesis.source.checkstyle=ignore` keeps `checkstyle.xml` in place
but skips Checkstyle, while PMD and SpotBugs still run, as
`-Djenesis.plugin.checkstyle=false` leaves out any plugin.

Per-module configuration
------------------------

The configuration directory above is the project-wide default. In a multi-module
project each module may also carry its own configuration, which takes precedence
over the project-wide one for that module:

  - in the MAVEN layout, in a `build.jenesis/` folder next to the module's `pom.xml`;
  - in the MODULAR and MODULAR_TO_MAVEN layouts (this demo uses the latter), under
    the module's `META-INF/build.jenesis/` folder, which the compilers keep out of
    the packaged jar.

A configuration file is looked up in the module's own location first and in the
project-wide configuration directory second, so a module overrides a shared
default by placing its own `checkstyle.xml` (and so on) next to its sources. The
lookup folders are always absolute, and the module and project-wide folders are
de-duplicated when they coincide. Set `-Djenesis.project.configuration=` to an
empty value to drop the project-wide directory entirely and configure every
module purely from its own location.

Where the tools run and what they produce
-----------------------------------------

Every linter runs in the module's `verify` phase, which starts once the module is
built, while the modules that depend on it build and test without waiting for it:
Checkstyle and PMD read the sources, and SpotBugs, forbiddenapis, jdeps and
jdeprscan are *validators* that read the compiled classes. Each tool
writes an XML report into a `reports/<tool>/` subfolder of its step's output, for
example:

    target/build/.../verify-module-sources/check/custom/checkstyle/delegate/check/output/reports/checkstyle/checkstyle-report.xml
    target/build/.../verify-module-sources/check/custom/pmd/delegate/check/output/reports/pmd/pmd-report.xml
    target/build/.../verify-module-sources/validate/custom/spotbugs/delegate/check/output/reports/spotbugs/spotbugs-report.xml
    target/build/.../verify-module-sources/validate/custom/forbiddenapis/delegate/check/output/reports/forbiddenapis/forbiddenapis.txt

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

Checkstyle reads `${config_loc}` as the folder of `checkstyle.xml`, as the Maven
and Gradle plugins define it. A file the configuration names as
`${config_loc}/<path>`, such as a suppressions file beside it, is bound into the
same input, as `@rules/suppressions.xml=<path>`, and re-runs the check when it
changes. Every other `${<property>}` the configuration names is a line of
`plugin-checkstyle.properties`, which takes the place of the Maven plugin's
`propertyExpansion` and Gradle's `configProperties`; without one Checkstyle fails
to load the configuration. Here `checkstyle.xml` sets its severity to
`${checkstyle.severity}`, which the file names as `warning`:

    @rules=../build.jenesis/checkstyle.xml
    checkstyle.severity=warning

`config_loc` is the one property the file cannot set. A value `@<key>` stands
for the setting `-Djenesis.variable.<key>`, `@<key>/<default>` gives it a
default, and `@@` writes a literal `@`.

One configuration can be shared by several projects. A bound file may lie
outside the project, and `@rules/checkstyle.xml=<path>` hands it to Checkstyle
as `checkstyle.xml` whatever it is called:

    mkdir ../shared
    mv build.jenesis/checkstyle.xml ../shared/company.xml
    sed -i "s#^@rules=.*#@rules/checkstyle.xml=../../shared/company.xml#" build.jenesis/plugin-checkstyle.properties

Formatting: verify, and how to reformat
---------------------------------------

The google-java-format plugin is wired in *verify* mode. It never rewrites your
sources during a normal build; instead it fails the build when a file is not
already formatted, which makes it a continuous-integration gate. Try it by
indenting `Sample.java` with spare spaces and rebuilding: the `format` step fails.

Its `plugin-googlejavaformat.properties` holds the formatter's own options:

    style=google|aosp     google-java-format's style, AOSP indenting by four
    reflow=false          leaves a string past the line limit as it is, as
                          Spotless's googleJavaFormat() does unless told otherwise
    javadoc=false         leaves Javadoc as it is
    imports=sort,remove   what is done to the imports: sort, remove the unused
                          ones, both (the default), or none

A key the formatter does not take fails the build, naming the keys it does take,
and a changed option verifies every source again. The Palantir plugin,
`palantir+format=build.jenesis.plugin.palantir`, takes `reflow` and `imports`
alike.

To apply the formatter and rewrite the sources in place, run the build with the
rewrite switch:

    java -Djenesis.format.rewrite=true build/jenesis/Make.java

The same switch drives the ktlint and scalafmt plugins in the language demos.
After a rewrite, a plain `java build/jenesis/Make.java` passes the verify gate
again.

The `eclipse` profile formats with the Eclipse formatter instead. Its
`build.jenesis/eclipse/` folder holds a `plugin-eclipse.properties`, which binds
the formatter profile Eclipse exports, here indenting by two spaces, so the same
source passes it:

    @profile/eclipse-formatter.xml=../build.jenesis/eclipse/eclipse-formatter.xml

Two formatters would fight over the same sources, so `jenesis-eclipse.properties`
switches google-java-format off under the profile with
`jenesis.plugin.googlejavaformat=false`:

    java -Djenesis.make.profiles=eclipse build/jenesis/Make.java

The formatter runs from `org.eclipse.jdt.core` in a plain JVM, with no Eclipse
installation. It reads the newest Java its release knows, whatever compiler
level an exported profile names. Moving the profile file changes nothing, as
long as the binding follows it; editing a setting in it verifies every source
again.

Rewriting with recipes
----------------------

The `openrewrite` profile adds OpenRewrite. Its `build.jenesis/openrewrite/`
folder holds an `openrewrite.properties`, which names the recipes to run, and a
`rewrite.yml` beside it, which declares the project's own:

    recipes=demo.Modernize

    type: specs.openrewrite.org/v1beta/recipe
    name: demo.Modernize
    recipeList:
      - org.openrewrite.java.ChangeMethodName:
          methodPattern: java.lang.String trim()
          newMethodName: strip

The build resolves OpenRewrite by itself where it finds the file, and runs it
before the Java formatter, so what a recipe writes is formatted afterwards. Like
the formatter it verifies by default: a source calling `trim()` fails the build,
printing the change as a diff,

    -    return name.trim();
    +    return name.strip();
    The recipes demo.Modernize change 1 file - run with -Djenesis.format.rewrite=true to apply them

and the rewrite switch applies it:

    java -Djenesis.make.profiles=openrewrite -Djenesis.format.rewrite=true build/jenesis/Make.java

`recipes` is the one value the file takes. A recipe of OpenRewrite's own Java
module needs nothing more; a recipe library is named for the plugin to load
beside OpenRewrite, as
`@jenesis.plugin openrewrite:tool maven/org.openrewrite.recipe/rewrite-static-analysis`
in `module-info.java`, and resolves with it in the group `openrewrite:tool`. Where
the library and OpenRewrite ask for two versions of one module, the build fails
naming the `@jenesis.pin openrewrite:tool/...` line that settles it. The module's
dependencies are OpenRewrite's class path, so a recipe that matches a type of a
library the module uses finds it. A recipe that
would add, delete or move a file fails the build, and
`-Djenesis.format.openrewrite=false` switches OpenRewrite off.

Forbidden APIs
--------------

The forbiddenapis plugin checks the compiled classes, the tests' included, for
calls the project forbids. Its `plugin-forbiddenapis.properties` names them:

    bundled=jdk-unsafe,jdk-deprecated,jdk-non-portable,jdk-system-out
    signatures=forbidden-signatures.txt
    @signatures=../build.jenesis/forbidden-signatures.txt

`bundled` names signatures forbiddenapis ships: `jdk-unsafe` forbids calls that
depend on the default locale, charset or time zone, `jdk-deprecated` deprecated
APIs, `jdk-non-portable` the JDK's internals and `jdk-system-out` printing to
the console. `jdk-unsafe`, `jdk-deprecated` and `jdk-internal` take the Java
release the classes are compiled for, as `jdk-unsafe-25`; the build appends it
where the name leaves it out. `signatures` names files of the project's own
signatures, each bound into the input `signatures` by a line of its own, written
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
`jenesis.plugin.googlejavaformat=false` skips the formatter.

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
