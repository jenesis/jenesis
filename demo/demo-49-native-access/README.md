Native access demo
==================

Code that calls into native memory or native functions - through the foreign
function and memory API, JNI, or a library built on either - uses *restricted
methods*. The JDK lets a module call them only when the launch grants it native
access with `--enable-native-access`; otherwise it prints a warning today and
will refuse the call in a future release. The `@jenesis.native` tag declares
that grant, and Jenesis adds it to every launch it makes.

Whether native code runs is decided by what a program uses, not by the library
that offers it. A library can offer a native API that most of its users never
touch, so it declares nothing. The module that uses the API names the module
that needs access, and every module that runs it grants that access itself. This
demo has four modules:

- `text` - the library `demo.natives.text`, which offers C's `strlen` through
  the foreign function API;
- `text-test` - its tests;
- `words` - the library `demo.natives.words`, which uses that function;
- `app` - the application `demo.natives.app`, which uses `words`.

Run it
------

From this directory:

    java build/jenesis/Execute.java

The build compiles and tests the modules, then runs the application:

    native access: demo.natives.text=true, demo.natives.app=false
    "Hello, native world!" is 20 bytes long, as C's strlen counts it

`demo.natives.text` was granted native access, the application was not, and no
warning was printed.

Layout
------

    demo/demo-49-native-access
    |-- build/jenesis                     symlink to ../../../sources/build/jenesis
    |-- jenesis.properties                jenesis.dependency.native=strict
    |-- text
    |   |-- module-info.java              declares nothing
    |   `-- demo/natives/text/NativeText.java
    |-- text-test
    |   |-- module-info.java              @jenesis.native demo.natives.text, for the test run
    |   `-- nativetest/NativeTextTest.java
    |-- words
    |   |-- module-info.java              @jenesis.native demo.natives.text
    |   `-- demo/natives/words/Words.java
    `-- app
        |-- META-INF/build.jenesis/packaging.properties   bundle=true
        |-- module-info.java              @jenesis.native demo.natives.text, for the application
        `-- demo/natives/app/Application.java

Naming the need
---------------

`words` calls the native function, so it names the module that needs access:

    /**
     * @jenesis.native demo.natives.text
     */
    module demo.natives.words {
        requires transitive demo.natives.text;
        exports demo.natives.words;
    }

A token names a module or a Maven coordinate (`org.example/jni`), as the pin
grammar does, and several may share one tag. A module that calls restricted
methods itself names itself. Every name is recorded in the module's jar as the
manifest attribute `Jenesis-Native-Access: demo.natives.text`.

Granting it
-----------

A declaration grants native access only to the runs of the module that makes
it: its tests, `Execute` and what it packages. It is never inherited. The
application runs `words`, so it grants the same module again:

    /**
     * @jenesis.main demo.natives.app.Application
     * @jenesis.native demo.natives.text
     */
    module demo.natives.app {
        requires demo.natives.words;
    }

The tests are a run of their own and grant it in `text-test/module-info.java`. A
grant adds no dependency: what it names must already be part of the run, or the
build fails saying so.

Every launch the build makes receives the grant. This demo's `bundle` carries it
in its launch:

    "--module-path"
    "jars/classes.jar:jars/demo.natives.text-0-SNAPSHOT.jar:jars/demo.natives.words-0-SNAPSHOT.jar"
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
defines the layer. It grants it on behalf of the module that asks for the layer,
through the `MethodHandles.lookup()` that module passes, so the JDK only allows
it if that module has native access itself: name it as well.

Discovering what to grant
-------------------------

`Jenesis-Native-Access` is how a module that runs `words` finds out what it has
to grant. `jenesis.dependency.native` decides what the build does with it:
`ignore`, the default, does nothing; `warn` prints each name that the running
module does not grant; `strict` fails the build. Delete the
`@jenesis.native demo.natives.text` line from `app/module-info.java` and build
with a warning:

    java -Djenesis.dependency.native=warn build/jenesis/Execute.java

    WARNING: demo.natives.app runs modules that declare a need for native access it does not grant:
      demo.natives.words names demo.natives.text
    Grant each with @jenesis.native in demo.natives.app if it runs code that needs it, or build with -Djenesis.dependency.native=ignore

The application still runs, and the JDK warns when the restricted method is
called. This demo's `jenesis.properties` sets `strict`, so without the flag the
same build fails with that message.

Maven projects
--------------

A `pom.xml` project declares the same names in a comment block, read only from
the project's own POM, and names itself with its own `<groupId>/<artifactId>`:

    <!--jenesis.native
    org.example/jni
    -->

The project's tests run its own jar as a dependency, so they are granted what
the project names as well.
