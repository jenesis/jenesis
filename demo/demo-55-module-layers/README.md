Module layers demo
==================

A library keeps a dependency private. It needs `jackson-core` 2.15.4; the application that uses
it needs 2.18.2. Both end up in one JVM, under the same package names, with nothing relocated -
and the application declares nothing, because it does not have to know.

This is what shading is used for, done with the module system instead of a bytecode rewriter.

Run it
------

From this directory:

    java build/Demo.java

which builds the project, unpacks the produced `bundle.zip`, launches the app out of it, and
prints:

    the application's jackson-core 2.18.2, loaded by jdk.internal.loader.ClassLoaders$AppClassLoader@...
    the library's private jackson-core 2.15.4, loaded by jdk.internal.loader.Loader@...

Two loaders, two versions, one package name.

The four modules
----------------

    spi/        demo.layers.spi       the API module - the one module the library shares with its layer
    library/    demo.layers.library   declares the layer, bootstraps it, consumes through the SPI
    impl/       demo.layers.impl      the provider, isolated, with jackson-core 2.15.4
    app/        demo.layers.app       an ordinary consumer, with jackson-core 2.18.2

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

How the application learns about it
-----------------------------------

It does not - its *build* does. The library's jar carries the declaration in its manifest:

    Jenesis-Layer: render=demo.layers.spi module/demo.layers.impl

Any build that resolves that jar reads the header, resolves those coordinates into `layer:render`,
and materialises them into a folder, exactly as it would for `Jenesis-Aliases` or
`Jenesis-Overrides`. The result is visible in the bundle:

    modulepath/demo.layers.app...       the application
    modulepath/demo.layers.library...   the library
    modulepath/demo.layers.spi...       the API module - shared, so it is here and not in the layer
    modulepath/com.fasterxml.jackson.core-2.18.2.jar
    layers/render/demo.layers.impl...           isolated
    layers/render/com.fasterxml.jackson.core-2.15.4.jar

`demo.layers.impl` is *not* on the application's module path. `demo.layers.spi` is - and only
there, which is what makes the `Report` instance that crosses the boundary a single class rather
than two of the same name.

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

`bundle=true` ships `layers/<name>/` beside `modulepath/` and records it in
`application.properties`, so the launch command hands it over as
`-Djenesis.layer.render=<unpacked>/layers/render`. Docker bakes the same option into its
`ENTRYPOINT`, and a run through `Execute` passes it too.

An executable jar (`launcher=true`) needs no such option: the layer travels inside the jar and the
launcher reads it from there, never unpacking anything.

A layer is defined while the JVM runs, so `native=true` rejects a project that declares one.
