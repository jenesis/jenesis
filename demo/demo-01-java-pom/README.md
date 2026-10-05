Java (POM-based) demo
=====================

The simplest way to build a Maven-style Java project with Jenesis: a `pom.xml`
and your sources. You point Jenesis at the project and it resolves the declared
dependency, compiles against it, and produces a jar - there is no build script to
write. It also has a modular counterpart that builds the same kind of project
from a `module-info.java` instead of a `pom.xml`.

Build it
--------

From this directory:

    java build/jenesis/Make.java

Jenesis auto-detects the MAVEN layout from the `pom.xml`, resolves and downloads
`commons-lang3` from Maven Central (or `~/.m2`), and compiles `Sample.java`
against it.

No selector was passed, so the **default target** ran: `build`, which resolves,
compiles, packages and tests every module. Everything else is named explicitly -
`pin`, `stage`, `dependencies` below - so a command with nothing after it is
always the whole build, and the demos never spell `build` out.

Reading the outcome
-------------------

Besides the progress lines, every build writes what happened to
`target/.jenesis.events.jsonl`, one JSON object per line, replaced by the next
build; the second progress line, `[EVENTS]`, names the file. A script or a coding
agent reads it instead of parsing the console:

    {"status":"started","target":"/.../demo-01-java-pom/target"}
    {"status":"resolved","module":"build","seconds":0.063}
    {"status":"executed","step":"build/maven/compose/module/module-/produce/assemble/binary/compiled/compile/javac","seconds":0.207,"folder":"/.../target/build/maven/compose/module/module-/produce/assemble/binary/compiled/compile/javac"}
    {"status":"executed","step":"build/maven/compose/module/module-/produce/assemble/binary/artifacts/jar","seconds":0.020,"folder":"/.../target/build/maven/compose/module/module-/produce/assemble/binary/artifacts/jar"}
    {"status":"completed","seconds":6.152,"executed":19,"skipped":0,"failed":0}

A step is `executed`, `skipped` because nothing it reads changed, or `failed`
with the `error` and `message` that stopped it, and `folder` is where its
`output/` lives. The last line says whether the build `completed` or `failed`;
a file without one belongs to a build that is still running or was killed.
`-Djenesis.executor.events=false` writes no file.

Layout
------

    demo/demo-01-java-pom
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- pom.xml              Maven coordinates, <sourceDirectory>, one <dependency>; ships pinned
    `-- sources/sample/Sample.java

`Sample.java` uses `org.apache.commons.lang3.StringUtils`, which is what makes
the build resolve the real dependency. Nothing else is configured: the build
compiles with plain `javac`.

Printing the dependency graph
-----------------------------

To see what the build resolves, run the `dependencies` selector. It prints each
module's resolved dependency graph, annotated with the resolved module name and
the declared license:

    java build/jenesis/Make.java dependencies

    maven/build.jenesis.demo/java-demo 1.0.0 [compile] (local ./)
    └─ maven/org.apache.commons/commons-lang3 3.14.0 [compile] (module org.apache.commons.lang3) {Apache-2.0}

In a terminal the tree is coloured. Redirected to a file or piped into another
program, as a script or a coding agent reads it, the same output is plain text;
`-Djenesis.print.color=true` keeps the colour there and `=false` drops it in a
terminal as well.

The tree starts from the project itself, drawn like any module built here: its
coordinate, version and scope, tagged `local` with the folder it is built from.
Each node below it shows the property-file key, the requested version (with the
negotiated version inline when it differs), the Maven scope, the resolved module
name, and the declared license.

Opening it in your IDE
----------------------

If you want to run or debug this project from an IDE, the `ide` selector generates
the project metadata for you (run a sub-step like `ide/idea` for just one editor):

    java build/jenesis/Make.java ide

It writes IntelliJ IDEA, VS Code, and Eclipse files at the project root from the
resolved sources and dependencies - see *Generating IDE metadata* in the root
README for details.

Pinned dependency
-----------------

This demo ships **already pinned**. `java build/jenesis/Make.java pin`
records the resolved dependency (with its content checksum) in the POM's
`<dependencyManagement>` block:

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>3.14.0</version>
                <!--Checksum/SHA-256/...-->
            </dependency>
        </dependencies>
    </dependencyManagement>

A POM-based pure-Java project is compiled by the JDK's `javac` - there is no
*resolved* compiler, hence no separate compiler scope. So its pins are
ordinary project dependencies in `<dependencyManagement>`, unlike the
Kotlin/Scala demos whose compilers pin under the `kotlin` / `scala` scope.
