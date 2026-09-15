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

Three loaders, three versions, one package name.

The four modules
----------------

    spi/            demo.layers.spi          the API module - shared with every layer here
    library/        demo.layers.library      declares the layer, bootstraps it, consumes through the SPI
    library-test/   demo.layers.library.test its tests, which exercise the layer
    impl/           demo.layers.impl         isolated with jackson-core 2.15.4 - and declares a layer itself
    nested/         demo.layers.nested       isolated one level deeper, with jackson-core 2.13.5
    app/            demo.layers.app          an ordinary consumer, with jackson-core 2.18.2

Only `library/module-info.java` says anything about layers:

    @jenesis.layer render api demo.layers.spi
    @jenesis.layer render module/demo.layers.impl

The first line names the API module. The second names what the layer isolates - resolved in a
dependency group of its own, `layer:render`, which pins, verifies and reports like any other
group. (`plugin:scala` already reads that way, so pins need no new grammar:
`@jenesis.pin layer:render/maven/g/a 1.2.3`.)

The library then asks for its layer by name:

    Renderer r = Launcher.load("render", Report.class).findFirst().orElseThrow();

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
handed `jenesis.layer.demo.layers.library.render` exactly as a deployment would be, so the library
bootstraps its layer in a test run the same way it does in production.

How the application learns about it
-----------------------------------

It does not - its *build* does. The library's jar carries the declaration in its manifest:

    Jenesis-Layer: render=demo.layers.spi module/demo.layers.impl

Any build that resolves that jar reads the header, resolves those coordinates into `layer:render`,
and materialises them into a folder, exactly as it would for `Jenesis-Aliases` or
`Jenesis-Overrides`. The result is visible in the bundle:

    jars/classes.jar                              every jar, stored once
    jars/demo.layers.library-1-SNAPSHOT.jar
    jars/demo.layers.spi-1-SNAPSHOT.jar
    jars/com.fasterxml.jackson.core-2.18.2.jar    the application's
    jars/com.fasterxml.jackson.core-2.15.4.jar    the library's
    jars/com.fasterxml.jackson.core-2.13.5.jar    one layer deeper
    jars/demo.layers.impl-1-SNAPSHOT.jar
    jars/demo.layers.nested-1-SNAPSHOT.jar

    application.properties
      modulepath=classes.jar,demo.layers.library-...,demo.layers.spi-...,...2.18.2.jar
      layer.demo.layers.library.render=demo.layers.impl-...,...2.15.4.jar
      layer.demo.layers.impl.inner=demo.layers.nested-...,...2.13.5.jar

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

What the build refuses
----------------------

- a layer declared without `requires build.jenesis.launcher` - the mechanics live there;
- a layer that names no API module, or whose API module the declaring module does not require;
- a `requires` on a module the same module isolates - it is off that module's path, and javac
  would say so anyway;
- a layer that isolates nothing, because the API module already shares all of it;
- a layer that provides a contract it also holds - the host would look that service up against a
  different class of the same name and find no provider, silently;
- a jar in a layer that carries no module, since a named module cannot read the unnamed module.

Where the layer comes from at run time
--------------------------------------

`bundle=true` records each layer's membership in `application.properties`, so the launch command
names the jars: `-Djenesis.layer.demo.layers.library.render=<unpacked>/jars/demo.layers.impl-….jar:…`.
The declaring module is part of the key because a layer may itself hold a module that declares one -
nesting is unbounded - and the runtime builds the same key from the module that calls it. Docker bakes the same option into its
`ENTRYPOINT`, and a run through `Execute` passes it too.

An executable jar (`launcher=true`) needs no such option: the layer travels inside the jar and the
launcher reads it from there, never unpacking anything.

Nesting needs no further mechanism: `Launcher.layer` parents a layer on its *caller's*, so a module
inside one layer that asks for another gets a child of the first, and the API module they share
resolves from there rather than from the application.

A layer is defined while the JVM runs, so `native=true` rejects a project that declares one.
