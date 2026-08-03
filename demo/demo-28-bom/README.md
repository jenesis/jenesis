Bill of materials demo
======================

A curated set of versions, shared as a bill of materials instead of repeated
`@jenesis.pin` tags. The module declares only *what* it requires; the BOMs
say *which version*:

    /**
     * @jenesis.bom org.slf4j/slf4j-bom 2.0.16
     * @jenesis.bom bom-platform.properties
     */
    module demo.bom {
        requires org.slf4j;
        exports sample;
    }

The first declaration is a **Maven BOM**: the one-slash token follows the pin
grammar's `<groupId>/<artifactId>` shorthand, so the dependencies step fetches
`org.slf4j:slf4j-bom:2.0.16` from Maven Central and imports its effective
`<dependencyManagement>` as if it were declared in the root POM - parent
chains and properties resolved, nested `<scope>import</scope>` BOMs flattened
recursively with Maven's first-wins rules. Here it manages every `org.slf4j`
artifact at `2.0.16`.

The second declaration is a **local file**: files named `bom-<name>.properties`
in the project's BOM location - by default the configuration location, i.e.
`build.jenesis/` under the project root. A dash can never occur in a Java
module name, so the file reference is structurally distinct from a module
coordinate. Here `bom-platform.properties` pins what a Maven BOM cannot
express, the bare module coordinate:

    org.slf4j = 2.0.16

The location is fixed per project (`-Djenesis.project.boms=<paths>` or the
`boms(...)` builder method to relocate; multiple locations are searched in
order, first hit wins) and deliberately not profile-resolved, so every build
sees the same BOMs.

Build it
--------

From this directory:

    java build/jenesis/Project.java

A Maven BOM carries versions, never hashes - POM bytes are not stable across
repositories, so its reference takes no checksum and checksum comments inside
the fetched BOM are not read. Strict pinning therefore needs the `pin` goal
first, which materializes every version the BOM declares as a `@jenesis.pin`
line (the resolved closure with artifact hashes, the rest version-only), so
the pinned build no longer depends on the BOM's unverifiable content:

    java build/jenesis/Project.java pin
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
    @jenesis.bom bom-platform.properties                a local file (this demo)
    @jenesis.bom kotlinc/bom-platform.properties        the same file, entries in the kotlinc group

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
consumes `bom-platform.properties`. It is published only into the Jenesis
repository; the Maven `export` never emits it. Emission is off unless
`bom.properties` is present, mirroring how `packaging.properties` gates
`jpackage`; the file is otherwise empty and reserved for future options.

Precedence
----------

Local pins always win: an explicit `@jenesis.pin` overrides any BOM entry.
Between BOMs, the first declared wins a conflicting coordinate. Under
`-Djenesis.dependency.pin=strict` a module-repository BOM reference needs a
checksum (its byte-stable properties file is content-verified before the
entries are trusted, and its hashed entries satisfy the strict closure
check); a Maven BOM reference is exempt, since it cannot carry one, and its
safety comes from the materialized pins instead.

Repinning
---------

The `pin` goal is BOM-aware. By default (`-Djenesis.pin.bom=keep`) it writes
no `@jenesis.pin` for a dependency a module BOM already supplies and removes
a pin line that became redundant when such a BOM took over its coordinate;
versioned module-repository references get their content hash written onto
the declaration, so the BOM itself is pinned. A Maven BOM inverts this:
running `java build/jenesis/Project.java pin` on this demo writes a pin line
for every version `slf4j-bom` declares - the resolved `slf4j-api` with its
artifact hash, the other `org.slf4j` artifacts version-only - while the
declaration itself stays hash-free. Under `-Djenesis.dependency.pin=ignore`
a versioned BOM reference floats to the latest published BOM and its entries
keep managing resolution while the pins float, so a repin upgrades the whole
curation in one step and rewrites the pins it entails. The inverse migration
is

    java -Djenesis.pin.bom=flatten build/jenesis/Project.java pin

which removes the `@jenesis.bom` declarations and pins the resolved closure
in full, turning this demo's module-info into its `@jenesis.pin` equivalent.
Orthogonally, `-Djenesis.pin.checksum=false` writes versions without hashes,
for pins and BOM references alike.
