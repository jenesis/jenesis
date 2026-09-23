Native access in a layer demo
=============================

`module-layers` showed a library that keeps a dependency in a module layer of its
own, so that the application using the library never learns about it.
`native-access` showed how a module that runs native code is granted access. This
demo combines the two: the module that needs native access sits in a library's
layer, and the application grants access to the library alone. The library
passes it on to its layer, as it would to a dependency it had shaded.

The demo has four modules:

- `spi` - `demo.strings.spi`, the interface the library shares with its layer;
- `text` - `demo.strings.text`, which measures a string with C's `strlen` through
  the foreign function API;
- `library` - `demo.strings.library`, which keeps `text` in its layer `strings`;
- `app` - the application `demo.strings.app`, which uses the library.

Run it
------

From this directory:

    java build/Demo.java

The build packages the application as a bundle, and the demo runs it with
`--illegal-native-access=deny`, so a module the launch does not grant cannot call
a restricted method at all:

    strlen("layered") = 7, measured in the library's layer with native access true

Layout
------

    demo/demo-50-native-access-layer
    |-- build/Demo.java                   builds, unpacks the bundle, runs it with deny
    |-- build/jenesis                     symlink to ../../../sources/build/jenesis
    |-- jenesis.properties                jenesis.dependency.native=strict
    |-- spi
    |   |-- module-info.java
    |   `-- demo/strings/spi/Length.java
    |-- text
    |   |-- module-info.java              declares nothing
    |   `-- demo/strings/text/NativeLength.java
    |-- library
    |   |-- module-info.java              the layer, and native access passed on to it
    |   `-- demo/strings/library/Strings.java
    `-- app
        |-- META-INF/build.jenesis/packaging.properties   bundle=true
        |-- module-info.java              @jenesis.native demo.strings.library
        `-- demo/strings/app/Application.java

Passing native access on
------------------------

The library declares its layer as in `module-layers`, and names the module in it
that needs native access with a third kind of line:

    /**
     * @jenesis.layer strings api demo.strings.spi
     * @jenesis.layer strings provider demo.strings.text
     * @jenesis.layer strings native demo.strings.text
     */
    module demo.strings.library {
        requires build.jenesis.launcher;
        requires demo.strings.spi;
        exports demo.strings.library;
    }

It asks for the layer with its own lookup:

    Length length = Launcher.instance(MethodHandles.lookup(), "strings", Length.class);

The layer is defined on the library's behalf, and so is the native access its
modules are given: the JDK only allows it if the library has native access
itself. A `native` line therefore also records that the library needs native
access, in its jar's `Jenesis-Native-Access` attribute, as `native-access`
showed for `@jenesis.native`.

Granting the library
--------------------

The application grants native access to the library it runs, and to nothing
else:

    /**
     * @jenesis.main demo.strings.app.Application
     * @jenesis.native demo.strings.library
     */
    module demo.strings.app {
        requires demo.strings.library;
    }

A grant is otherwise never inherited, but what a granted module passes on to
its own layers is granted with it: nothing outside the library can reach those
modules, so only the library can decide which of them need access. The
application names neither `demo.strings.text` nor the layer, and the launch
carries both grants:

    --enable-native-access=demo.strings.library
    -Djlayer.enableNativeAccess.strings=demo.strings.text

The application cannot name that module either. Add `demo.strings.text` to its
`@jenesis.native`, and the build fails as it would for any name the application
does not run:

    @jenesis.native grants main/module/demo.strings.text native access, but demo.strings.app does not resolve it at run time

Delete the `@jenesis.native demo.strings.library` line from
`app/module-info.java`, and the build fails, because this demo's
`jenesis.properties` sets `jenesis.dependency.native=strict`. It asks for the
library, not for the module in its layer:

    demo.strings.app runs modules that declare a need for native access it does not grant:
      demo.strings.library names demo.strings.library
      demo.strings.library passes native access on to demo.strings.text in its layer strings, once granted itself
