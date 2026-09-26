Java (multi-module POM) demo
============================

Build a multi-module Maven-style project where one module depends on another:
a root aggregator `pom.xml` lists two sub-modules, and one sub-module depends on
the sibling plus a real external Maven dependency, so you get intra-project and
external resolution side by side with no build script to write. The `greeter`
module also carries a JUnit test, so the demo doubles as a Maven-layout testing
example. Its single-module counterpart is `../demo-01-java-pom`.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Layout
------

The project is an aggregator `pom.xml` over two module directories:

    demo/demo-03-java-pom-multi
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- pom.xml              aggregator (packaging pom); lists the two modules
    |-- greeter/            the library module (with a JUnit test)
    |   |-- pom.xml          <sourceDirectory> + <testSourceDirectory>; a test-scoped JUnit dependency
    |   |-- sources/sample/greeter/Greeter.java
    |   `-- test/sample/greeter/GreeterTest.java
    `-- app/                the consumer module
        |-- pom.xml          depends on greeter + commons-lang3
        `-- sources/sample/app/App.java   uses Greeter + StringUtils

The root `pom.xml` carries `<packaging>pom</packaging>`, so Jenesis treats it as
an aggregator and never builds it as a jar; its presence is what selects the
MAVEN layout (`Project.Layout.of`). Jenesis then walks the tree, discovers every
`pom.xml`, and builds one module per descriptor.

The `app` module declares two dependencies: `build.jenesis.demo:greeter` (the
sibling library) and `org.apache.commons:commons-lang3` (an external artifact).
Jenesis builds `greeter` first, exposes its output through the sibling's `assign`
step, and prepends that as a repository when resolving `app`, so the sibling
coordinate resolves from within the build while `commons-lang3` is fetched from
Maven Central.

Tests
-----

The `greeter` module declares a test source folder and a test-scoped dependency
in its `pom.xml`:

    <build>
        <sourceDirectory>sources</sourceDirectory>
        <testSourceDirectory>test</testSourceDirectory>
    </build>
    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

A `<testSourceDirectory>` turns the module into a main artifact plus a `tests`
variant. Jenesis compiles `test/sample/greeter/GreeterTest.java` against the main
classes and the test-scoped dependencies, detects the test engine from the
resolved jars (JUnit 5 here), and runs it through the JUnit Platform console
launcher as part of the build. The launcher is fetched automatically, and its
version defaults to the one derived from the discovered `org.junit.platform.engine`
module (`1.11.3`), so it matches the JUnit Platform line the tests compile against
with no configuration. That default is only a fallback: the `junit-platform-console`
entry in `<dependencyManagement>` (written by `pin` as part of the full resolved
closure, with its checksum) overrides it, so the pinned version always wins - you
could pin a higher console version here than the derived default and Jenesis would
honor it.

Sharing test code across modules
--------------------------------

Because `greeter` carries a test source folder, Jenesis produces a `tests` variant
of it alongside the main jar. Another module can depend on that variant to reuse
shared test fixtures - declare it in the consumer's `pom.xml` with either form:

    <dependency>
        <groupId>build.jenesis.demo</groupId>
        <artifactId>greeter</artifactId>
        <version>1.0.0</version>
        <type>test-jar</type>          <!-- or: <classifier>tests</classifier> -->
    </dependency>

Both resolve to the same `-tests` artifact: Jenesis normalizes Maven's `test-jar`
type to the `tests` classifier (the same way it handles `javadoc` / `java-source` /
`ejb-client`). Within this build the consumer compiles directly against `greeter`'s
sibling test module; against an already-published project it resolves the deployed
`greeter-<version>-tests.jar`. This demo does not wire such a dependency, to stay
minimal - it is the same sibling resolution shown above, applied to the `tests`
variant.

Selecting modules and filtering tests
-------------------------------------

The default build compiles, jars, and tests every module. Selectors scope a build
to part of the graph.

A `+<module>` selector builds just that module's subtree. `+greeter` builds the
`greeter` library and runs its tests, without touching `app`:

    java build/jenesis/Make.java +greeter

A module's own dependencies come along automatically: `+app` builds `app` and,
because it depends on the sibling, `greeter` too - but no unrelated module:

    java build/jenesis/Make.java +app

A selector is a slash-delimited path of build steps, with two
wildcards: `:` matches a single path segment and `::` matches any depth. So
`::/jar` runs the `jar` step of every module wherever it sits in the tree:

    java build/jenesis/Make.java '::/jar'

Wildcards are lenient (non-matching branches are skipped), and once a module is
reached its own pipeline steps run - so a wildcard chooses *which steps* you ask
for across the tree, not how much of each matched module is rebuilt.

Test runs are narrowed with `-Djenesis.test.filter`, a comma-separated list of
`<class-regex>[#<method>]` patterns; the regex matches the fully-qualified class
name. `greeter` ships two tests, so this runs only one of them:

    java -Djenesis.test.filter='.*GreeterTest#prefix_is_a_greeting' build/jenesis/Make.java

The test step's summary then reports `1 tests successful` instead of the default
`2`. The `-D` flag must come **before** the source file - anything after it is
read as a selector.

Tests can also be selected by tag with `-Djenesis.test.tag`, a comma-separated
list of tag names: a test runs where it carries one of them, names joined by `&`
select the tests carrying all of them, and a name preceded by `!` leaves out the
tests carrying it. Jenesis translates the list into the test
framework's own grouping mechanism - JUnit Platform tags or TestNG groups; JUnit 4
has none its console runner can select. `GreeterTest#prefix_is_a_greeting` is
annotated `@Tag("slow")`, so this again runs only that one test, this time by tag:

    java -Djenesis.test.tag=slow build/jenesis/Make.java

A run remembers what it covered until the tests or what they test change. Asked
for `slow,io` next, the build runs only the tests tagged `io` that are not tagged
`slow`, because the others already passed; asked for `slow` alone, it runs nothing.
`-Djenesis.test.force=true` forgets what ran and runs the whole selection.

Passing `-Djenesis.test.parallel` runs the matched tests in parallel, letting
the test framework execute them concurrently where its configuration allows.

Three more switches round out the test wiring. `-Djenesis.test.reporting=true`
writes machine-readable reports into the module's `reports/tests`, next to the
console summary: the legacy JUnit XML that CI report plugins read, and the JUnit
open-test-reporting XML. `-Djenesis.test.skip=true` skips the test step entirely
- handy when staging an artifact whose tests have already run.
And on a `stage` build, `-Djenesis.stage.tests=true` also stages the module's
`tests`-classifier variant beside the main jar, so the test artifact is published
too (by default only the main artifact is staged).

Pinned dependencies
-------------------

This demo ships **already pinned**. External dependencies are pinned in
`<dependencyManagement>` with a content checksum:

    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-lang3</artifactId>
        <version>3.14.0</version>
        <!--Checksum/SHA-256/...-->
    </dependency>

Running `java build/jenesis/Make.java pin` walks the build outputs and writes
the resolved version closure into each module's `<dependencyManagement>`; it is
idempotent. That closure is project-wide, so the pinned JUnit test artifacts land
in both modules' managed dependencies even though only `greeter` runs tests -
managed entries set versions, not actual dependencies, so this is the same shared
bill-of-materials Maven projects keep in a parent. Pinning records external
coordinates (`commons-lang3`, the JUnit closure) but skips the intra-project
`greeter` dependency, since coordinates produced within the project are never
pinned into dependency management.

Printing the dependency graph
-----------------------------

To see what each module resolves, run the `dependencies` selector. It prints one
tree per module and scope, each starting from the module itself - tagged `local`
with the folder it is built from - with every node carrying its resolved module
name and declared license:

    java build/jenesis/Make.java dependencies

    maven/build.jenesis.demo/greeter 1.0.0 [compile] (local ./greeter)
    └─ maven/org.apache.commons/commons-lang3 3.14.0 [compile] (module org.apache.commons.lang3) {Apache-2.0}

    maven/build.jenesis.demo/app 1.0.0 [compile] (local ./app)
    └─ maven/build.jenesis.demo/greeter 1.0.0 [compile] (local ./greeter)
       └─ maven/org.apache.commons/commons-lang3 3.14.0 [compile] (module org.apache.commons.lang3) {Apache-2.0}

`app` reaches `greeter` as a module built in the same project, so it is tagged
`local` with the folder it comes from wherever it appears. The test module of
`greeter` follows under its own coordinate, `…/greeter/jar/tests`. Repeated
subtrees are dimmed and marked `(*)`, and a `Resolved dependencies:` summary
after each tree lists the final version chosen for every coordinate.

Each node shows the property-file key, version, and Maven scope; a dependency
reached more than once is expanded under its first parent and dimmed with `(*)`
everywhere else.
