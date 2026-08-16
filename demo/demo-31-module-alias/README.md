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

Layout
------

    demo/demo-31-module-alias
    |-- build/jenesis          symlink to ../../../sources/build/jenesis
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
tools themselves. `jlink` and modular `jpackage` images cannot consume an alias,
since an automatic module is not linkable.

Pinning
-------

`java build/jenesis/Project.java pin` records the aliased target like any other
Maven dependency - `@jenesis.pin args4j/args4j <version> SHA-256/...` - and never
pins the alias name itself. This demo ships already pinned.
