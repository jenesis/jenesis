Startup demo
============

What a build costs to start, and what a reused JVM saves on top of that. The
project is a single class, so the figures below are the floor rather than a
typical build.

Running it
----------

From this directory:

    java build/jenesis/Make.java          # build
    java build/jenesis/Make.java          # again, warm

| command                                     | cold  | warm  |
|---------------------------------------------|-------|-------|
| `... -Djenesis.make.compile=false`          | 8.0s  | 8.6s  |
| `java build/jenesis/Make.java` (default)    | 3.6s  | 0.75s |
| `... -Djenesis.make.daemon=true`            | 3.7s  | 0.66s |
| `jenesis` (the installed command)           | 3.7s  | 0.15s |

Measured in this folder, deleting `target/` before every run so each does the
same work. The default compiles the build sources once and runs from those
classes, which is where most of the difference in the first row comes from; the
installed command is fastest because it is already compiled.

Reusing the JVM
---------------

A daemon is never started unless you ask for it, either in a `jenesis.properties`
at the project root or on the command line:

    java -Djenesis.make.daemon=true build/jenesis/Make.java   # first call starts it
    java -Djenesis.make.daemon=true build/jenesis/Make.java   # later calls reuse it
    java build/jenesis/Make.java --stop                       # shut it down

What it saves is compile time, so it pays in proportion to how much a build
compiles:

| project                        | no daemon | daemon |
|--------------------------------|-----------|--------|
| `help`, compiling nothing      | 0.58s     | 0.63s  |
| this demo, one class           | 0.75s     | 0.66s  |
| five modules                   | 2.9s      | 2.0s   |

On a build with nothing to compile it costs more than it saves; by five modules
it is about a third off, and it keeps growing with the amount of compiling. The
build's output is streamed back as it happens, so a piped or redirected build
reads exactly as it does without a daemon, and the command exits with the build's
status.

Two settings configure the daemon process itself, read when it starts, so a
change takes effect on the next one:

    -Djenesis.daemon.idle=600           # exit after ten idle minutes (default: 10800)
    -Djenesis.daemon.options="-Xmx4g"   # JVM options for the daemon (default: -Xmx2g)

Any other JVM option you pass applies to your own command rather than to the
daemon, which was started earlier.

What travels with a call
------------------------

`-Djenesis.*` flags travel with every call and are cleared again afterwards, so
two calls with different flags cannot contaminate each other:

    java -Djenesis.make.daemon=true -Djenesis.project.layout=modular build/jenesis/Make.java help
    java -Djenesis.make.daemon=true build/jenesis/Make.java help

The second call reports `maven`, the layout this demo's `pom.xml` infers, rather
than the `modular` the first call asked for.

Everything a running JVM cannot change - the build sources, the environment, the
JVM arguments, and any `-D` that is not a `jenesis.` one - replaces the daemon
instead of being served by one configured for something else, so
`MAVEN_REPOSITORY_URI=...` is honoured at the price of a restart. Editing the
engine replaces it too, so a daemon never serves a build with stale engine code.

Limits
------

One build at a time: a second build arriving while one runs is refused, exactly
as it is without a daemon, since two builds over one `target/` would interfere
either way. A daemon serves one project root. A containerized build
(`-Djenesis.project.docker`) is refused, because it replaces the running process.

When a build behaves in a way you cannot explain, stopping the daemon is the
first thing to rule out - `java build/jenesis/Make.java` on its own always runs
with nothing kept.
