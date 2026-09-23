Custom assembler demo
=====================

Run a preprocessing pass over your Java sources before they are compiled, so the
code that lands in the jar is the transformed version rather than what you wrote
on disk. Here the transformation is a simple textual substitution: the
`${greeting}` placeholder in `Sample.java` is rewritten to a real message before
the regular compile, jar, and test flow runs, so the value compiled into the jar
is the substituted one. Built with the stock build, the placeholder would survive
verbatim.

Run it
------

From this directory:

    java build/jenesis/Make.java

You should see the build graph resolve and run, with a line reporting the
substitution as the `preprocess` step rewrites the source:

    custom-assembler: substituted ${greeting} in sample/Sample.java

To confirm the substituted value is what ended up compiled into the jar, run the
produced modular jar yourself:

    java --module-path \
        target/build/modules/compose/module/module-sources/produce/assemble/binary/artifacts/jar/output/artifacts/classes.jar \
        --module demo.custom

which prints:

    Hello from a source preprocessed by a custom assembler!

Built with the stock assembler instead, the same command would print the
unsubstituted `${greeting}` placeholder.

Layout
------

    demo/demo-50-custom-assembler
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/custom/Preprocessing.java   the customizer: wraps the assembler of the project
    |-- jenesis.properties   jenesis.project.customizer=build.custom.Preprocessing
    `-- sources/
        |-- module-info.java     module demo.custom { exports sample; } (@jenesis.main)
        `-- sample/Sample.java    defines GREETING = "${greeting}", prints its substituted value

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout and emits a modular jar (plus a generated POM), exactly
as the `java-modular` demo does. The only difference is the assembler.

How the wrapping works
----------------------

`Preprocessing` is a customizer: a class under `build/custom/` that is handed the
`InferredMultiProjectAssembler` the build would otherwise use and returns the
assembler to build with instead.

        public class Preprocessing implements Project.Customizer {

            @Override
            public MultiProjectAssembler<? super ProjectModuleDescriptor> apply(InferredMultiProjectAssembler assembler) {
                return (descriptor, repositories, resolvers) -> assembler
                        .apply(descriptor.sources("preprocess"), repositories, resolvers)
                        .mapBuild(stock -> (sub, inherited) -> {
                            sub.addStep("preprocess", (executor, context, arguments) -> {
                                ... // rewrite ${greeting} into context.next()
                            }, descriptor.sources().stream());
                            stock.accept(sub, inherited);
                        });
            }
        }

The assembler and the `preprocess` step are both lambdas. A build step is
serialised into the key its output is cached under, and a lambda serialises with
what it captures, so the step uses only its parameters and constants. An
anonymous class created inside it would capture the customizer, which would then
have to be `Serializable` too.

`jenesis.project.customizer` names the class. This demo names it in
`jenesis.properties`, so a plain `Make` run applies it; the same key on the
command line or in a profile works as well. Everything else is the stock build:
the assembler a customizer receives is configured by `jenesis.properties`, the
profiles and the `-Djenesis.*` arguments, and the build runs on the JDK, in the
daemon or in Docker as those settings ask. The customizer lies
outside `build/jenesis`, so `jenesis-validate` still finds the vendored engine
unchanged.

> [!NOTE]
> A customizer runs the project's own code, just as its tests do. Before you build
> a project you do not trust, run the build in a container with
> `-Djenesis.project.docker=true`. As the `docker-isolation` demo explains, the
> customizer is then applied inside the container and never on your machine, and
> the project cannot switch Docker off.

The preprocessing is delivered by an assembler that wraps the stock one. A
`MultiProjectAssembler<? super ProjectModuleDescriptor>` is a functional
interface, so the lambda above is the whole assembler, and it calls the
`InferredMultiProjectAssembler` the settings configured for every module.

For each module the wrapper does three things:

1. Adds a `preprocess` build step that reads the module's original `sources/`
   tree, copies it into its own output, and rewrites `${greeting}` in every
   `.java` file (other files are linked through unchanged).
2. Hands the stock assembler a `ProjectModuleDescriptor` whose `sources()` is
   redirected to the `preprocess` step instead of the original source folder.
   `ProjectModuleDescriptor` is immutable with a wither per property, so this is
   a one-liner: `descriptor.sources("preprocess")`. Every reference
   accessor (`dependencies`, `sources`, `resources`, `manifests`, `coordinates`,
   `artifacts`, `content`) returns a `SequencedSet<String>`, so a customizer can add folders
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

The customizer also checks that no placeholder survives. Every module the
assembler wires - the checks, the formatters, the toolchain and what it nests,
the documentation - takes additional modules through its `custom` wither, and
wires them next to its own under the name `custom`, so an added name never
collides with a stock one:

        SequencedMap<String, BuildExecutorModule> checks = new LinkedHashMap<>();
        checks.put("placeholders", (executor, inherited) -> executor.addStep("verify", (_, context, arguments) -> {
            ... // fail on a ${ left in the sources/ of any argument
        }, inherited.sequencedKeySet()));
        InferredMultiProjectAssembler checked = assembler.check(check -> check.custom(checks));

An added module reads what the module it is added to reads. The checks read the
module's sources, which the redirection above made the preprocessed ones, so
`check/custom/placeholders/verify` fails the build if the substitution missed a
placeholder, and passes here. Nothing of the stock checks is wrapped or replaced.

Running the produced jar is left to you (see above). The companion
`build/jenesis/Execute.java` reads the same `jenesis.properties` and applies the
same customizer, so the program it runs is built from the preprocessed sources.
