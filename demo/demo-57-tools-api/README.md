tools API demo
==============

Runs Jenesis through `java.util.spi.ToolProvider` - the JDK's own interface for a tool
that runs **inside** a JVM rather than as a process of its own, the way `javac`, `jar`
and `jlink` are reachable. `build.jenesis` declares three of them, named after the
commands they answer to on a command line:

  * `jenesis-make` builds a project
  * `jenesis-exec` builds it and then runs it
  * `jpx` runs a published program without building anything

A program that has the module resolved reaches any of them by name and runs a build
without forking anything:

    ToolProvider.findFirst("jenesis-make").orElseThrow()
            .run(out, err, "-Djenesis.project.version=1.0.0", "build");

Settings come first, as `-Djenesis.<name>[=<value>]` arguments, and everything after them
is what the command line would take: selectors for `jenesis-make`, the program's own
arguments for `jenesis-exec`, the target and its options for `jpx`. A setting that is not
a `jenesis.*` one is refused rather than passed on.

A whole command line can live in a file instead, the way the JDK's own tools read one:

    # the settings and selectors this run stands for
    -Djenesis.print.progress=false
    build

and `@<file>` stands for the arguments it holds, the argument file `profiles` introduces.
Nothing about it belongs to the tools: the commands read it the same way, so
`java build/jenesis/Make.java @release.args` is the same run from a shell.

A tool run is configured by those arguments alone, never by the properties of the JVM it
runs in, so two runs in one program do not interfere and neither leaves anything behind.
The demo builds the same module twice with a different version, runs the built program,
asks `jpx` for its help, and then shows that this JVM still holds no value for the setting
both builds were given.

A released `build.jenesis` registers the three both ways - in its module descriptor and in
`META-INF/services/java.util.spi.ToolProvider` - so the service loader finds them whether the
jar is on the module path or the class path. In source mode there is neither, so the demo
builds the same tools directly and says which of the two routes it took. The contract is the
same either way.

What a tool run cannot do
-------------------------

Three settings replace the process a build runs in, which no in-process tool can do, so
each is refused by name rather than ignored:

  * `jenesis.toolchain.version`, which relaunches the build on another JDK
  * `jenesis.project.docker`, which relaunches it inside a container
  * `jenesis.execute.docker`, which does the same to the program `jenesis-exec` runs

Run such a build through the `jenesis-make` or `jenesis-exec` command instead.

Everything the build itself prints arrives on the writers, because a run is handed the pair of
consumers its lines go to rather than writing to the JVM's streams: two runs in one program can
print to different places, and neither redirects anything the calling program owns.
`jenesis-exec` forks the program it runs, as the command does, so that program writes to the
stdio of the JVM the tool was called from rather than to the writers.

Build and run it
----------------

From this directory:

    java build/Demo.java

which prints:

    no service loader here, so the tools are built directly - the contract below is the same
    jenesis-make -Djenesis.project.version=1.0.0 @target/build.args -> 0, produced demo.tools@1.0.0
    jenesis-make -Djenesis.project.version=2.0.0 @target/build.args -> 0, produced demo.tools@2.0.0
    jenesis-exec builds and runs the program, which prints on its own:
    hello
    jenesis-exec -> 0
    jpx --help -> 0
    jenesis.project.version in this JVM: null
