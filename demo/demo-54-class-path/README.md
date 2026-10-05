Class path demo
===============

A modular jar declares its services in `module-info.java`, which the module path
reads and the class path does not: there, a `ServiceLoader` finds a provider only
through a `META-INF/services/<service>` file. A `classpath.properties` in a
configuration folder makes the build write those files from the module's
`provides` clauses, so one jar serves both.

Build it
--------

From this directory:

    java build/jenesis/Make.java

The module provides a `java.util.spi.ToolProvider`, and its main class looks that
tool up by name. The jar now runs from either path:

    java -p target/.../demo.classpath-0-SNAPSHOT.jar -m demo.classpath/sample.Main
    Hello from a service, found by the module path

    java -cp target/.../demo.classpath-0-SNAPSHOT.jar sample.Main
    Hello from a service, found by the class path

Without `build.jenesis/classpath.properties` the second command fails with
`No greeter`, because the jar carries no `META-INF/services` entry.

What it does
------------

The file is empty: its presence switches the step on, for every module the
configuration folder covers. For each `provides <service> with <providers>`
clause of a module's compiled `module-info`, the jar gets a
`META-INF/services/<service>` file naming the providers, one per line. A module
that grants native access to itself with `@jenesis.native` also gets
`Enable-Native-Access: ALL-UNNAMED` in its manifest, which `java -jar` reads where
the module path reads `--enable-native-access` - unless the manifest sets that
attribute already. The main class needs nothing: `@jenesis.main` puts it into the
manifest and into `module-info` alike. A module without a `module-info` is a
class-path jar already and gets nothing.

Two things the class path cannot do fail the build instead of failing at run
time:

- a provider must be a public class with a public constructor taking no
  arguments. The module path also accepts a public static `provider()` method,
  which a class-path `ServiceLoader` ignores;
- a module that ships its own `META-INF/services/<service>` for a service it also
  `provides` is refused, since the build writes that file itself.

`-Djenesis.generate.classpath=false` switches the step off, in a profile if need
be, without deleting the file.

Layout
------

    demo/demo-54-class-path
    |-- build/jenesis                     symlink to ../../../sources/build/jenesis
    |-- build.jenesis/
    |   `-- classpath.properties          empty: write META-INF/services from module-info
    `-- sources/
        |-- module-info.java              provides java.util.spi.ToolProvider with sample.Greeter
        `-- sample/
            |-- Greeter.java              the tool, saying which path found it
            `-- Main.java                 looks the tool up with ToolProvider.findFirst
