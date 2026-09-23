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
        target/build/modules/compose/module/module-sources/produce/assemble/assemble/binary/artifacts/jar/output/artifacts/classes.jar \
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
    |-- jenesis.properties   jenesis.project.customizers=build.custom.Preprocessing
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
                return project.decorate(new Decoration("assemble")
                        .descriptor(descriptor -> descriptor.sources("preprocess"))
                        .before(descriptor -> (sub, _) -> sub.addStep("preprocess", (executor, context, arguments) -> {
                            ... // rewrite ${greeting} into context.next()
                        }, descriptor.sources().stream())));
            }
        }

The `preprocess` step is a lambda. A build step is
serialised into the key its output is cached under, so the class whose lambda
becomes a step implements `Serializable`.

`jenesis.project.customizers` names such classes, separated by commas, and the
build applies them in order. This demo names its customizer in
`jenesis.properties`, so a plain `Make` run applies it; the same key on the
command line or in a profile works as well. Everything else is the stock build:
the project a customizer receives is configured by `jenesis.properties`, the
profiles and the `-Djenesis.*` arguments, and the build it returns runs on the
JDK, in the daemon or in Docker as those settings ask. The customizer lies
outside `build/jenesis`, so `jenesis-validate` still finds the vendored engine
unchanged.

> [!NOTE]
> A customizer runs the project's own code, just as its tests do. Before you build
> a project you do not trust, run the build in a container with
> `-Djenesis.project.docker=true`. As the `docker-isolation` demo explains, the
> customizer is then applied inside the container and never on your machine, and
> the project cannot switch Docker off.

The preprocessing is delivered by a `Decoration`, which wraps the assembler the
settings configured. `project.decorate(...)` hands it the project's assembler
and keeps the decorated one. For each module the decoration does three things:

1. Its `before` step, `preprocess`, reads the module's original `sources/`
   tree, copies it into its own output, and rewrites `${greeting}` in every
   `.java` file (other files are linked through unchanged).
2. Its `descriptor` operator hands the stock build a `ProjectModuleDescriptor`
   whose `sources()` names the `preprocess` step instead of the original source
   folder. `ProjectModuleDescriptor` is immutable with a wither per property, so
   this is a one-liner: `descriptor.sources("preprocess")`. Every reference
   accessor (`dependencies`, `sources`, `resources`, `manifests`, `coordinates`,
   `artifacts`, `content`) returns a `SequencedSet<String>`, so a customizer can add folders
   as readily as replace them.
3. It runs the stock build unchanged, as a module of its own named `assemble`,
   which is why the jar above sits under `assemble/assemble`. Because its
   `sources()` now points at the preprocessed tree, `javac`, the jar step, and
   (when present) the test step all consume the substituted sources, and the
   rest of the build - dependency resolution, staging, pinning - is untouched.

The stock build sits in a module of its own, so a step the decoration adds never
collides with one the stock build declares, whatever it is named. A step that
should run after the stock build goes in `after` instead of `before`, and names
`assemble` as its input.

That redirection is the whole trick: the custom assembler never reimplements the
Java toolchain, it only interposes a source transformation in front of it. Any
preprocessing that produces a `sources/` tree (template expansion, code
generation, license-header stamping) fits the same shape.

Running the produced jar is left to you (see above). The companion
`build/jenesis/Execute.java` reads the same `jenesis.properties` and applies the
same customizer, so the program it runs is built from the preprocessed sources.
