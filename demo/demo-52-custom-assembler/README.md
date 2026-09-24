Custom assembler demo
=====================

Run a preprocessing pass over your Java sources before they are compiled, so the
code that lands in the jar is the transformed version rather than what you wrote
on disk. Here the transformation is a simple textual substitution: the
`${greeting}` placeholder in `Sample.java` is rewritten to a real message before
the regular compile, jar, and test flow runs, so the value compiled into the jar
is the substituted one. Built with the stock build, the placeholder would survive
verbatim.

Changing what the stock steps consume is more than adding to the stock build, so
this demo is a build of its own: an entry point that wires the assembler in code.

Run it
------

From this directory:

    java build/Demo.java

You should see the build graph resolve and run, with a line reporting the
substitution as the `preprocess` step rewrites the source, and then the program
the build produced:

    custom-assembler: substituted ${greeting} in sample/Sample.java
    ...
    Hello from a source preprocessed by a custom assembler!

Layout
------

    demo/demo-52-custom-assembler
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java      the entry point: wraps the stock assembler, builds and runs
    `-- sources/
        |-- module-info.java     module demo.custom { exports sample; } (@jenesis.main)
        `-- sample/Sample.java    defines GREETING = "${greeting}", prints its substituted value

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout and emits a modular jar (plus a generated POM), exactly
as the `java-modular` demo does. The only difference is the assembler.

How the wrapping works
----------------------

`build/Demo.java` configures the project from the settings, as `Make` would, and
replaces its assembler with one that wraps the stock one:

        Environment environment = new Environment(Make.settings(Path.of(".")).keys());
        InferredMultiProjectAssembler checked = InferredMultiProjectAssembler.ofEnvironment(environment)
                .check(...);
        Project project = Project.ofEnvironment(environment, Path.of("."))
                .assembler((descriptor, repositories, resolvers) -> checked
                        .apply(descriptor.sources("preprocess"), repositories, resolvers)
                        .mapBuild(stock -> (sub, inherited) -> {
                            sub.addStep("preprocess", (executor, context, arguments) -> {
                                ... // rewrite ${greeting} into context.next()
                            }, descriptor.sources().stream());
                            stock.accept(sub, inherited);
                        }));
        System.exit(new Execution(project).execute(args));

`Execution` builds the project and runs the module that declares a main class,
as `build/jenesis/Execute.java` does for a stock build. The assembler and the
`preprocess` step are both lambdas. A build step is serialised into the key its
output is cached under, and a lambda serialises with what it captures, so the
step uses only its parameters and constants.

A `MultiProjectAssembler<? super ProjectModuleDescriptor>` is a functional
interface, so the lambda above is the whole assembler, and it calls the stock
`InferredMultiProjectAssembler` for every module.

For each module the wrapper does three things:

1. Adds a `preprocess` build step that reads the module's original `sources/`
   tree, copies it into its own output, and rewrites `${greeting}` in every
   `.java` file (other files are linked through unchanged).
2. Hands the stock assembler a `ProjectModuleDescriptor` whose `sources()` is
   redirected to the `preprocess` step instead of the original source folder.
   `ProjectModuleDescriptor` is immutable with a wither per property, so this is
   a one-liner: `descriptor.sources("preprocess")`. Every reference
   accessor (`dependencies`, `sources`, `resources`, `manifests`, `coordinates`,
   `artifacts`, `content`) returns a `SequencedSet<String>`, so a build can add folders
   as readily as replace them.
3. Runs the stock assembler's module unchanged. Because its `sources()` now
   points at the preprocessed tree, `javac`, the jar step, and (when present)
   the test step all consume the substituted sources, and the rest of the build
   - dependency resolution, staging, pinning - is untouched.

That redirection is the whole trick: the custom assembler never reimplements the
Java toolchain, it only interposes a source transformation in front of it. Any
preprocessing that produces a `sources/` tree (template expansion, code
generation, license-header stamping) fits the same shape.

Adding a module beside the stock ones
-------------------------------------

The build also checks that no placeholder survives. Every module the stock
assembler wires - the checks, the formatters, the toolchain and what it nests,
the documentation - takes additional steps and modules through its `custom`
withers, and wires them next to its own under the name `custom`, so an added
name never collides with a stock one:

        InferredMultiProjectAssembler checked = InferredMultiProjectAssembler.ofEnvironment(environment)
                .check(check -> check.custom("placeholders", (_, _, arguments) -> {
                    ... // fail on a ${ left in the sources/ of any argument
                }));

`custom(name, step)` adds one step and `custom(name, module)` one module; both
refuse a name that is taken already, and `custom(map)` sets all of them at once.
An added step reads what the module it is added to reads. The checks read the
module's sources, which the redirection above made the preprocessed ones, so
`check/custom/placeholders` fails the build if the substitution missed a
placeholder, and passes here. Nothing of the stock checks is wrapped or replaced.
