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

Because args4j declares no module identity, Jenesis cannot simply put its jar on
the module path under the chosen name - a jar's automatic name comes from its
file, not from a build's wishes. Instead the alias resolver synthesizes a module
locally: it takes a **copy of the args4j jar and injects
`Automatic-Module-Name: org.kohsuke.args4j`** into its manifest, and that copy
carries args4j's packages onto the module path under the aliased name. The
original, identity-less jar stays in the closure on the class path, shadowed by
the named copy that any module-path reader sees first. So `requires
org.kohsuke.args4j` compiles against args4j's classes, and at run time they load
from the module named `org.kohsuke.args4j`.

That last point is why the `opens` directive names the alias. args4j sets the
`@Option` fields by reflection, which needs the `demo.cli` package opened to
args4j's module - and because the alias is a real module name, `opens demo.cli
to org.kohsuke.args4j;` is exactly how you grant it. Run the demo after deleting
that directive and the parse fails with an `InaccessibleObjectException`: proof
that the aliased library really is running as the named module the project
declared.

The version resolves like any Maven coordinate: the `@jenesis.pin` here fixes it
(with a checksum, so `-Djenesis.dependency.pin=strict` is satisfied), an inline
`@jenesis.alias ... <version>` would be the next choice, and with neither the
latest release is negotiated. args4j is pinned to **2.33**, the last release
before it added an `Automatic-Module-Name` of its own - a newer args4j already
has a module identity, so the alias would instead leave the jar untouched and
read its packages under that existing name. Aliasing is for the artifacts that
have no name to offer.

Pinning
-------

`java build/jenesis/Project.java pin` records the aliased target like any other
Maven dependency - `@jenesis.pin args4j/args4j <version> SHA-256/...` - and never
pins the synthetic alias itself. This demo ships already pinned.
