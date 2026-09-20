Ahead-of-time cache demo
========================

Start a build from a JVM ahead-of-time cache instead of keeping a daemon alive. The
first build that runs with `jenesis.aot.enabled` trains the cache and pays for it;
every later build starts from that file with the engine already loaded and linked.
Nothing stays resident between builds, and the file survives a reboot.

Run it
------

From this directory:

    java build/Demo.java

which compiles the engine, builds this project off it without the cache, trains one,
reuses it, and then runs the same build with the cache named on the command line, the
way an installed launcher can:

    Without the cache:      405 ms
    Training it:            2168 ms
    Reusing it:             305 ms
    Named by a launcher:    234 ms
    Cache:                  20 MB at .jenesis/engine.aot
    Beside the daemon:      refused, naming jenesis.make.daemon
    Without compiling:      refused, naming jenesis.make.compile
    A cache for `help`:     never trained, as intended

Layout
------

    demo/demo-63-aot-cache
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java      trains the cache, reuses it, and times what each shape costs
    `-- sources              a one-class module, so that startup dominates the build
        |-- module-info.java
        `-- sample/app/App.java

The settings
------------

Three properties, all off by default:

    jenesis.aot.enabled=true
    jenesis.aot.file=.jenesis/engine.aot
    jenesis.aot.lifetime=P7D

`enabled` turns it on, `file` says where the cache lives relative to the project root,
and `lifetime` is an optional ISO-8601 age after which the cache is trained again. They
are ordinary properties, so `jenesis.properties` or `~/.jenesis/jenesis.properties`
carries them from build to build - which is the point, since a cache is a file rather
than a process.

Two settings contradict it and are refused rather than quietly ignored.
`jenesis.make.daemon` keeps the engine loaded in a JVM of its own, which is the very
thing a cache replaces, and `jenesis.make.compile=false` leaves nothing compiled for a
cache to serve. Either one beside `jenesis.aot.enabled` stops the build with a message
naming both settings, because a build that silently ignored one of them would look
configured and behave as if it were not.

Beside the cache lives a `.digest` naming the engine and the JVM it was trained for.
A JDK upgrade, a changed engine, or a cache older than the lifetime is trained again
rather than silently ignored; the JVM would otherwise fall back to loading everything,
which costs a little more than having no cache at all.

A daemon is keyed by the same two things, the engine and the JVM version in full, so a
patch upgrade retires one and retrains the other alike. How carefully each is checked
differs, because the two answer for different things. A daemon's identity decides which
code runs, so it hashes the engine's bytes. This identity only decides when to train
again: the JVM refuses a cache that does not match the jar it is handed or the build it
was written by, and loads normally instead, so a size and a timestamp are enough here
and cost a millisecond rather than fifteen. The daemon also hashes the environment and
the JVM options, which a cache of loaded classes has no use for.

Where the saving lands
----------------------

The cache holds the engine's classes, loaded and linked, so it removes a fixed block of
startup and nothing else. Who hands it to the JVM decides how much of that block
survives:

- **A launcher names it.** An installed CLI runs the engine from a jar, so its launcher
  can put `-XX:AOTCache` on the command line and the build starts straight from the
  cache. That is the 405 ms against 234 ms above, and it holds for a build of any size.
- **Make relaunches for it.** Where nothing named the cache, Make starts a second JVM
  that can use one, which costs a fork and keeps most of the rest: 405 ms against
  305 ms.

Source mode is out of reach either way. `java build/jenesis/Make.java` compiles
`Make.java` in the JVM that is already running, long before any of this tool's code
could ask for a cache, and that JVM cannot be handed one afterwards. The setting is
therefore ignored there, and it applies once the engine is compiled - which is how the
installed CLI runs, and how this demo measures.

The setting is worth turning on where builds are frequent and small, and it is not
worth turning on to speed up one long build, where startup is noise.

Against the daemon
------------------

Both remove startup; only the daemon also keeps a warm JIT, which is why it stays ahead
on builds that compile a lot. Measured on one machine against a no-op rebuild, a plain
run took 418 ms, the cache 228 ms and a warm daemon 196 ms; on a clean build of a
300-class project the three were 1904 ms, 1218 ms and 635 ms.

The daemon costs a process: about a second to start, roughly 70 MB resident, one per
project, and a lifetime of its own. The cache costs a training run and 20 to 37 MB on
disk. Where a machine builds many projects occasionally, or where CI starts from
nothing every time, the cache is the better trade; in a tight edit-build loop on a
large project, the daemon still wins.
