Bill of materials demo
======================

A curated set of versions, shared as a bill of materials instead of repeated
`@jenesis.pin` tags. This demo consumes both BOM kinds side by side, one per
dependency, because they come with different integrity models:

    /**
     * @jenesis.bom org.slf4j/slf4j-bom 2.0.16
     * @jenesis.bom pin-lang3.properties
     * @jenesis.pin org.slf4j 2.0.16
     * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a125...   (written by the pin goal)
     */
    module demo.bom {
        requires org.slf4j;
        requires org.apache.commons.lang3;
        exports sample;
    }

The first declaration is a **Maven BOM**: the one-slash token follows the pin
grammar's `<groupId>/<artifactId>` shorthand, so the dependencies step fetches
`org.slf4j:slf4j-bom:2.0.16` from Maven Central and imports its effective
`<dependencyManagement>` as if it were declared in the root POM - parent
chains and properties resolved, nested `<scope>import</scope>` BOMs flattened
recursively with Maven's first-wins rules. Here it manages every `org.slf4j`
artifact at `2.0.16`.

The second declaration is a **local file**: files named `pin-<name>.properties`
in the project's BOM location - by default the configuration location, i.e.
`build.jenesis/` under the project root. A dash can never occur in a Java
module name, so the file reference is structurally distinct from a module
coordinate. Here `pin-lang3.properties` supplies `org.apache.commons.lang3`, as both
the module coordinate a Maven BOM cannot express and the hashed artifact:

    org.apache.commons.lang3 = 3.20.0
    org.apache.commons/commons-lang3 = 3.20.0 SHA-256/69e5c9fa35da7a51a5fd2099dfe56a2d8d32cf233e2f6d770e796146440263f4

The location of file BOMs is fixed per project (`-Djenesis.project.boms=<paths>`
or the `boms(...)` builder method to relocate; multiple locations are searched
in order, first hit wins) and deliberately not profile-resolved, so every
build sees the same BOMs. Should several BOMs pin the same coordinate, the
last declared wins - broad curation first, local refinement last.

Why the Maven BOM needs pins and the properties BOM does not
------------------------------------------------------------

The two kinds differ in what can be verified. A properties BOM is a
byte-stable file whose entries carry their own content hashes: pin the
reference (or track the local file) and everything it supplies is sealed, so
`org.apache.commons.lang3` resolves strict-clean without a single pin line. A Maven BOM
cannot offer that: repositories and mirrors re-serialize POMs, so a byte
hash over a downloaded pom is meaningless - the reference takes no checksum,
checksum comments inside the fetched BOM are not read, and its entries carry
versions only. The workflow is instead: declare the BOM, then run

    java build/jenesis/Project.java pin

which pins each artifact the build resolves through the BOM - here
`slf4j-api`, with a hash computed over the downloaded jar. That pin is
committed in this demo, so the build no longer depends on the BOM's
unverifiable content. Entries the dependency graph never touches
(`slf4j-simple`, `jul-to-slf4j`, ...) are deliberately not pinned - they
would only be noise - and keep managing resolution from the fetched BOM;
should one enter the graph later, strict pinning fails closed until the next
`pin` run records it. The bare `org.slf4j 2.0.16` module pin is the one
hand-declared line: a Maven BOM manages Maven coordinates and cannot version
a module name.

Build it
--------

From this directory:

    java build/jenesis/Project.java

Both integrity models satisfy strict pinning: `org.apache.commons.lang3` through the file
BOM's hashed entries, `org.slf4j` through the imported pins:

    java -Djenesis.dependency.pin=strict build/jenesis/Project.java

The BOM file format
-------------------

Keys use the pin token grammar minus the group - the group never appears
inside a BOM:

    acme.core = 2.1.0 SHA-256/...              0 slashes: the module acme.core
    org.slf4j/slf4j-api = 2.0.16 SHA-256/...   1 slash: Maven groupId/artifactId
    maven/com.acme/lib/jar = 1.0.0             2+ slashes: explicit repository/coordinate

Values follow the pin value grammar, `<version>[ <algo>/<hash>]`, including
leading-colon classifier qualifiers; checksums are optional. Platform guards
are not allowed inside a BOM file - guard the declaration instead.

Declaring a BOM
---------------

The `@jenesis.bom` tag mirrors `@jenesis.pin`:

    @jenesis.bom acme.platform                          floating latest from the module repository
    @jenesis.bom acme.platform 2.1.0 SHA-256/ab12...    fetched at a version, content-verified
    @jenesis.bom org.slf4j/slf4j-bom 2.0.16             a Maven BOM's dependencyManagement (this demo)
    @jenesis.bom kotlinc/module/acme.platform 2.1.0     entries merge into the kotlinc group
    @jenesis.bom acme.platform 1.9.0 [legacy]           platform-guarded, like a guarded pin
    @jenesis.bom pin-lang3.properties                a local file (this demo)
    @jenesis.bom kotlinc/pin-lang3.properties        the same file, entries in the kotlinc group

A repository BOM is a properties file published at the module repository's
standard versioned path, `<module>/<version>/<module>.properties`; without a
version the unversioned `<module>/<module>.properties` latest is fetched,
which is only as reproducible as any unpinned dependency and cannot carry a
hash. The local `~/.jenesis` repository is consulted first, so an exported
BOM resolves before the remote one.

Emitting a BOM
--------------

A module can also *publish* a BOM of its own resolved dependency closure. A
`bom.properties` build-configuration file (this demo has one at its root)
switches the modular layouts to emit the closure as a properties file, which
`export` stages into the local Jenesis repository alongside the module jar:

    ~/.jenesis/demo.bom/1-SNAPSHOT/demo.bom.properties

The file is the module's full closure with a version and checksum per
coordinate, keyed group-less exactly like a hand-written BOM, so another
project consumes it with `@jenesis.bom demo.bom` the same way this one
consumes `pin-lang3.properties`. It is published only into the Jenesis
repository; the Maven `export` never emits it. Emission is off unless
`bom.properties` is present, mirroring how `packaging.properties` gates
`jpackage`; the file is otherwise empty and reserved for future options.

Precedence
----------

Local pins always win: an explicit `@jenesis.pin` overrides any BOM entry.
Between BOMs, the last declared wins a conflicting coordinate. Under
`-Djenesis.dependency.pin=strict` a module-repository BOM reference needs a
checksum (its byte-stable properties file is content-verified before the
entries are trusted, and its hashed entries satisfy the strict closure
check); a Maven BOM reference is exempt, since it cannot carry one, and its
safety comes from the pins the pin goal imports instead.

Repinning
---------

The `pin` goal is BOM-aware. By default (`-Djenesis.pin.bom=keep`) it writes
no `@jenesis.pin` for a dependency a properties BOM already supplies and
removes a pin line that became redundant when such a BOM took over its
coordinate - this demo's `pin` run leaves `org.apache.commons.lang3` without a single pin
line - and versioned module-repository references get their content hash
written onto the declaration, so the BOM itself is pinned. A Maven BOM
inverts this: its entries never cover, so the `pin` goal pins each artifact
resolved through `slf4j-bom` with its computed hash (this demo's
`slf4j-api` line), while the declaration itself stays hash-free. Under
`-Djenesis.dependency.pin=ignore`
a versioned BOM reference floats to the latest published BOM and its entries
keep managing resolution while the pins float, so a repin upgrades the whole
curation in one step and rewrites the pins it entails. The inverse migration
is

    java -Djenesis.pin.bom=flatten build/jenesis/Project.java pin

which removes the `@jenesis.bom` declarations and pins the resolved closure
in full, turning this demo's module-info into its `@jenesis.pin` equivalent.
Orthogonally, `-Djenesis.pin.checksum=false` writes versions without hashes,
for pins and BOM references alike.
