Transform and inspect demo
==========================

Run plugins over everything the build produced, after every module is built and
before anything is staged: a `transform` plugin adds files to the modules, and an
`inspect` plugin checks the result and fails the build when it is wrong. Both run as
part of `build`, in `build/postprocess`, so everything after it - `stage`, `export`,
`release` and `java build/jenesis/Execute.java` - sees what they added and never
runs past a failed inspection.

Run it
------

From this directory:

    java build/jenesis/Make.java stage

The `notice` plugin writes a notice for each module, listing what the module
includes, and the `audit` plugin checks that every module carries one. The notice is
staged beside the module's jar:

    target/stage/modular/output/demo.app/demo.app-notice.txt
    target/stage/maven/output/demo/app/demo.app/0-SNAPSHOT/demo.app-0-SNAPSHOT-notice.txt

holding

    demo.app - Copyright Example Corp.
    Licensed under the Apache License, Version 2.0.
      includes maven/org.json/json/20260814
      includes module/org.json/20260814

Switch the transform off and the inspection refuses the build:

    java -Djenesis.plugin.notice=false build/jenesis/Make.java stage

    No notice is attached to demo.app - add notice+postprocess/transform to jenesis.plugins.properties, or switch it back on

Layout
------

    demo/demo-56-transform-inspect
    |-- build/jenesis                          symlink to ../../../sources/build/jenesis
    |-- legal/HEADER.txt                       the licence line the notice carries
    |-- jenesis.plugins.properties             notice+postprocess/transform=./notice
    |                                          audit+postprocess/inspect=./audit
    |-- jenesis.plugins.arguments.properties   notice.holder=Example Corp.
    |                                          notice.@legal=legal
    |-- jenesis.plugins.pin.properties         written by the pin goal
    |-- notice/                                the transform, compiled from source
    |-- audit/                                 the inspection, compiled from source
    `-- sources/
        |-- module-info.java                   module demo.app { requires org.json; }
        `-- sample/Sample.java

Naming the plugins
------------------

The plugins are named in `jenesis.plugins.properties`, as the `internal-module`
demo names its generator, but under one of two slots of the project's build:

    notice+postprocess/transform=./notice
    audit+postprocess/inspect=./audit

A plugin of `postprocess` runs once for the whole build rather than once per module, so
no `plugin-<name>.properties` switches it on in a module: the line itself does.
Transforms run in the order the file names them, each seeing what the ones before
it added, and the inspections run after all of them.

Configuring the plugins
-----------------------

A plugin of `postprocess` reads its values from
`jenesis.plugins.arguments.properties` beside `jenesis.plugins.properties`, one line
per value as `<plugin>.<key>`. This demo's holds

    notice.holder=Example Corp.
    notice.@legal=legal

and `holder=Example Corp.` reaches the plugin's `SequencedMap` constructor. A profile
brings values of its own in `jenesis.plugins.arguments-<profile>.properties`, which
win over the file without a profile:

    # jenesis.plugins.arguments-release.properties, active with -Djenesis.make.profiles=release
    notice.holder=Example Corp., all rights reserved

The values come from these files alone, never from the command line.
`-Djenesis.plugin.<name>=false` leaves a plugin out, and a profile can switch one
back on. As the plugins run with every build, one that takes long is best switched
off in `jenesis.properties` and on in the profile that ships:

    # jenesis.properties
    jenesis.plugin.audit=false

    # jenesis-release.properties, active with -Djenesis.make.profiles=release
    jenesis.plugin.audit=true

`-Djenesis.project.plugins=false` leaves out every plugin at once, those of each
module's build included. The plugins run with everything that builds, `pin` among
it, so an inspection that fails stops `pin` as well; this pins without running any:

    java -Djenesis.project.plugins=false build/jenesis/Make.java pin

Binding project files
---------------------

A plugin reads only what the build hands it, so a file of the project reaches it as
an input. A key starting with `@` names one instead of a value:

    notice.@legal=legal

binds the project's `legal/` folder into an input named `legal`, which the plugin
reads as the folder `../inputs/legal`: this demo's notice takes its licence line
from `legal/HEADER.txt`. Editing the file runs the transform again.

A target after the input's name places what is bound inside the input rather than
at its root, and one input takes a key per target, so it can gather several files
and folders of the project:

    notice.@legal=legal
    notice.@legal/third-party/NOTICE.txt=vendor/NOTICE.txt

A single file keeps its name unless a target renames it, and two bindings that
place their files at one target fail the build. A value whose key starts with `@` is
written with two: `@@name=value` hands the plugin `@name=value`.

A path is resolved against the project root here, and against the module's own
folder in a module's `plugin-<name>.properties`. Either way it has to stay within
the project, symbolic links included:

    # jenesis.plugins.arguments-outside.properties
    notice.@legal=../..

    java -Djenesis.make.profiles=outside build/jenesis/Make.java stage

    The input @legal of the plugin notice names ..., which lies outside the project ... - name a folder the project holds

What a plugin sees
------------------

Each plugin is handed the inventory of every module: its jar, sources and
documentation, its POM, and every dependency it resolved, each with the jar it
resolved to. A transform adds to a module by writing files into its own output
and naming them in an `inventory.properties` there, under the module's prefix:

    module-sources.attachment.notice=notices/module-sources/NOTICE.txt

`<module>.attachment.<classifier>` is staged beside the module's jar under that
classifier, and `<module>.report.<name>` is staged with the module's reports. A
transform adds and never replaces: an inventory naming anything else, or a module
the build does not have, fails the build, and so does an attachment whose file the
build stages already. A transform that brings its own SBOM, say, attaches it as
`cyclonedx` once `-Djenesis.sbom.cyclonedx=false` has switched off the stock one.

An inspection reads the same inventories plus what the transforms added, and
fails the build by throwing. It writes only into its own output: one that
changes a file it was handed fails the build as well.

Pinning the plugins
-------------------

The plugins of `postprocess` belong to no module, so their pins live beside the file
that names them, in `jenesis.plugins.pin.properties`, one line for each module in
each plugin's closure:

    plugin-audit/module/build.jenesis=0.14.0 SHA-256/...
    plugin-notice/module/build.jenesis=0.14.0 SHA-256/...

`java build/jenesis/Make.java pin` writes the file, pinning every plugin the file
names, including one a setting switches off, so a plugin that only a profile
switches on is pinned all the same.
