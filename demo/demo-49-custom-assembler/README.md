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

From this directory, naming the customizer this demo ships:

    java build/jenesis/Make.java -Djenesis.project.customizers=build.custom.Preprocessing

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

    demo/demo-49-custom-assembler
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/custom/Preprocessing.java   the customizer: wraps the assembler of the project
    `-- sources/
        |-- module-info.java     module demo.custom { exports sample; } (@jenesis.main)
        `-- sample/Sample.java    defines GREETING = "${greeting}", prints its substituted value

With a `module-info.java` and no `pom.xml`, Jenesis auto-detects the
MODULAR_TO_MAVEN layout and emits a modular jar (plus a generated POM), exactly
as the `java-modular` demo does. The only difference is the assembler.

How the wrapping works
----------------------

`Preprocessing` is a customizer: a `UnaryOperator<Project>` under `build/custom/` that is
handed the project the build would otherwise run and returns the one to run
instead.

        public class Preprocessing implements UnaryOperator<Project>, Serializable {

            @Override
            public Project apply(Project project) {
                return project.assembler((descriptor, repositories, resolvers) -> project.assembler()
                        .apply(descriptor.sources("preprocess"), repositories, resolvers)
                        .mapBuild(inner -> (sub, inherited) -> {
                            sub.addStep("preprocess", (executor, context, arguments) -> {
                                ... // rewrite ${greeting} into context.next()
                            }, descriptor.sources().stream());
                            inner.accept(sub, inherited);
                        }));
            }
        }

The assembler and the `preprocess` step are both lambdas. A build step is
serialised into the key its output is cached under, so the class whose lambda
becomes a step implements `Serializable`.

`jenesis.project.customizers` names such classes, separated by commas, and the
build applies them in order. Everything else is the stock build: the project a
customizer receives is configured by `jenesis.properties`, the profiles and the
`-Djenesis.*` arguments, and the build it returns runs on the JDK, in the daemon
or in Docker as those settings ask. A customizer runs code of the project's own,
so a file the project provides cannot name one: pass it on the command line, in
an `@<file>` argument, or in your own `~/.jenesis/jenesis.properties`. The
customizer lies outside `build/jenesis`, so `jenesis-validate` still finds the
vendored engine unchanged.

The preprocessing is delivered by an assembler that wraps the stock one.
`Project.assembler(...)` accepts any
`MultiProjectAssembler<? super ProjectModuleDescriptor>`, which is a functional
interface, so the lambda above is the whole assembler. `project.assembler()` is
the `InferredMultiProjectAssembler` the settings configured, and the lambda calls
it for every module.

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

Running the produced jar is left to you (see above). The companion
`build/jenesis/Execute.java` applies the same customizer when it is given the
same setting, so the program it runs is built from the preprocessed sources.
