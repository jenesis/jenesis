Module layer over a legacy tree
===============================

`../demo-21-module-layers` keeps one library private and shows what a layer *is*. This one shows
what a layer is usually *for*: shading is applied to legacy code, and legacy code is a tree of jars
that name themselves nowhere. Naming every one of them would be the work the layer exists to avoid.

Here the layer holds commons-beanutils 1.9.4 and the commons-logging and commons-collections it
drags behind it. None of the three carries a `module-info` or an `Automatic-Module-Name`. One of
them is named, the other two are not, and that is the whole configuration.

Run it
------

From this directory:

    java build/Demo.java

which builds the project, unpacks the produced `bundle.zip`, launches the app out of it, and prints:

    the application requires one module, and knows no commons-anything
    the library's private tree: commons-beanutils converted "42" to Integer 42, logging through
        commons-logging/commons-logging/1.2.jar, loaded by java.net.URLClassLoader@...

The four modules
----------------

    spi/        demo.legacy.spi      the API module - shared with the layer
    library/    demo.legacy.library  declares the layer, bootstraps it, consumes through the SPI
    impl/       demo.legacy.impl     isolated, and the only module that names the legacy library
    app/        demo.legacy.app      an ordinary consumer, requiring one module and nothing else

Name one jar, not the tree
--------------------------

A layer splits a module path and a class path exactly as the application does. What carries a
module identity is resolved into the layer; what does not is read through the layer's class path.
So `impl/module-info.java` names the one jar its own code calls, and nothing else:

    @jenesis.alias commons.beanutils commons-beanutils/commons-beanutils

    module demo.legacy.impl {
        requires commons.beanutils;
        requires demo.legacy.spi;

        provides demo.legacy.spi.Beans with demo.legacy.impl.ConvertingBeans;
    }

commons-logging and commons-collections are never mentioned. They arrive as commons-beanutils' own
dependencies, carry no identity, and become the layer's unnamed module. The descriptor says so:

    layer.modulepath.demo.legacy.library.beans=demo.legacy.impl-1-SNAPSHOT.jar,commons.beanutils-1.9.4.jar
    layer.classpath.demo.legacy.library.beans=commons-logging%2Fcommons-logging%2F1.2.jar,...

The rule that decides the shape
-------------------------------

**Only an automatic module reads the unnamed module.** That is the module system's rule, not this
tool's, and it is why the alias matters: a jar with no identity of its own becomes an *automatic*
module when named, and an automatic module reads a class path as it would on a plain `java -cp`.
So commons-beanutils reaches commons-logging, while `demo.legacy.impl` - a module with a descriptor
of its own - could not, and javac would not let it try.

That is also why `ConvertingBeans` reports commons-logging's location through the class loader
rather than by naming the class: it cannot name it.

A layer that holds no module at all is refused, since a layer is reached through the modules it
holds. Name one with `@jenesis.alias` or `modules.properties`, and the rest needs no names.

Where the jars are
------------------

Nowhere special. Every jar is stored once in the bundle's single `jars/` folder - the application's,
the layer's module path and the layer's class path alike - and the descriptor names which path holds
which. The application's own module path names none of them, which is what keeps the legacy tree off
it; its generated POM names none of them either, so a consumer resolving this library through Maven
does not inherit them.

A legacy tree also expects the platform modules a plain class-path launch roots, so a layer with a
class path adds `--add-modules ALL-MODULE-PATH,ALL-DEFAULT` to the launch even when every jar of the
application itself is a module.
