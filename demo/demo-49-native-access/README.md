Native access demo
==================

Code that calls into native memory or native functions - through the foreign
function and memory API, JNI, or a library built on either - uses *restricted
methods*. The JDK lets a module call them only when the launch grants it native
access with `--enable-native-access`; otherwise it prints a warning today and
will refuse the call in a future release. The `@jenesis.native` tag declares
that grant, and Jenesis adds it to every launch it makes.

A grant belongs to the program that runs the code, never to the library that
wants it. A library can only *say* that it needs native access; the module that
runs it decides. This demo has three modules:

- `text` - the library `demo.natives.text`, which counts the bytes of a string
  with C's `strlen`, called through the foreign function API;
- `text-test` - its tests;
- `app` - the application `demo.natives.app`, which uses the library.

Run it
------

From this directory:

    java build/jenesis/Execute.java

The build compiles and tests the modules, then runs the application:

    native access: demo.natives.text=true, demo.natives.app=false
    "Hello, native world!" is 20 bytes long, as C's strlen counts it

The library was granted native access, the application - which asked for none -
was not, and no warning was printed.

Layout
------

    demo/demo-49-native-access
    |-- build/jenesis                     symlink to ../../../sources/build/jenesis
    |-- jenesis.properties                jenesis.dependency.native=strict
    |-- text
    |   |-- module-info.java              @jenesis.native: this library needs native access
    |   `-- demo/natives/text/NativeText.java
    |-- text-test
    |   |-- module-info.java              @jenesis.native demo.natives.text, for the test run
    |   `-- nativetest/NativeTextTest.java
    `-- app
        |-- META-INF/build.jenesis/packaging.properties   bundle=true
        |-- module-info.java              @jenesis.native demo.natives.text, for the application
        `-- demo/natives/app/Application.java

Saying that a library needs native access
-----------------------------------------

The library states its need with a bare `@jenesis.native`:

    /**
     * @jenesis.native
     */
    module demo.natives.text {
        exports demo.natives.text;
    }

Its jar then carries the manifest attribute `Jenesis-Native-Access: true`. The
attribute grants nothing: it only tells whoever uses the library that a grant is
needed.

Granting it
-----------

The application names the module it grants:

    /**
     * @jenesis.main demo.natives.app.Application
     * @jenesis.native demo.natives.text
     */
    module demo.natives.app {
        requires demo.natives.text;
    }

Only the declarations of the module that runs are read. The application's run
is granted what `app/module-info.java` names and nothing that a dependency
declares, so no library on the path can grant itself - or anything else - native
access. The tests are a run of their own and grant the library in
`text-test/module-info.java`, which is why they pass without a warning too.

A token names a module or a Maven coordinate (`org.example/jni`), as the pin
grammar does, and several may share one tag. A grant adds no dependency: what it
names must already be on the module's run-time path, or the build fails saying
so. A bare `@jenesis.native` in a module that runs grants that module itself.

Every launch the build makes receives the grant: the test runs, `Execute`, and
what is packaged. This demo's `bundle` carries it in its launch:

    "--module-path"
    "jars/classes.jar:jars/demo.natives.text-0-SNAPSHOT.jar"
    "--enable-native-access=demo.natives.text"
    "--module"
    "demo.natives.app/demo.natives.app.Application"

A jar on the class path cannot be named, so a grant for one becomes
`--enable-native-access=ALL-UNNAMED`, which covers the whole class path.

A module isolated in a layer, as `module-layers` showed, is granted the same
way: the module that runs names it, by its module name or as
`layer:<name>/module/<module>`. The JVM cannot name a module that only exists
once the layer is defined, so the launch carries
`-Djlayer.enableNativeAccess.<name>=<module>` and the launcher grants it when it
defines the layer, and is granted native access itself to do so.

Refusing an ungranted need
--------------------------

By default a library's `Jenesis-Native-Access` is only information. This demo's
`jenesis.properties` sets `jenesis.dependency.native=strict`, which fails the
build of any module whose run includes such a jar without granting it. Delete
the `@jenesis.native demo.natives.text` line from `app/module-info.java` and
build again:

    Dependencies of demo.natives.app declare that they need native access, which only the module that runs them grants:
      main/maven/demo.natives/demo.natives.text/0-SNAPSHOT (module demo.natives.text)
    Declare each with @jenesis.native <module> or <groupId>/<artifactId>, or build with -Djenesis.dependency.native=ignore

With `-Djenesis.dependency.native=ignore` the same build succeeds, and the run
shows what the JDK does with an ungranted call:

    native access: demo.natives.text=false, demo.natives.app=false
    WARNING: A restricted method in java.lang.foreign.Linker has been called
    ...

Maven projects
--------------

A `pom.xml` project declares the same in a comment block, read only from the
project's own POM. An empty block states that the project itself needs native
access; tokens grant it to dependencies:

    <!--jenesis.native-->
    <!--jenesis.native
    org.example/jni
    -->

The project's tests run its own jar as a dependency, so they are granted it as
well.
