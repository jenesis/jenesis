Classifiers demo
================

Publish variants of a module's jar under classifiers, the way Maven's
`maven-jar-plugin` `<classifier>` executions do, and run another module of the
same build on one of them. A variant is the module's jar plus a few files of its
own - a native library for one platform, say - so it carries the same module
name and takes the jar's place wherever a consumer selects it.

The project is a modular two-module build: `native` is the library, `app` the
application that requires it. Which variant a machine builds is decided by a
profile, here the neutral `platform-a` so that the demo runs on every machine.

Run it
------

From this directory:

    java -Djenesis.make.profiles=platform-a build/jenesis/Execute.java

which builds both modules and prints what the library found in its jar:

    Running on the variant for platform-a, with a library a plugin built for platform-a

`stage` lays out the variants beside the jar, in both trees:

    java -Djenesis.make.profiles=platform-a build/jenesis/Make.java stage

    target/stage/modular/output/demo.natives/demo.natives.jar
    target/stage/modular/output/demo.natives/demo.natives-platform-a.jar
    target/stage/modular/output/demo.natives/demo.natives-legacy.jar
    target/stage/maven/output/demo/natives/demo.natives/0-SNAPSHOT/demo.natives-0-SNAPSHOT.jar
    target/stage/maven/output/demo/natives/demo.natives/0-SNAPSHOT/demo.natives-0-SNAPSHOT-platform-a.jar
    target/stage/maven/output/demo/natives/demo.natives/0-SNAPSHOT/demo.natives-0-SNAPSHOT-legacy.jar

and the POM generated for `app` depends on `demo.natives` with the classifier
`platform-a`. Without the profile the library declares no variant, and the build
names the profile that does:

    java build/jenesis/Make.java

    app asks for the classifier platform-a of module/demo.natives, which the module of this build that builds it declares only in the profile platform-a - activate it with -Djenesis.make.profiles=platform-a

Layout
------

    demo/demo-65-classifiers
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- jenesis.plugins.properties   library+binary/compiled=./library
    |-- library/             the plugin that builds the variant's library
    |   |-- .jenesis.skip
    |   |-- module-info.java
    |   |-- demo/library/LibraryFactory.java
    |   `-- demo/library/LibraryModule.java  writes library/<platform>/library.txt
    |-- native/              the library, module demo.natives
    |   |-- module-info.java
    |   |-- META-INF/build.jenesis/platform-a/
    |   |   |-- classifiers.properties   the variants the profile platform-a builds
    |   |   `-- plugin-library.properties   platform=platform-a
    |   `-- sample/natives/Natives.java  reads variant.txt and library.txt from its own jar
    |-- variants/platform-a/             what the variant platform-a adds
    |   |-- variant.txt
    |   `-- META-INF/MANIFEST.MF         Variant: platform-a
    `-- app/                 the application, module demo.app
        |-- module-info.java     requires demo.natives, pinned to :platform-a
        `-- sample/app/App.java

Declaring variants
------------------

A module declares its variants in a `classifiers.properties`, found like any
other configuration file: in the module's own configuration folder, then in
`build.jenesis/` at the project root, each with the folders of the active
profiles first, and the first file found is the one read. One line per
classifier names the folders whose files the variant adds:

    platform-a=../variants/platform-a,library/platform-a
    legacy=

The folders are named from the module's folder and must lie inside the project.
A folder the project does not hold is the folder a plugin of the module wrote,
named as the plugin writes it in its output, as the plugin below does.
Each file of a folder lands at the root of the variant under its path inside the
folder, so `variants/platform-a/variant.txt` becomes `variant.txt`. A
`META-INF/MANIFEST.MF` among them is not copied but merged over the module's
manifest, its value winning for each attribute it names:

    unzip -p target/stage/modular/output/demo.natives/demo.natives-platform-a.jar META-INF/MANIFEST.MF

    Variant: platform-a

A variant only adds: a file the module's jar holds already fails the build. A line
naming no folder, as `legacy=` does, publishes the module's jar as it is under
that classifier, for consumers that still ask for a classifier which no longer
differs. `tests`, `sources` and `javadoc` are the build's own and cannot be
declared. An empty `classifiers.properties`, for example in a profile's folder,
declares no variant at all. Like every archive of the build, a variant is
reproducible.

Because the file sits in the folder of the profile `platform-a`, a machine builds
the variant only with that profile active. A real project keeps one folder per
platform, such as `linux-x86_64/` and `macos-aarch64/`, and activates the one the
machine builds; a release that gathers all of them builds each on its own machine.

A variant a plugin builds
-------------------------

A variant's files are often built rather than kept: a native library compiled
for one platform by `cargo`, `cmake` or `make`. A plugin of the module builds
them, and the variant names the folder the plugin writes. Here a local plugin,
`library/`, stands in for such a compiler: `jenesis.plugins.properties` adds it
to the module build beside `javac`,

    library+binary/compiled=./library

and it runs where a `plugin-library.properties` is found, here in the folder of
the profile `platform-a`, naming the platform it builds for:

    platform=platform-a

The plugin writes `library/platform-a/library.txt` in its output, and the line
`platform-a=../variants/platform-a,library/platform-a` adds it to the variant
beside the files of the project's own folder, while the module's jar does not
hold it. A plugin that compiles native code writes its library the same way, as
`library/linux-x86_64/libnatives.so`, and the variant adds it at
`libnatives.so`. When a folder name is both a folder of the project and one a
plugin wrote, the variant holds the files of both and refuses a file the two
share; a name that neither the project nor a plugin provides fails the build
when the variant is made.

Selecting a variant
-------------------

`app` selects the variant with the classifier qualifier of a pin, as
`platform-guard` selects one of a published module:

    /**
     * @jenesis.pin demo.natives :platform-a
     */
    module demo.app {
        requires demo.natives;
    }

A module of the same build is resolved as it is built, so its pin names the
classifier alone and no version. The variant replaces the module's jar on every
path `app` is built and run with - compiling, testing, `Execute`, a bundle, a
launcher jar, `jlink` and `jpackage` - and never sits beside it, because both
carry the same module name. `native` itself is compiled and tested against its
own jar. The pin works in the pure modular layout as well:

    java -Djenesis.project.layout=modular -Djenesis.make.profiles=platform-a build/jenesis/Execute.java

A `pom.xml` selects a variant of a module of the same build the way Maven does,
with a `<classifier>` on the dependency; the variant then replaces the module's
own jar where the closure holds both.

Staged variants are exported and released with the rest of the Maven tree. A
release to a Jenesis module repository uploads each module's jar alone, so the
variants are not part of it.
