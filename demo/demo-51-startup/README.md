Startup demo
============

`java build/jenesis/Make.java` is the canonical command, and it pays for that
simplicity twice on every invocation. The launcher compiles the vendored engine
before `main` is entered, then compiles the rest of it lazily as the build
touches it; when the build ends the JVM exits and throws away every class it
just compiled and every method the JIT just optimised. The next call starts
from nothing again.

This demo runs the same build through a daemon instead:

    java build/jenesis/Make.java build

A daemon is never started unless asked for: set `jenesis.make.daemon=true` in
`jenesis.properties` at the project root, or pass `-Djenesis.make.daemon=true`.
Compiling the build sources once is on by default and already removes most of
what a daemon saves, so the daemon is the last increment rather than the first.

Why a second entry point
------------------------

Source mode compiles the file you name *plus everything it references*, so the
entry point decides the bill. `Project.java` drags in its whole closure - about
1.4 seconds before `main` even runs, and more as the build touches classes that
were not compiled yet.

`Make.java` deliberately references no engine class at all. It names the
server as a string and reaches it over a socket, so the Java launcher has one small
file to compile. Everything expensive happens in a JVM that is already running
and already warm.

That is the whole trick, and it is why this could not be a flag on a file that
declares `Project`: a flag read inside `main` arrives after the Java launcher has
already paid for the compile. `Project` therefore has no `main` at all - it is
the configuration API, and `Make` is the entry point.

| command                                        | cold  | warm  |
|------------------------------------------------|-------|-------|
| `... -Djenesis.make.compile=false`             | 8.0s  | 8.6s  |
| `java build/jenesis/Make.java build` (default) | 3.6s  | 0.75s |
| `... -Djenesis.make.daemon=true`               | 3.7s  | 0.66s |
| `java -cp … build.jenesis.Make` (installed)    | 3.7s  | 0.15s |

Compiling once is the default because it wins even on a build that runs a single
time - one batch compile beats the Java launcher compiling class by class as it loads
them. The daemon is the opt-in on top, and on a project this small it is worth
only another tenth of a second.

What the daemon is worth
------------------------

It is worth what your build spends on compiling. A Jenesis build has no script to
parse - the project is described by `pom.xml` or `module-info.java`, and the
settings are properties files - so there is no parsed or compiled build model to
keep in memory between calls. What there is, is javac: a build spends most of its
time inside it, and a JVM that has already compiled a few modules runs the next
build's javac faster, because the JIT has seen that code.

So the daemon pays in proportion to how much a build compiles, and the one-class
project above is the worst case for it:

| project                        | no daemon | daemon |
|--------------------------------|-----------|--------|
| `help`, compiling nothing      | 0.58s     | 0.63s  |
| this demo, one class           | 0.75s     | 0.66s  |
| `demo-04`, five modules        | 2.9s      | 2.0s   |

On a build with nothing to compile the socket costs more than the warm code
saves. By five modules it is about a third off, and it keeps growing with the
amount of compiling.

The last row is what the installed `jenesis` command does, and it is the fastest
because it never compiles anything: the client is already the compiled engine and
only has to reach the daemon.

Measured in this folder, deleting `target/` before every run so each does the
same work; only the engine and the JVM differ. About half of the remaining 0.6s
is the client's own compile. The build itself takes 0.44s in a cold JVM and
0.05s once the daemon is warm.

Running it
----------

    java build/jenesis/Make.java build     # first call compiles and starts the daemon
    java build/jenesis/Make.java build     # later calls reuse it
    java build/jenesis/Make.java --stop    # shut it down

A project that drives its own build rather than `Project` asks for the same thing
by constructing it, from a second source-mode file beside its entry point:

    // build/Make.java
    public class Make {
        public static void main(String... selectors) throws Exception {
            System.exit(new build.jenesis.Make("build.Demo").run(selectors));
        }
    }

`Make` compiles everything under the folder its own file sits in, so a custom
`build/Demo.java` lands in `.jenesis/classes` beside the engine and the daemon runs
that class's `main`. It has to be a separate file from `build/Demo.java` itself,
for the same reason `Make.java` is separate from `Project.java`: the file you
name is the one the launcher compiles.

The output is the build's own, streamed back frame by frame and replayed on the
client's `stdout` and `stderr`, so a piped or redirected build reads exactly as
it does without the daemon. The client exits with the build's status.

What appears under `.jenesis/`
------------------------------

    tool/           the compiled engine, and jenesis.digest, the fingerprint that produced it
    daemon.port     the loopback port the daemon listens on
    daemon.token    a 32-byte secret every request must present
    daemon.log      whatever the daemon writes outside a request; empty in normal operation

`daemon.port` and `daemon.token` are owner-only and the daemon deletes them when
it exits, but only if they are still its own - a daemon retiring while a
successor is already up will not take the successor's files with it.

Seeing that it is really one JVM
--------------------------------

    pgrep -f "DaemonServer $PWD"

The process id stays the same across builds. It changes when the engine changes:
the fingerprint covers the *content* of every `.java` file under
`build/jenesis/`, so `touch build/jenesis/Make.java` keeps the same daemon,
while editing one byte of it retires the running daemon and starts a fresh one.
A daemon therefore never serves a build with stale engine code.

What carries over and what does not
-----------------------------------

`-Djenesis.*` flags are the only thing that travels with a call. They are cleared
and set again around every build, so two calls with different flags cannot
contaminate each other:

    java -Djenesis.project.layout=modular build/jenesis/Make.java help   # layout modular
    java build/jenesis/Make.java help                                    # layout maven

The second call reports `maven`, the layout this demo's `pom.xml` infers, rather
than the `modular` the first call forced on the daemon.

Everything else is the daemon's *identity* rather than its configuration, because
a running JVM cannot change it: the build sources, the environment, the JVM
arguments, and any `-D` that is not a `jenesis.` one. A call that differs in any
of them replaces the daemon instead of being served by one configured for
something else - so `MAVEN_REPOSITORY_URI=... ` is honoured rather than silently
ignored, at the price of a restart. This is the same rule Gradle applies to the
properties a JVM cannot change after startup; the alternative, which mvnd takes,
is to reach for native `setenv`, and Jenesis has no native code to reach for.

`-Djenesis.daemon.*` configures the daemon process itself and is read when it
starts, so changing it takes effect on the next daemon:

    -Djenesis.daemon.idle=600                # exit after ten idle minutes (default: 10800)
    -Djenesis.daemon.options="-Xmx4g"        # JVM options for the daemon process

`options` defaults to `-Xmx2g`. Left to itself a JVM would take a quarter of the
machine's memory as its maximum heap, and a daemon that sits idle for hours on
that budget is a poor neighbour; raise it for a build that needs more.

Other JVM options you pass to the *client* apply to the client only; the daemon
was started earlier and its heap is already fixed. That is what
`jenesis.daemon.options` is for.

Limits
------

Requests are served one at a time, because the build's configuration lives in
system properties and those are per-JVM. A second build that arrives while one is
running is not queued: it is refused with the same message a second build gets
without a daemon, since two builds over one `target/` would interfere either way.
The daemon is scoped to one project root, as the port and token files live under
it.

A dockerized build (`-Djenesis.project.docker`) is refused: it replaces the
running process, which would take the daemon with it.

Being a long-lived JVM is the point and also the risk: it keeps whatever a build
leaves behind, in static state and in heap. It collects after every build it
serves, on a thread that runs once the client already has its answer, which holds
the live heap at a few megabytes over dozens of builds; without that the process
grew to hundreds of megabytes of garbage it had no reason to reclaim. It retires
on a build-source change and on the idle timeout, and `--stop` is the manual
reset. When a build behaves in a
way you cannot explain, stopping the daemon is the first thing to rule out - and
`java build/jenesis/Make.java` always runs with nothing kept.
