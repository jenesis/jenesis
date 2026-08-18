Module alias demo
=================

Require a plain library that carries no module identity of its own. args4j - a
small, widely used command-line parser - ships as an ordinary jar: no
`module-info`, and not even an `Automatic-Module-Name` manifest entry. On the
module path such a jar becomes an *automatic module named after its file*
(`args4j-2.33.jar` -> `args4j`), a name that changes when the file does and that
nothing can stably `requires`. In the MODULAR_TO_MAVEN layout, where resolution
is by module name, that makes the library unrequireable as it stands.

`@jenesis.alias` fixes this by giving the artifact a module name this project
chooses:

    /**
     * @jenesis.main demo.cli.Main
     * @jenesis.alias org.kohsuke.args4j args4j/args4j
     * @jenesis.pin args4j/args4j 2.33 SHA-256/91ddeaba...
     */
    module demo.cli {
        requires org.kohsuke.args4j;

        opens demo.cli to org.kohsuke.args4j;
    }

The tag reads `@jenesis.alias <module-name> <groupId>/<artifactId>`. From then on
`org.kohsuke.args4j` *is* a module: it can be `requires`d, it can be named in an
`opens` directive, and it resolves together with its dependency graph like any
other. The name is this project's to pick; `org.kohsuke.args4j`, matching the
library's package, is the obvious one.

Run it
------

From this directory:

    java build/jenesis/Execute.java -name Ada -shout

`Execute` builds the module and runs its `@jenesis.main` entry point, forwarding
the trailing arguments to `main`. args4j parses them into an `Options` bean and
the program prints:

    HELLO, ADA!

Without arguments it falls back to the defaults and prints `Hello, world!`. The
first run downloads args4j from Maven Central.

The second half of the demo links the same module into a self-contained runtime
image, which needs the `stage` goal:

    java build/jenesis/Project.java stage

Layout
------

    demo/demo-31-module-alias
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
    |-- build.jenesis
    |   |-- modules.properties     mode=declared - rewrite the closure into named modules
    |   `-- packaging.properties   jlink=true - link the result into a runtime image
    `-- sources
        |-- module-info.java   @jenesis.main + @jenesis.alias for args4j
        `-- demo/cli
            |-- Options.java   the @Option-annotated argument bean
            `-- Main.java      parses argv with args4j's CmdLineParser

What the alias does
-------------------

The alias is a **rename**, not a synthesis. args4j's jar arrives in the build's
`resolved/` folder under an encoded Maven coordinate, a file name no legal module
name can be derived from, so the dependencies step moves it to
`resolved/org.kohsuke.args4j.jar`. From that file name the JDK derives exactly
the automatic module the project asked for, and `javac` and `java` agree on it
because both derive it the same way. Nothing inside the jar is touched: bytes,
signature entries and all, which is why the pinned checksum keeps describing the
file that is actually on the command line. The jar in `resolved/` is a hard link
into the shared artifact cache, so the rename never reaches the cache either.

`requires org.kohsuke.args4j;` then means what it says. Because the target really
is a module named that, the qualified `opens demo.cli to org.kohsuke.args4j;`
reaches the code that does the reflecting, and args4j can set the `@Option`
fields. Delete the `opens` directive and the parse fails with an
`InaccessibleObjectException`, exactly as for any other module.

An alias does not have to be required, either. It maps a name onto any jar the
resolved tree already contains, so a project can name a transitive dependency it
never mentions. Here the `requires` is what pulls args4j in: Jenesis translates a
`requires` of an aliased name into a requirement on the aliased Maven coordinate.
Declaring an alias for something the tree does not contain is an error - there is
nothing to rename.

The declaration also travels. Jenesis writes it into this module's own manifest as
`Jenesis-Aliases: org.kohsuke.args4j=args4j/args4j`, so a downstream module that
depends on `demo.cli` inherits the name without redeclaring it, and two modules
that disagree about a name fail the build rather than racing over one file.

The alias itself never carries a version - the tag is exactly two words, and a
third is rejected with an error naming the `@jenesis.pin` line to write instead,
so the version lives in one place only. Three steps decide it: the `@jenesis.pin`
here fixes it (with a checksum, so `-Djenesis.dependency.pin=strict` is
satisfied); failing a pin or a BOM entry, a coordinate the resolved closure
already carries - even as somebody else's transitive dependency - is taken at
exactly the version that closure settled on, because naming a jar must never
raise its version; and only a coordinate nothing else pulls in is fetched as
`LATEST`. args4j is pinned to **2.33**, the last release
before it added an `Automatic-Module-Name` of its own - a newer args4j already
has a module identity, and Jenesis rejects an alias for such a target, since it
can be required under the name it declares. Aliasing is for the artifacts that
have no name to offer.

Because the target becomes a real module rather than a class-path jar, the usual
module-path rules apply to it: a class in the default package, a package shared
with another module, or a name another module already carries is reported by the
tools themselves.

From requirable to linkable
---------------------------

An alias makes args4j *requirable*, but only as an **automatic** module, and an
automatic module declares no `requires` of its own. `jlink` needs a complete,
explicit module graph to decide what belongs in an image, so it refuses one
outright:

    Error: automatic module cannot be used with jlink: org.kohsuke.args4j

That is the gap `build.jenesis/modules.properties` closes, with one key:

    mode=declared

The rewrite runs at the head of the module's assembly. It splits the resolved
closure in two - jars that already declare a `module-info` (here, this project's
own) pass through byte for byte, everything else is renamed to the module name it
is to carry - runs `jdeps` **once** over the whole set to work out what each one
actually reads, and injects a generated `module-info.class` into a copy of each
jar.

That rewritten closure then *replaces* the resolved one for everything the module
builds: `javac` compiles against it, the tests compile and run against it, and
`jlink` links from it. Replacing rather than adding is deliberate. A module graph
that does not hold together - a split package, a `requires` nothing provides, a
jar with no name - is then a compile or test failure, where it is cheap to read
and cheap to fix, instead of a `jlink` error at the end of the pipeline or a
`NoClassDefFoundError` inside a shipped image.

With `jlink=true` beside it in `packaging.properties`, the `stage` goal produces a
runtime that knows about exactly four modules:

    target/stage/runtime/output/bin/java --list-modules

    demo.cli@1-SNAPSHOT
    java.base@25.0.3
    java.xml@25.0.3
    org.kohsuke.args4j open

`org.kohsuke.args4j` is a real, explicit module in the image now, and `java.xml`
came along because `jdeps` found that args4j reads it. The app runs from that
runtime with no class path and no `--add-modules`, nothing but the module graph:

    target/stage/runtime/output/bin/java -m demo.cli/demo.cli.Main -name Ada -shout

    HELLO, ADA!

The `opens demo.cli to org.kohsuke.args4j;` above still does its job there, because
the synthesized module is an `open` module that `exports` every package it
contains - as close to the class path the jar came from as a named module gets.

The three modes
---------------

    mode=declared     rewrite; fail on a jar that declares no name, naming the coordinate
    mode=synthetic    rewrite; name such a jar build.jenesis.pseudo.module<hash>
    mode=none         no rewrite

`declared` is the default, so an empty `modules.properties` is the strict rewrite,
and it is what this demo uses: every module name in the image is one that was
either declared upstream (an `Automatic-Module-Name`) or chosen by this project -
the `@jenesis.alias` above. Drop that alias and the build stops with the
coordinate that has no name, though it would already have stopped earlier, since
nothing could `requires` it.

`synthetic` is for the deep transitive dependency nobody wants to name by hand: it
derives `build.jenesis.pseudo.module<hash>` from the jar's content - the leading
128 bits of its SHA-256 digest in hex, so `sha256sum` on the jar reproduces it.
The name is stable across builds and machines, but it is not one anything should
`requires` in source.

`none` is what an absent file already means, so it exists for one reason: the file
is located per module and the first match wins, so `mode=none` is how one module
opts out of a project-wide file that would otherwise rewrite it.

What the generated descriptor says
----------------------------------

The synthesized module's `provides` come from the jar's own `META-INF/services`,
its `uses` from the `ServiceLoader.load` call sites in its byte code, and its
`requires` from `jdeps`, which keeps the `transitive` and `static` modifiers it
infers.

Because the bytes change, the rewritten jar is no longer the artifact that was
fetched: any signature is stripped (it could not verify anyway), and the entry
loses its checksum in the rewritten index. The parts of the build that describe
what was *fetched* therefore keep reading the resolved closure and never see the
rewrite: the SBOM, the license and vulnerability checks, and the inventory that
`pin` reads - so a rewritten jar's bytes can never end up in a `@jenesis.pin`
checksum. That is also why the `Execute` run above launches against args4j as
fetched, an automatic module as before, while the linked image runs the rewrite.

Pinning
-------

`java build/jenesis/Project.java pin` records the aliased target like any other
Maven dependency - `@jenesis.pin args4j/args4j <version> SHA-256/...` - and never
pins the alias name itself. This demo ships already pinned.
