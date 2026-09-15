Module layers demo
==================

Two versions of one library, in one JVM, with no relocation. This is the thing
shading is used for, done with the module system instead of a bytecode rewriter: the
conflicting dependency and its tree are resolved into their own dependency group, kept
off the application's module path at compile time and at run time, and defined at run
time as a child `ModuleLayer` with its own class loader.

The application here requires `jackson-core` 2.18.2. The renderer requires
`jackson-core` 2.15.4. Both keep the package `com.fasterxml.jackson.core` - nothing is
renamed, nothing is merged, `META-INF/services` and resources keep working - and the
layer's class loader is what keeps them apart.

Run it
------

From this directory:

    java build/Demo.java

which builds the project, unpacks the produced `bundle.zip`, launches the app out of
it on this JDK's own `java`, and prints:

    the application sees jackson-core 2.18.2, loaded by jdk.internal.loader.ClassLoaders$AppClassLoader@...
    the layer sees jackson-core 2.15.4, loaded by jdk.internal.loader.Loader@...
    the seam module is the parent's: true

Two loaders, two versions, one package name. The last line is the part that makes it
usable rather than merely possible.

The three modules
-----------------

    api/        demo.layers.api        the seam - one interface, Report
    renderer/   demo.layers.renderer   the adapter, isolated in the layer, pins jackson 2.15.4
    app/        demo.layers.app        the application, pins jackson 2.18.2

`app/module-info.java` declares the layer:

    @jenesis.layer render module/demo.layers.renderer
    @jenesis.layer render shared demo.layers.api
    @jenesis.pin com.fasterxml.jackson.core 2.18.2 SHA-256/...
    @jenesis.pin render/module/com.fasterxml.jackson.core 2.15.4

The first line puts the renderer - and its whole transitive closure, including its
jackson - into the `render` group. The group is resolved exactly like any other, so it
is pinned, checksum-verified, signed, and reported in the SBOM and the licence and
vulnerability checks with everything else; `render/…` pins simply name the group first.
The second line is the seam, described below.

Why a layer needs a seam
------------------------

A class loader delegates *up*, never down. So the application cannot call into the
layer by naming a type in it - and it could not compile such a call anyway, because the
layer's group is not on its `--module-path`. Try it and javac says the module does not
exist, which is the point: shading fails at run time or silently binds the wrong copy,
while this fails at the compiler.

What crosses instead is the *seam*: a module the layer resolves from its parent rather
than carrying a second copy, so both sides work with the very same classes. Here the
seam is `demo.layers.api`, and the call across it is a plain interface call on `Report`
- no reflection, no proxy, no `MethodHandle`.

The application reaches the first object across the seam the way the JDK intends:

    ModuleLayer parent = Main.class.getModule().getLayer();
    ModuleFinder finder = ModuleFinder.of(Path.of(System.getProperty("jenesis.layer.render")));
    Configuration configuration = parent.configuration()
            .resolveAndBind(finder, ModuleFinder.of(), roots);
    ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(
            configuration, List.of(parent), Main.class.getClassLoader()).layer();
    for (Report report : ServiceLoader.load(layer, Report.class)) { … }

`demo.layers.api` is absent from the layer's `ModuleFinder`, so resolution falls
through to the parent configuration and the layer reads the parent's module - which is
what the third printed line asserts. The application declares `uses demo.layers.api.Report`;
the `uses` belongs to the module doing the lookup, not to the module declaring the
interface.

What the build checks
---------------------

Delete the `shared` line and the build fails:

    layer render provides demo.layers.api.Report, but the module that declares it,
    demo.layers.api, is isolated in the layer - the application would look the service
    up against a different class of the same name and find no provider; declare
    @jenesis.layer render shared demo.layers.api to resolve it from the parent

Without that check the build would succeed and the application would simply find no
provider at run time. The build also rejects a module shared with the layer whose own
closure reaches back into it - the case where a type crosses the seam that both sides
define under one name and two loaders, which the JVM reports much later, as a
`LinkageError` far from its cause - and a jar in a layer that carries no module, since
a named module cannot read the unnamed module.

Where the layer ends up
-----------------------

The `layers` step writes one folder per layer beside the module's artifacts:

    layers/render/com.fasterxml.jackson.core-2.15.4.jar
    layers/render/demo.layers.renderer-1-SNAPSHOT.jar

`bundle=true` carries that folder into the zip and records it in
`application.properties` as `layer.render=layers/render`, so a deployment resolves it
against wherever it unpacked the bundle. `Demo.java` does exactly that and passes
`-Djenesis.layer.render=<unpacked>/layers/render`. A Docker image built with
`docker=<image>` bakes the same option into its `ENTRYPOINT`, and a plain run through
Jenesis passes it too.

Limitations
-----------

A layer is defined at run time, so `native=true` rejects a project that declares one:
a native image resolves its module graph ahead of time. `launcher=true` rejects one as
well, because its jars would live *inside* the launcher jar rather than beside it;
`bundle=true` is the packaging that ships layers today.

Layers are a modular-layout feature: they are declared in `module-info.java`, and a
layer resolves modules only.
