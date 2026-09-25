Project plugins demo
====================

Hook plugins into the build of the whole project rather than into one module's:

- a `preprocess` plugin checks the project before any module is built;
- a `postprocess/transform` plugin adds files to every module built, and a
  `postprocess/inspect` plugin checks the result, before anything is staged;
- a `package` plugin adds a package of its own beside the ones Jenesis builds;
- an `export` or a `release` plugin delivers what was staged;
- a plugin under `plugin` runs only when it is asked for.

`preprocess` and `postprocess` run as part of `build`, so everything after it -
`stage`, `export`, `release` and `java build/jenesis/Execute.java` - sees what the
transforms added and never runs past a failed check.

Run it
------

From this directory:

    java build/jenesis/Make.java stage

The `licence` plugin first checks that `legal/HEADER.txt` names the project's
licence. Once the module is built, the `notice` plugin writes a notice for it,
listing what the module includes, and the `audit` plugin checks that every module
carries one. The notice is staged beside the module's jar:

    target/stage/modular/output/demo.app/demo.app-notice.txt
    target/stage/maven/output/demo/app/demo.app/0-SNAPSHOT/demo.app-0-SNAPSHOT-notice.txt

holding

    demo.app - Copyright Example Corp.
    Licensed under the Apache License, Version 2.0.
      includes maven/org.json/json/20260814
      includes module/org.json/20260814

The notices of all modules are also staged once for the whole project:

    target/stage/project/output/NOTICE.txt

The `zip` plugin packs the module with its runtime dependencies, staged with the
other packages:

    target/stage/packages/output/demo-app.zip
    |-- lib/demo-app.jar
    `-- lib/org.json-20260814.jar

Switch the transform off and the inspection refuses the build:

    java -Djenesis.plugin.notice=false build/jenesis/Make.java stage

    No notice is attached to demo.app - add notice+postprocess/transform to jenesis.plugins.properties, or switch it back on

Layout
------

    demo/demo-56-project-plugins
    |-- build/jenesis                          symlink to ../../../sources/build/jenesis
    |-- build.jenesis/plugin-zip.properties    name=demo-app
    |-- legal/HEADER.txt                       the licence line the notice carries
    |-- jenesis.plugins.properties             one line per plugin, below
    |-- jenesis.plugins.arguments.properties   the values of the plugins of the project
    |-- jenesis.plugins.pin.properties         written by the pin goal
    |-- licence/                               the preprocessor, compiled from source
    |-- notice/                                the transform
    |-- audit/                                 the inspection
    |-- zip/                                   the packager
    |-- publish/                               the exporter
    |-- checksums/                             a plugin run on demand
    `-- sources/
        |-- module-info.java                   module demo.app { requires org.json; }
        `-- sample/Sample.java

Naming the plugins
------------------

The plugins are named in `jenesis.plugins.properties`, as the `internal-module`
demo names its generator:

    licence+preprocess=./licence
    notice+postprocess/transform=./notice
    audit+postprocess/inspect=./audit
    zip+package=./zip
    publish+export=./publish
    checksums+plugin=./checksums

Six slots run a plugin once for the whole project rather than once per module:

| Slot | Runs as | Runs | Is handed |
| --- | --- | --- | --- |
| `preprocess` | `build/preprocess/custom/<name>` | before any module is built | only what it binds |
| `postprocess/transform` | `build/postprocess/transform/<name>` | after every module is built | every module's inventory |
| `postprocess/inspect` | `build/postprocess/inspect/<name>` | after the transforms | the same, plus what they added |
| `export` | `export/custom/<name>` | with `export`, beside its stock steps | everything staged |
| `release` | `release/custom/<name>` | with `release`, beside its stock steps | everything staged |
| `plugin` | `plugin/<name>` | only when `plugin/<name>` is named | what `build` and `stage` produced |

No `plugin-<name>.properties` switches such a plugin on in a module: the line itself
does. Transforms run in the order the file names them, each seeing what the ones
before it added, and the inspections run after all of them. `package` is a slot of
each module instead, like `binary/generated`, and is described below.

Configuring the plugins
-----------------------

A plugin of the whole project reads its values from
`jenesis.plugins.arguments.properties` beside `jenesis.plugins.properties`, one line
per value as `<plugin>.<key>`. This demo's holds

    licence.name=Apache License, Version 2.0
    licence.@legal=legal
    notice.holder=Example Corp.
    notice.@legal=legal
    publish.directory=target/published
    checksums.file=target/SHA256SUMS

and `holder=Example Corp.` reaches the `notice` plugin's `SequencedMap` constructor. A profile
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

Checking the project first
--------------------------

A preprocessor runs before any module is built and hands the build nothing, so all
it can do is stop it early. It sees only what it binds - here the `legal/` folder -
and the `licence` plugin refuses a header that does not name the licence it is given:

    # jenesis.plugins.arguments-mit.properties
    licence.name=MIT License

    java -Djenesis.make.profiles=mit build/jenesis/Make.java

    legal/HEADER.txt does not name the MIT License - the project is released under it, so its header must say so

What a transform and an inspection see
--------------------------------------

Each of them is handed the inventory of every module: its jar, sources and
documentation, its POM, and every dependency it resolved, each with the jar it
resolved to. A transform adds to a module by writing files into its own output
and naming them in an `inventory.properties` there, under the module's prefix:

    module-sources.attachment.notice=notices/module-sources/NOTICE.txt

The key after the prefix is any the module's inventory knows, and the stages read
it as they read the module's own: `<module>.attachment.<classifier>` is staged
beside the module's jar under that classifier, and `<module>.report.<name>` with
the module's reports. What a transform adds is its author's responsibility; a
module the build does not have fails the build, and so does a key two transforms
add, or an attachment whose file the build stages already. A transform that brings
its own SBOM, say, attaches it as `cyclonedx` once `-Djenesis.sbom.cyclonedx=false`
has switched off the stock one.

What belongs to no module - an aggregated report, a site, a distribution of every
module - a transform writes into a `project/` folder of its own output, laid out as
it sees fit. `stage` copies it as it stands into `target/stage/project/output/`,
and a file two transforms place at the same path fails the build. The `notice`
plugin places all notices in one `NOTICE.txt` there.

An inspection reads the same inventories plus what the transforms added, and
fails the build by throwing. It writes only into its own output: one that
changes a file it was handed fails the build as well.

Adding a package
----------------

A plugin under `package` joins the packaging of each module where its
`plugin-<name>.properties` is found, as a plugin of any other module slot does - here
`build.jenesis/plugin-zip.properties`, which also names the zip:

    name=demo-app

It runs as `package/custom/<name>` and is handed the module's jar and its runtime
dependencies, together with whatever `packaging.properties` asks Jenesis to build -
a runtime image, a `jpackage` image, a launcher - so it can wrap or sign one of them
into a package of its own. What it writes into a `packages/` folder is staged with
the other packages in `stage/packages/`, and a package whose name another packager
writes already fails the build rather than replacing it.

Delivering what was staged
--------------------------

An exporter runs with `export`, and a releaser with `release`, beside the steps
Jenesis runs there itself. Both are handed everything that was staged. The `publish`
plugin copies the modular tree into the folder its arguments name:

    java build/jenesis/Make.java export/custom

    target/published/demo.app/demo.app.jar

Naming `export/custom` runs the exporters alone, without writing into the local
repositories. A step that writes outside the build, as an exporter does, runs every
time it is selected rather than only when what it reads has changed - `publish`
says so by overriding `shouldRun`.

Running a plugin on demand
--------------------------

A plugin under `plugin` is not part of any goal: no build waits for it, and it runs
only when its own selector is named. It is handed what `build` and `stage` produced,
so it suits a benchmark, a documentation site or a smoke test against a staged image.
The `checksums` plugin writes a SHA-256 for every file staged:

    java build/jenesis/Make.java plugin/checksums

    target/SHA256SUMS

Its name cannot collide with anything of Jenesis, as `plugin/` holds nothing else.

Pinning the plugins
-------------------

The plugins of the whole project belong to no module, so their pins live beside the
file that names them, in `jenesis.plugins.pin.properties`, one line for each module
in each plugin's closure:

    plugin-audit/module/build.jenesis=0.14.0 SHA-256/...
    plugin-checksums/module/build.jenesis=0.14.1 SHA-256/...
    plugin-licence/module/build.jenesis=0.14.1 SHA-256/...
    plugin-notice/module/build.jenesis=0.14.0 SHA-256/...
    plugin-publish/module/build.jenesis=0.14.1 SHA-256/...

A plugin of a module, the `zip` packager among them, is pinned in the module's own
declaration, as the `internal-module` demo's generator is:

    @jenesis.pin plugin-zip/module/build.jenesis 0.14.1 SHA-256/...

`java build/jenesis/Make.java pin` writes the file, pinning every plugin the file
names, including one a setting switches off, so a plugin that only a profile
switches on is pinned all the same.
