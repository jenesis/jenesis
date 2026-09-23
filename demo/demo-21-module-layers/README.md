Module layers demo
==================

A library keeps a dependency private. It needs `jackson-core` 2.15.4; the application that uses
it needs 2.18.2; and the library's own provider keeps a third, 2.13.5, in a layer of its own. All
three end up in one JVM, under the same package names, with nothing relocated - and the application
declares nothing, because it does not have to know.

This is what shading is used for, done with the module system instead of a bytecode rewriter.

Run it
------

From this directory:

    java build/Demo.java

which builds the project, unpacks the produced `bundle.zip`, launches the app out of it, and
prints:

    the application's jackson-core 2.18.2, loaded by jdk.internal.loader.ClassLoaders$AppClassLoader@...
    the library's private jackson-core 2.15.4, loaded by jdk.internal.loader.Loader@...
        and one layer deeper: jackson-core 2.13.5

Three loaders, three versions, one package name. Here the layer's jars are files beside the unpacked
bundle, so the JDK's own layer loader reads them; inside an executable jar, where there is no file to
name, the launcher reads them from the jar it is already holding open instead.

The four modules
----------------

    spi/            demo.layers.spi          the API module - shared with every layer here
    library/        demo.layers.library      declares the layer, bootstraps it, consumes through the SPI
    library-test/   demo.layers.library.test its tests, which exercise the layer
    impl/           demo.layers.impl         isolated with jackson-core 2.15.4 - and declares a layer itself
    nested/         demo.layers.nested       isolated one level deeper, with jackson-core 2.13.5
    app/            demo.layers.app          an ordinary consumer, with jackson-core 2.18.2

Only `library/module-info.java` says anything about layers:

    @jenesis.layer render api      demo.layers.spi
    @jenesis.layer render provider module/demo.layers.impl

The first line names the API module. The second names what the layer isolates - resolved in a
dependency group of its own, `layer:render`, which resolves, verifies and reports like any other
group. A group is the top isolation axis of a coordinate, so a layer needs no grammar of its own:
`layer:render/maven/<groupId>/<artifactId>` addresses its contents wherever a coordinate is
written.

The library then asks for its layer by name:

    Report r = Launcher.instance("render", Report.class);

`app/module-info.java` has none of this. It requires the library and a different `jackson-core`,
and that is all.

Nesting
-------

`impl/module-info.java` declares a layer of its own, and reaches it the same way the library reaches
`render`. Nesting needs no mechanism: `Launcher.layer` parents a layer on its **caller's**, so
`inner` is a child of `render` rather than of the application, and the API module they share
resolves from `render`.

Discovery is a fixpoint on the build side too. `Dependencies` resolves a round, reads
`Jenesis-Layer` off what that round produced, and queues whatever is new - so a layer declared by a
module that is itself inside a layer is found, and a cycle is refused rather than nested without
end.

Tests use layers as well. `library-test/` is an ordinary `@jenesis.test` module, and the test JVM is
handed `jlayer.modulepath.render` exactly as a deployment would be, so the library
bootstraps its layer in a test run the same way it does in production.

How the application learns about it
-----------------------------------

It does not - its *build* does. The library's jar carries the declaration in its manifest:

    Jenesis-Layer: render=demo.layers.spi module/demo.layers.impl

Any build that resolves that jar reads the header, resolves those coordinates into `layer:render`,
and materialises them into a folder, exactly as it would for `Jenesis-Aliases` or
`Jenesis-Overrides`. The result is visible in the bundle:

    jars/classes.jar                              every jar, stored once
    jars/demo.layers.library-0-SNAPSHOT.jar
    jars/demo.layers.spi-0-SNAPSHOT.jar
    jars/com.fasterxml.jackson.core-2.18.2.jar    the application's
    jars/com.fasterxml.jackson.core-2.15.4.jar    the library's
    jars/com.fasterxml.jackson.core-2.13.5.jar    one layer deeper
    jars/demo.layers.impl-0-SNAPSHOT.jar
    jars/demo.layers.nested-0-SNAPSHOT.jar

    application.unix.args   (and application.windows.args, the same with ';')
      "-Djlayer.modulepath.inner=jars/demo.layers.nested-...:jars/...2.13.5.jar"
      "-Djlayer.modulepath.render=jars/demo.layers.impl-...:jars/...2.15.4.jar"
      "--module-path"
      "jars/build.jenesis.launcher-...:jars/classes.jar:jars/...2.18.2.jar:jars/demo.layers.library-...:jars/demo.layers.spi-..."
      "--module"
      "demo.layers.app/demo.layers.app.Main"

Nothing is placed anywhere special, and one store is enough because no path is a folder. A dependency
is materialised once under a name that carries its version, so three versions of one library stand side
by side, and a jar a layer and the application both need is one file named in two lists - stored once,
loaded twice. Every path is *named*, because a folder holds whatever happens to be in it.

`demo.layers.impl` is not named in `modulepath`, so the application never reads it.
`demo.layers.spi` is named there and in no layer - which is what makes the `Report` instance that
crosses the boundary a single class rather than two of the same name.

`build.jenesis.launcher` is shared with every layer too, for the same reason the API module is: it
is the mechanism a layer is reached through, not a dependency to isolate, and a second copy would
mean a second `Launcher` class with a cache of its own.

Why the API module must be shared
---------------------------------

A layer's `Configuration` is built from its host's, so any module the layer does not itself hold
resolves from the host. Everything the API module reaches is therefore left out of the layer, and
both sides work with the very same classes - which is what lets the call across the boundary be an
ordinary interface call rather than a proxy.

The consequence is worth stating plainly: **a dependency whose types your API module reaches is
exposed by it, and cannot be isolated behind it.** That is true of shading too; the difference is
that here the build says so instead of leaving it to a `LinkageError` later.

Legacy trees and the layer's class path
---------------------------------------

A layer splits the two paths exactly as the application does, because the libraries worth isolating are
usually the ones that were never modularized. What carries a module identity - a `module-info`, an
`Automatic-Module-Name`, or a name you give it in `modules.properties` - is resolved into the layer. The
rest is the layer's own class path, and the descriptor names both:

    layer.modulepath.demo.layers.library.render=demo.layers.impl-...,...2.15.4.jar
    layer.classpath.demo.layers.library.render=commons-logging-1.2.jar,...

So a legacy library is reached by naming *it*, not its whole tree: alias the one jar your code calls, and
its long tail is read through the class path, as it would be on a plain `java -cp`.

One rule of the module system decides how this can be used: **only an automatic module reads the unnamed
module.** A jar promoted to an automatic module by an alias can therefore use the tail; a module with a
real `module-info` cannot, and javac will not let it try. That is the usual shape anyway - what you isolate
is a non-modular library, and what gives it a name is the alias.

A layer that holds no module at all is refused, because a layer is reached through the modules it holds.

What the build refuses
----------------------

- a layer declared without `requires build.jenesis.launcher` - the mechanics live there;
- a layer that names no API module, or whose API module the declaring module does not require;
- a `requires` on a module the same module isolates - it is off that module's path, and javac
  would say so anyway;
- a layer that isolates nothing, because the API module already shares all of it;
- a layer that provides a contract it also holds - the host would look that service up against a
  different class of the same name and find no provider, silently;
- a layer that holds no module at all, since a layer is reached through the modules it holds - name one
  with `modules.properties` and the rest is read through the layer's class path.

Where the layer comes from at run time
--------------------------------------

`bundle=true` writes each layer into the argument file that *is* the launch, as a
`-Djlayer.modulepath.<name>` naming its jars beside the module path that omits them.
The declaring module is part of the key because a layer may itself hold a module that declares one -
nesting is unbounded - and the runtime builds the same key from the module that calls it. Docker
copies the same file into its image, and a run through `Execute` passes the same option.

An executable jar (`launcher=true`) needs no such option: the layer travels inside the jar and the
launcher reads it from there, never unpacking anything.

A layer that is not bundled in an executable jar is defined from the `jlayer.*` system properties
when a module first asks for it. The JVM lets any code overwrite a system property at any time and
offers no way to protect one, so code that runs earlier - in the application or in an outer layer -
can change which jars that layer holds, and so place its own code in another module's layer, outside
the encapsulation that layer was declared for. A layer bundled in an executable jar is read from the
jar and is not affected.

Nesting needs no further mechanism: `Launcher.layer` parents a layer on its *caller's*, so a module
inside one layer that asks for another gets a child of the first, and the API module they share
resolves from there rather than from the application.

A layer is defined while the JVM runs, so a packaging that resolves its module graph ahead of time
refuses a project that declares one, naming the conflict rather than flattening it silently.
