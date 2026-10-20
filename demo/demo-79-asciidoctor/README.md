Asciidoctor demo
================

Convert the AsciiDoc documents of a project to HTML and stage them beside its
modules. There is no build script: an `asciidoctor.properties` in the
`build.jenesis/` configuration folder switches Asciidoctor on and names the folder
of documents, and the build resolves the official plugin
`build.jenesis.plugin.asciidoctor` by itself, as it resolves the plugins of the
code-quality demo.

Build it
--------

From this directory:

    java build/jenesis/Make.java stage

The module is compiled as in any other demo, and then the documents of `docs/` are
converted once for the whole project:

    target/stage/project/output/docs/index.html
    target/stage/project/output/docs/guide/getting-started.html
    target/stage/project/output/docs/images/logo.svg

The first build downloads AsciidoctorJ, which runs Asciidoctor on JRuby, so it
takes a while; a later build converts again only when a document or the tool
changed.

Layout
------

    demo/demo-79-asciidoctor
    |-- build/jenesis                symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   `-- asciidoctor.properties   switches Asciidoctor on: its documents and values
    |-- docs/
    |   |-- index.adoc               the start page
    |   |-- _version.adoc            a partial, which index.adoc includes
    |   |-- guide/
    |   |   `-- getting-started.adoc
    |   `-- images/
    |       `-- logo.svg             copied as it stands
    `-- sources
        |-- module-info.java
        `-- demo/manual/Greeter.java

What is converted
-----------------

The properties name the folder of documents as `documents`, relative to the file
itself:

    documents=../docs

Every `.adoc` file there is converted to HTML and keeps its folder, so
`docs/guide/getting-started.adoc` becomes `docs/guide/getting-started.html`, and a
cross reference such as `xref:guide/getting-started.adoc[]` links the two. A file
or folder whose name starts with `_` is a partial: a document includes it, as
`index.adoc` includes `_version.adoc`, and it is never converted on its own. Every
other file - an image, a stylesheet - is copied as it stands.

The HTML names neither the time nor the Asciidoctor version it was written with,
so two builds of the same documents stage the same bytes. A document that
includes a file that does not exist fails the build.

Configuring it
--------------

The other lines of `asciidoctor.properties` are the plugin's values:

    attribute.product-name=Greeter
    attribute.icons=font

| Value | Meaning |
|---|---|
| `attribute.<name>` | an AsciiDoc attribute every document reads, as `{product-name}` reads the line above |
| `backend` | `html5` (default) or `docbook5` |
| `output` | the folder below `target/stage/project/output/` the documents go to, `docs` by default |

Any other key fails the build, naming the keys it takes. The documents alone
switch nothing on, so a project may keep `.adoc` files for another tool; the
properties switch Asciidoctor on, and properties that name no `documents` fail the
build naming the line to add. A project whose documents lie where the Maven and
Gradle plugins of Asciidoctor read them names that folder:

    documents=../src/docs/asciidoc

The values are read from this file alone, so a line for Asciidoctor in
`jenesis.plugins.arguments.properties` is refused.

`-Djenesis.documentation.asciidoctor=false` switches it off without removing anything:

    java -Djenesis.documentation.asciidoctor=false build/jenesis/Make.java stage

Where it runs
-------------

Asciidoctor runs at the hook point `postprocess/transform` once every module is
built, as `build/postprocess/transform/asciidoctor`, and writes what belongs to no
module into the `project/` folder that `stage` copies into
`target/stage/project/output/`. A plugin of `jenesis.plugins.properties` may not
take the name `asciidoctor`, which the build gives the plugin it resolves itself.

AsciidoctorJ resolves in the tool group of the plugin, `asciidoctor:tool`, and the
plugin's own module in the group `asciidoctor`. `java build/jenesis/Make.java pin`
writes both closures, JRuby included, into `jenesis.plugins.pin.properties`.
