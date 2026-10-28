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
| `... -Djenesis.make.mode=source`           | 8.0s  | 8.6s  |
| `... -Djenesis.make.mode=compiled`         | 3.6s  | 0.75s |
| `java build/jenesis/Make.java` (default)    | 3.7s  | 0.66s |
| `jenesis` (the installed command)           | 3.7s  | 0.15s |

Measured in this folder, deleting `target/` before every run so each does the
same work. Every mode but `source` compiles the build sources once and runs from
those classes, which is where most of the difference in the first row comes
from; the installed command is fastest because it is already compiled. The
first line a build prints names the mode it ran in, as does the `started` line of
`target/.jenesis.events.jsonl`:

    [STARTED]   Building in '.../demo-06-startup/target' in daemon mode...

Reusing the JVM
---------------

By default a build is handed to a daemon, a JVM kept running for the builds that
follow. The first build starts it, every later one reuses it, and `--stop` shuts
down every daemon of the machine:

    java build/jenesis/Make.java          # first call starts it
    java build/jenesis/Make.java          # later calls reuse it
    java build/jenesis/Make.java --stop   # shut them down

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

`-Djenesis.make.mode=compiled` builds without a daemon, and so does a build that
names nothing but sets `jenesis.process.mode` to `fork` or `aot`, runs in a
container (`jenesis.project.docker`), or publishes a release
(`jenesis.release.publish=true`), so that a release's credentials and signing key
are only ever handed to a JVM no earlier build ran in. Naming
`jenesis.make.mode=daemon` keeps the daemon in every one of these cases.

One daemon for many projects
----------------------------

A daemon is not tied to a project. Any project whose build runs the same engine
on the same JVM, with the same JVM options and environment, hands its build to
a daemon that is free, and a project whose engine, JVM or environment differs
starts one of its own. At most three daemons run at once on a machine; starting a
fourth first stops the one used least recently, which finishes the build it is
running. A project's daemons announce themselves in `~/.jenesis/daemons`, each
with its log, beside a copy of every engine compiled from source that a daemon
runs, so no project's `.jenesis/classes` is pulled from under another's build.

    -Djenesis.daemon.limit=3            # how many may run at once (default: 3)
    -Djenesis.daemon.shared=false       # daemons for this project alone (default: true)
    -Djenesis.daemon.idle=600           # exit after ten idle minutes (default: 10800)
    -Djenesis.daemon.options="-Xmx4g"   # JVM options of the daemon itself
    -Djenesis.daemon.folder=...         # where daemons announce themselves (default: ~/.jenesis/daemons)

Unset, `jenesis.daemon.options` hands the daemon the `-X`, `-D` and `-ea` options of
the JVM that started the build, so `java -Xmx4g build/jenesis/Make.java` builds in
a daemon with four gigabytes, and a JVM started with other options gets a daemon
of its own. The folder, the limit, the idle time and the options are the
machine's to choose: only the command line or `~/.jenesis/jenesis.properties` may
set them, never a file the project provides. Every process the build starts - a
test JVM, a warm tool - runs in the project's root, as it would without a daemon
(`jenesis.process.directory` names another folder of the project).

What travels with a call
------------------------

`-Djenesis.*` flags travel with every call and are cleared again afterwards, so
two calls with different flags cannot contaminate each other:

    java -Djenesis.project.layout=modular build/jenesis/Make.java help
    java build/jenesis/Make.java help

The second call reports `modular_to_maven`, the layout this demo's `module-info.java`
infers, rather than the `modular` the first call asked for.

Everything a running JVM cannot change - the engine, the JVM, its options and the
environment - is handed to another daemon instead of being served by one
configured for something else, so `MAVEN_REPOSITORY_URI=...` is honoured at the
price of a start. Editing the engine does the same, so a daemon never serves a
build with stale engine code.

What one build can leave to the next
------------------------------------

A daemon runs the builds of every project that shares it in one JVM, and a build
runs code its project chose: its plugins, its annotation processors, the tools it
names. Whatever that code changes for the whole JVM, the next build would
inherit. So after every build the daemon restores the system properties, the
default locale and the default time zone, and it ends once a build changed a
JVM-wide default it cannot restore - the security providers, the default TLS
context, socket factory and host name verifier, the default authenticator, proxy
selector, cookie handler or response cache, the uncaught exception handler or the
standard input - saying which:

    [DAEMON]    The build changed the default authenticator, which a later build in this JVM would inherit - the daemon ends after it

The next build starts a fresh one. The JVMs a build keeps warm for its tools are
kept per project, as they run in its root.

This keeps one project's settings out of another's build; it does not make a
daemon a boundary. Since Java 24 no JVM can confine the code it runs, so code that
sets out to stay - a thread left running, a field changed by reflection, a stream
handler only the JVM's first caller may install - outlives its build and reaches
every later build of the daemon, a release excepted, which never runs in one by
default. Build a project you do not trust with `-Djenesis.make.mode=compiled`,
which keeps it from the daemons of other projects, or in a container
(`-Djenesis.project.docker`), which also keeps it from your files.

Limits
------

One build per project at a time: a second build of the same project arriving
while one runs is refused, exactly as it is without a daemon, since two builds
over one `target/` would interfere either way. A daemon runs one build at a time,
so two projects building at once run in two daemons.

When a build behaves in a way you cannot explain, stopping the daemons is the
first thing to rule out - `java -Djenesis.make.mode=compiled build/jenesis/Make.java`
always runs with nothing kept.

Starting from a cache instead
-----------------------------

A JVM can also start from an ahead-of-time cache: a file holding the engine's
classes already loaded and linked, trained once and read by every later build.
It needs no process, no idle timeout and no memory between builds, and it
survives a reboot. Turn it on in `jenesis.properties` or in
`~/.jenesis/jenesis.properties`:

    jenesis.make.mode=aot

It applies where the engine runs compiled, which is how the installed `jenesis`
command runs it: the first build trains the cache, and every later one starts
from it. Source mode is out of its reach - `java build/jenesis/Make.java` runs in
a JVM that is already running by the time the cache could be handed to it - so
there only the tools a build starts in JVMs of their own are cached. The
compiled engine the default leaves in `.jenesis/classes` shows the effect as
well:

    java -cp .jenesis/classes build.jenesis.Make -Djenesis.make.mode=aot   # trains the cache
    java -cp .jenesis/classes build.jenesis.Make -Djenesis.make.mode=aot   # starts from it

| no-op build of this demo                  | plain  | cache  |
|-------------------------------------------|--------|--------|
| the engine as `jenesis` runs it           | 0.69s  | 0.43s  |
| the engine from `.jenesis/classes`        | 0.58s  | 0.49s  |

Training costs a build of about two to three seconds and some 22 MB on disk. The
installed command gains most, because it runs the engine from a jar the cache
reads directly; from `.jenesis/classes`, the build first packs the classes into
`.jenesis/aot/engine.jar` and starts a second JVM that can use the cache, which costs
part of what the cache saves.

The cache lives in `.jenesis/aot/`, beside the caches of the tools a build
starts in JVMs of their own, which the same setting gives caches as well. The
folder records the engine and the JVM it was started for, so a changed engine or
an upgraded JDK clears it at once and the caches are trained again. Two settings
configure it:

    -Djenesis.aot.folder=.jenesis/aot   # where it lives, relative to the project
    -Djenesis.aot.lifetime=P7D          # clear it once it is this old

`help`, `skill` and its pages, `configuration` and `properties` only print, so they never train
or use a cache. The daemon and the cache are two answers to the same cost, and
`jenesis.make.mode` names one of them - `source`, `compiled`, `aot` or `daemon` -
so a build never runs with both. Where builds are frequent and small, or where a machine builds many projects now
and then, the cache is the better trade; in a tight edit-build loop on a large
project, the daemon's warm JIT still wins.
