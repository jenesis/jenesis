Verify demo
===========

Run plugins over everything the build produced, after every module is built and
before anything is staged: a `transform` plugin adds files to the modules, and an
`inspect` plugin checks the result and fails the build when it is wrong. Both run in
the `verify` goal, which `stage`, `export`, `release` and `Execute` run first.

Run it
------

From this directory:

    java build/jenesis/Make.java stage

The `notice` plugin writes a notice for each module, listing what the module
includes, and the `audit` plugin checks that every module carries one. The notice is
staged beside the module's jar:

    target/stage/modular/output/demo.verify/demo.verify-notice.txt
    target/stage/maven/output/demo/verify/demo.verify/0-SNAPSHOT/demo.verify-0-SNAPSHOT-notice.txt

holding

    demo.verify - Copyright Example Corp.
    Licensed under the Apache License, Version 2.0.
      includes maven/org.json/json/20260814
      includes module/org.json/20260814

Switch the transform off and the inspection refuses the build:

    java -Djenesis.plugin.notice=false build/jenesis/Make.java stage

    No notice is attached to demo.verify - add notice+transform to jenesis-plugins.properties, or switch it back on

`java build/jenesis/Execute.java` runs `verify` as well, so the program only
starts once its inspections pass.

Layout
------

    demo/demo-67-verify
    |-- build/jenesis                      symlink to ../../../sources/build/jenesis
    |-- jenesis.properties                 jenesis.plugin.notice.holder=Example Corp.
    |                                      jenesis.plugin.notice.@legal=legal
    |-- legal/HEADER.txt                   the licence line the notice carries
    |-- jenesis-plugins.properties         notice+transform=./notice
    |                                      audit+inspect=./audit
    |-- jenesis-plugins-pin.properties     written by the pin goal
    |-- notice/                            the transform, compiled from source
    |-- audit/                             the inspection, compiled from source
    `-- sources/
        |-- module-info.java               module demo.verify { requires org.json; }
        `-- sample/Sample.java

Naming the plugins
------------------

The plugins are named in `jenesis-plugins.properties`, as the `internal-module`
demo names its generator, but under one of the two slots of `verify`:

    notice+transform=./notice
    audit+inspect=./audit

A plugin of `verify` runs once for the whole build rather than once per module, so
no `plugin-<name>.properties` switches it on in a module: the line itself does.
Transforms run in the order the file names them, each seeing what the ones before
it added, and the inspections run after all of them.

Configuring the plugins
-----------------------

A plugin of `verify` reads its values from settings named
`jenesis.plugin.<name>.<key>`, so they come from `jenesis.properties`, a profile, your
own `~/.jenesis/jenesis.properties` or the command line, like any other setting. This
demo's `jenesis.properties` holds

    jenesis.plugin.notice.holder=Example Corp.

which reaches the plugin's `SequencedMap` constructor as `holder=Example Corp.`.
`-Djenesis.plugin.<name>=false` leaves a plugin out, and a profile can switch one
back on.

Binding project files
---------------------

A plugin reads only what the build hands it, so a file of the project reaches it as
an input. A key starting with `@` names one instead of a value:

    jenesis.plugin.notice.@legal=legal

binds the project's `legal/` folder into an input named `legal`, which the plugin
reads as the folder `../inputs/legal`: this demo's notice takes its licence line
from `legal/HEADER.txt`. Editing the file runs the transform again. `@<input>=<path>:<target>`
places what is bound at `<target>` inside the input rather than at its root, and a
single file keeps its name unless a target renames it. A value whose key starts with
`@` is written with two: `@@name=value` hands the plugin `@name=value`.

A path is resolved against the project root here, and against the module's own
folder in a module's `plugin-<name>.properties`. Either way it has to stay within
the project, symbolic links included:

    java "-Djenesis.plugin.notice.@legal=../.." build/jenesis/Make.java stage

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
the build does not have, fails the build.

An inspection reads the same inventories plus what the transforms added, and
fails the build by throwing. It writes only into its own output: one that
changes a file it was handed fails the build as well.

Pinning the plugins
-------------------

The plugins of `verify` belong to no module, so their pins live beside the file
that names them, in `jenesis-plugins-pin.properties`, one line for each module in
each plugin's closure:

    plugin-audit/module/build.jenesis=0.14.0 SHA-256/...
    plugin-notice/module/build.jenesis=0.14.0 SHA-256/...

`java build/jenesis/Make.java pin` writes the file, pinning every plugin the file
names, including one a setting switches off, so a plugin that only a profile
switches on is pinned all the same.
