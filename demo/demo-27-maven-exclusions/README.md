Maven dependency exclusions demo
================================

Exclude an unwanted transitive dependency so it never reaches the class path.
You declare one dependency with an `<exclusions>` block in a Maven-layout
`pom.xml`, point Jenesis at the project, and a bundled test proves the excluded
transitive is gone.

Build it
--------

    java build/jenesis/Project.java

The build compiles the sources and runs `ExclusionTest`, which checks the class
path directly:

- `commons-text`'s `StringSubstitutor.class` **is** present (the dependency
  resolved);
- `commons-lang3`'s `StringUtils.class` is **not** present (the exclusion took
  effect).

The test looks the classes up as **resources** rather than with
`Class.forName(...)`: loading a Commons Text class would link it against the
now-missing Commons Lang and fail with `NoClassDefFoundError` - which is itself a
good illustration of why excluding a hard transitive is usually something you do
to *replace* it, not to drop it outright. Resource lookup just asks whether each
`.class` is on the class path, which is exactly what the exclusion changes.

Like Maven, the exclusion applies to the whole module, test class path included:
test scope extends compile scope, so the pruned transitive is absent when the
tests run, not just when the main code does.

Layout
------

A single Maven-layout project: a `pom.xml`, the sources, and the test that proves
the exclusion took effect.

    demo-27-maven-exclusions
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- pom.xml                  depends on commons-text, excluding its commons-lang3 transitive; ships pinned
    |-- sources/sample/Sample.java
    `-- test/sample/ExclusionTest.java   asserts commons-text is present and commons-lang3 is not

The dependency
--------------

Apache Commons Text normally pulls in Apache Commons Lang as a transitive
dependency. The POM keeps Commons Text but drops Lang:

    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-text</artifactId>
        <version>1.12.0</version>
        <exclusions>
            <exclusion>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
            </exclusion>
        </exclusions>
    </dependency>

The same thing without a pom
----------------------------

Exclusions are a Maven-graph concept: a dependency drags in a transitive subtree
and `<exclusions>` prunes part of it. That subtree exists wherever a pom decides
it, so it exists in the `MODULAR_TO_MAVEN` layout too - there a `requires` is
resolved by fetching the module's pom and walking it, which is the same walk, and
an upstream pom that declares a dependency it should not have declared is the same
problem. There the declaration is a tag rather than a `pom.xml` element:

    /**
     * @jenesis.exclude org.apache.commons.text org.apache.commons/commons-lang3
     */
    module demo.sample {
        requires org.apache.commons.text;
    }

One line names one module and any number of targets, and repeated lines for one
module add up, so a list of upstream mistakes stays readable:

    @jenesis.exclude some.module org.example/wrong-lib commons-logging/commons-logging

Each target is an exact `<groupId>/<artifactId>` - an exclusion names an artifact,
not one of its variants, so there is no version, type or classifier slot. Excluding
from a module the `module-info.java` does not `requires` is an error rather than a
silent no-op.

What it does is what `<exclusions>` does here: the artifact takes the whole subtree
it pulled in with it, and because it never enters the resolved closure there is
nothing left to leak. It is off the compile and test paths, out of any linked
image, absent from the generated pom, and absent from the `sbom` and the compliance
report - which is the honest answer, since the build genuinely never fetched it.

The **purely modular** layout is the one place this cannot apply. Resolution there
matches module descriptors against the Jenesis module repository and never reads a
pom at all, so there is no transitive pom dependency to prune; the tag is rejected
rather than ignored.

Pinned dependencies
-------------------

This demo ships **already pinned**: `java build/jenesis/Project.java pin` records
the resolved closure into `<dependencyManagement>` with `SHA-256` checksums.
Because `commons-lang3` is excluded, it is absent from that closure - the pinned
set contains `commons-text` and the JUnit test dependencies, but no Lang.
