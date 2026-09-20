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

which builds this project without the cache, trains one, reuses it, and then runs the
same build off the compiled engine the way the installed CLI runs it:

    Without the cache:      1213 ms
    Training it:            2978 ms
    Reusing it:             1201 ms
    Cache:                  20 MB at .jenesis/engine.aot

    The same build off the compiled engine, as the installed CLI runs it:
      without the cache:    410 ms
      with the cache:       227 ms
    A cache for `help`:     never trained, as intended

Layout
------

    demo/demo-63-aot-cache
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    |-- build/Demo.java      trains the cache, reuses it, and times both shapes
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

Beside the cache lives a `.digest` naming the engine and the JVM it was trained for.
A JDK upgrade, a changed engine, or a cache older than the lifetime is trained again
rather than silently ignored; the JVM would otherwise fall back to loading everything,
which costs a little more than having no cache at all.

Where the saving lands
----------------------

The cache holds the engine's classes, loaded and linked, so it removes a fixed block of
startup and nothing else. Two shapes decide how much of that survives:

- **A cheap entry point keeps it.** The installed CLI runs the engine from a jar, so
  its launcher names the cache on the command line and the build starts straight from
  it. That is the 410 ms against 227 ms above, and it holds for a build of any size.
- **Source mode spends it again.** `java build/jenesis/Make.java` compiles `Make.java`
  in memory before any of the tool's own code runs, and the JVM it is already in cannot
  be given a cache. Make therefore relaunches into a JVM that can, which costs a fork
  and recovers only part of what the cache saves.

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
