Build cache demo
================

Every build already has an *incremental* cache: Jenesis content-hashes each
step's inputs and outputs under `target/`, so a warm rebuild only re-runs the
steps whose inputs changed. This demo adds the second tier - a build cache
*outside* `target/` that can hand a step the output of an earlier build (or a
different checkout, machine, or CI) instead of re-running it at all.

That cache can live in two places - a project-local folder, or a shared location
you name - and the two compose.

A project-local cache (the easy one)
------------------------------------

The simplest form needs nothing but a flag; Jenesis keeps a content-addressed
cache under `.jenesis/cache`, rooted at the project root:

    -Djenesis.project.cache

The property is a filesystem path; an empty value (as above) resolves to
`.jenesis/cache` under the project root, and a value relocates it. This is the
main example below. Anything the cache already holds is linked into place instead
of being built again, and because the cache sits outside `target/`, it survives a
`target/` wipe.

Build it
--------

This is the smallest possible project - a `module-info.java` and one
dependency-free source - so the only thing of interest is *who* produced the output. Run it from
this directory.

**1. Bootstrap the cache.** A normal build populates `.jenesis/cache` while it
compiles:

    java -Djenesis.project.cache build/jenesis/Make.java

    [EXECUTED]  .../compile/javac in 0.07 seconds
    [EXECUTED]  .../binary/classes in 0.02 seconds
    [EXECUTED]  .../binary/artifacts/jar in 0.02 seconds
    [COMPLETED] Finished in ...

**2. Force a full rebuild, served from the cache.** `-Djenesis.executor.rebuild=true`
deletes `target/` first, so the incremental cache is gone and *every* step is a
forced miss that would normally re-run from scratch. The build cache lives outside
`target/`, so it survives - and serves them:

    java -Djenesis.project.cache \
         -Djenesis.executor.rebuild=true \
         build/jenesis/Make.java

    [EXECUTED]  .../compile/javac in 0.00 seconds
    [EXECUTED]  .../binary/classes in 0.00 seconds
    [EXECUTED]  .../binary/artifacts/jar in 0.00 seconds
    [COMPLETED] Finished in ...

The steps still print `[EXECUTED]` - their output *was* produced - but it came
from the cache, not from `javac`, which is why each lands in ~0.00s. Add
`-Djenesis.print.cache` to make it explicit: each step served from the cache then
prints a `[LOADED]` line and each one written to it a `[STORED]` line. On this toy
project the saving is tiny; on a real module the compile that took seconds returns
instantly. Delete `.jenesis/cache` to start over.

A shared cache (the `uri` one)
------------------------------

`.jenesis/cache` is private to one checkout. To share results across checkouts,
machines, or CI, name an explicit location with `-Djenesis.cache.uri=`. The value
is a URI: it typically points at a cache *server* over HTTP, but it can equally be
a `file://` location on a local or shared (NFS, network drive) file system:

    -Djenesis.cache.uri=https://cache.example.com      # a cache server
    -Djenesis.cache.uri=file:///mnt/team/jenesis-cache # a shared (or local) folder

A `file://` URI resolves a `BuildExecutorFileCache` (the same on-disk format as the
local cache); an `http(s)://` URL selects `BuildExecutorHttpCache`, which speaks the
protocol below to the server, naming the cache project with
`-Djenesis.cache.project=<project>` and authenticating with
`-Djenesis.cache.token=<token>` (both sent as headers, never in the URL). A non-URI
value is rejected - use `file://` for an on-disk location.

A cache server is asked for files, never for a step's whole output. The build keeps
every file it downloads or uploads in a folder of this machine, `~/.jenesis/cache`
unless `-Djenesis.cache.local=<folder>` names another, in the layout shown under
*What the cache keeps*. A file any build of any project already brought there is
not downloaded again, and a step whose files the server already holds is stored
with one small request. That folder is capped at 10 GiB unless its
`cache.properties` names another `size`.

The shared cache can be used **two ways**.

**As a replacement** - the shared cache only, no local tier (e.g. an ephemeral CI
runner whose disk is thrown away anyway):

    java -Djenesis.cache.uri=file:///mnt/team/jenesis-cache build/jenesis/Make.java

**Layered behind the local cache** - set both `-Djenesis.project.cache` *and*
`-Djenesis.cache.uri=...`, and the local cache sits in front of the shared one:
every read tries `.jenesis/cache` first and only falls through to the shared cache
on a miss; a shared hit is copied into `.jenesis/cache` on the way past, so the next
read is local; and a store writes through to both. A second build on the same
checkout never re-downloads what the first already fetched:

    java -Djenesis.project.cache \
         -Djenesis.cache.uri=https://cache.example.com \
         -Djenesis.cache.project=acme -Djenesis.cache.token=alice \
         build/jenesis/Make.java

Serving a step from the local tier means no `GET` reaches the server - which would
let that shared entry age toward eviction there even though it is in active use. So
a local hit also sends the server a best-effort `HEAD` (it never transfers the
body), and the server treats it as a read, bumping the entry's recency just as a
`GET` would. Each tier keeps its own LRU and both stay warm.

A step no cache stands in for
-----------------------------

A step is cached because its output is all it does. A step that does more - one
that publishes, or writes a file into the project as `pin` and the IDE steps do -
has to run on this machine every time, since restoring its folder would leave the
effect out. Such a step says so on the result it returns:

    public class Publish implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) {
            ...
            return CompletableFuture.completedStage(new BuildStepResult().cached(false));
        }
    }

Its output is stored in no cache, neither under `.jenesis/cache` nor on a server,
so no later build finds an entry to restore in its place. A result that says
nothing, `new BuildStepResult()`, is stored in every tier as before.

Layout
------

    demo/demo-58-build-cache
    |-- build/jenesis        symlink to ../../../sources/build/jenesis
    `-- sources
        |-- module-info.java     module demo.cache, no dependencies
        `-- sample/Sample.java

There is nothing build-cache-specific in the project itself: the cache is an
engine capability you switch on from the command line, so any project gains it
for free.

What the cache keeps
--------------------

A cache folder holds every file once, under the checksum of its content, and one
small manifest per step result that names the files the step wrote:

    .jenesis/cache
    |-- cache.properties                 optional, see below
    `-- sha-256                          one store per jenesis.executor.digest
        |-- blobs/<2 hex>/<62 hex>       a file, named by its SHA-256
        `-- steps/<step>/<inputs>        a manifest: <checksum> <size> <path> per line

Storing a step links each of its files into `blobs/` unless a file of that content
is there already, so a jar that five steps hand on, or a class file that did not
change between two builds, costs its bytes once. Restoring a step links the files
its manifest names into the step's folder, and no bytes are copied where the cache
and `target/` share a file system. An empty folder of the output is a line
`- 0 <path>`.

Every file is checked against its name as it is restored, with the pass that would
hash the restored output anyway. A file whose content does not match its name - a
tool wrote into a linked file, a disk lost a block - is deleted with the manifest
that named it, and the step runs instead of handing the damage on. A manifest
naming a file the cache does not hold is dropped the same way.

The checksums are those of `jenesis.executor.digest`, SHA-256 by default. A build
under another digest keeps a store of its own beside this one, as it could verify
no file of this one.

The protocol of a cache server
------------------------------

Every request carries `Jenesis-Cache-Digest`, the algorithm that names the files
(`SHA-256`), `Jenesis-Cache-Project` where a project is named and
`Jenesis-Cache-Key` where a token is set. `<uri>` is `jenesis.cache.uri`,
`<step>` the hexadecimal hash of the step and `<inputs>` that of its inputs. A
manifest is UTF-8 text, one line per file, `<checksum> <size> <path>`, sorted by
path, with `/` between the segments of a path and `- 0 <path>` for an empty
folder; a checksum is lower-case hexadecimal.

| request | body | answer |
| ------- | ---- | ------ |
| `GET <uri>/step/<step>/<inputs>` | none | `200` and the manifest, or `404` |
| `HEAD <uri>/step/<step>/<inputs>` | none | `200` or `404`; tells the server the step is in use |
| `POST <uri>/blobs` | the checksums wanted, one per line | `200` and a zip with one entry per file the server holds, named by its checksum; a file it does not hold is left out |
| `PUT <uri>/step/<step>/<inputs>` | the manifest | `2xx` once stored, or `409` and the checksums the server lacks, one per line |
| `PUT <uri>/blobs` | a zip with one entry per file, named by its checksum | `2xx` once every entry is stored |

A build restores a step by asking for its manifest, then for the files this
machine lacks in one `POST`, and checks every file against its name before it
keeps it; a file that fails the check, or that the server left out, makes the step
a miss and it runs. A build stores a step by sending its manifest; on `409` it
uploads the files named in the answer in one zip and sends the manifest again. A
server stores a manifest only while it holds every file it names, checks an
uploaded file against its name, and keeps the files of one project apart from
those of another, so that no project learns what another built by asking for a
checksum. The zip a build uploads deflates fast and stores what is already an
archive.

Tuning with `cache.properties`
------------------------------

Drop an optional `cache.properties` at the cache root - `.jenesis/cache/cache.properties`
for the project-local cache, or `<folder>/cache.properties` for a file-system
shared one - to tune the writes; every key has a default, so the file may be
omitted entirely:

| key          | default   | effect                                                                 |
| ------------ | --------- | ---------------------------------------------------------------------- |
| `digest`     | `SHA-256` | algorithm folding a step's inputs into the name of its manifest        |
| `steps`      | `250`     | maximum number of steps that keep manifests                            |
| `versions`   | `10`      | maximum number of manifests kept per step, one per variant of its inputs |
| `size`       | unset     | maximum total bytes of files kept; over it, the files no output folder links are evicted by `lru` until under (unset = no size cap) |
| `lru`        | `true`    | evict the least recently used when over a limit (`false` = most recently) |
| `touch`      | `true`    | bump a manifest's timestamp on read, so reads keep hot steps alive     |
| `ttl`        | unset     | ISO-8601 duration (e.g. `P30D`); manifests not touched within it are evicted on a background sweep (unset = no age eviction) |
| `read`       | `true`    | serve cache reads; `read=false` makes every lookup a miss              |
| `write`      | `true`    | populate the cache; `write=false` serves reads but never writes, evicts, or touches |

Eviction happens as steps are stored. The count caps (`steps`, `versions`) and
`ttl` evict manifests by their timestamp, which `touch` keeps fresh, and a file
that no remaining manifest names leaves with them. The byte cap (`size`) evicts
files instead: a file that an output folder still links costs no space of its own
and stays, and of the others the one linked least recently goes first, by the
change time a file system records for it; a manifest naming an evicted file is
dropped the next time it is asked for. On a file system without link counts every
file is a candidate, oldest first. `read` and `write` are the typical CI split: a
privileged job builds with the defaults (both `true`) to populate the cache, while
everyone else sets `write=false` to consume it read-only without mutating it.
Setting both to `false` turns the cache off entirely.

Where this fits
---------------

A folder, a cache server, and one layered in front of another are the same cache
as far as a build is concerned: `jenesis.cache.uri` names the shared one and
`jenesis.project.cache` keeps a local one in front of it. A step
compiled once is reused everywhere its inputs are identical, whether "everywhere"
means the next build on your laptop or every job on the cluster.
