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

Starting from a cache instead
-----------------------------

A JVM can also start from an ahead-of-time cache: a file holding the engine's
classes already loaded and linked, trained once and read by every later build.
It needs no process, no idle timeout and no memory between builds, and it
survives a reboot. Turn it on in `jenesis.properties` or in
`~/.jenesis/jenesis.properties`:

    jenesis.make.aot=true

It applies where the engine runs compiled, which is how the installed `jenesis`
command runs it: the first build trains the cache, and every later one starts
from it. Source mode is out of its reach - `java build/jenesis/Make.java` runs in
a JVM that is already running by the time the cache could be handed to it - so
there the setting changes nothing. The compiled engine the default leaves in
`.jenesis/classes` shows the effect as well:

    java -cp .jenesis/classes build.jenesis.Make -Djenesis.make.aot=true   # trains the cache
    java -cp .jenesis/classes build.jenesis.Make -Djenesis.make.aot=true   # starts from it

| no-op build of this demo                  | plain  | cache  |
|-------------------------------------------|--------|--------|
| the engine as `jenesis` runs it           | 0.69s  | 0.43s  |
| the engine from `.jenesis/classes`        | 0.58s  | 0.49s  |

Training costs a build of about two to three seconds and some 22 MB on disk. The
installed command gains most, because it runs the engine from a jar the cache
reads directly; from `.jenesis/classes`, the build first packs the classes into
`.jenesis/engine.jar` and starts a second JVM that can use the cache, which costs
part of what the cache saves.

The cache lives at `.jenesis/engine-<hex>.aot`, where the hex names the engine
and the JVM it was trained for, so a changed engine or an upgraded JDK trains a
new one and the old one is removed. Two settings configure it:

    -Djenesis.aot.file=.jenesis/engine.aot   # where it lives, relative to the project
    -Djenesis.aot.lifetime=P7D               # train it again once it is this old

`help`, `skill`, `configuration` and `properties` only print, so they never train
or use a cache. The daemon and the cache are two answers to the same cost, so
naming both - or the cache beside `-Djenesis.make.compile=false`, which leaves
nothing compiled to cache - stops the build with a message naming both settings.
Where builds are frequent and small, or where a machine builds many projects now
and then, the cache is the better trade; in a tight edit-build loop on a large
project, the daemon's warm JIT still wins.
