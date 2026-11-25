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

A cache server is sent one archive per step, which leaves out what is held
elsewhere: a file an input of the step holds, and a file of 64 KiB or more, which
is stored once on the server however many steps wrote it. The build keeps every
file it downloads or uploads in a folder of this machine, `~/.jenesis/cache` unless
`-Djenesis.cache.local=<folder>` names another, in the layout shown under *What the
cache keeps*. A large file any build of any project brought there is not
downloaded again. That folder is capped at 10 GiB unless its `cache.properties`
names another `size`. `-Djenesis.cache.inline=<bytes>` moves the size from which a
file is stored apart from its step.

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
nothing, `new BuildStepResult()`, is stored in every tier.

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

A cache server stores opaque entries under two hexadecimal segments and answers
three requests. `<uri>` is `jenesis.cache.uri`; every request carries
`Jenesis-Cache-Project` where a project is named and `Jenesis-Cache-Key` where a
token is set, and the server keeps the entries of one project apart from those of
another.

| request | answer |
| ------- | ------ |
| `GET <uri>/<a>/<b>` | `200` and the entry, or `404` |
| `HEAD <uri>/<a>/<b>` | `200` or `404`; tells the server the entry is in use |
| `PUT <uri>/<a>/<b>` with a `Content-Length` | `201` once stored, `204` where the entry exists, which is never rewritten |

A build stores two kinds of entry.

A **step** is stored at `<step>/<key>`: the hash of the step, and the hash of its
inputs taken after the text `jenesis-cache/2` and a zero byte. It is a zip read
front to back. Its first entry, `manifest`, is UTF-8 text of lines ending in a
line feed: `jenesis-cache/2 <digest algorithm>`, then one line
`<kind> <checksum> <size> <path>` per file, sorted by path, with `/` between the
segments of a path and a lower-case hexadecimal checksum.

| kind | the file |
| ---- | -------- |
| `f` | is an entry of the zip named by its checksum, once however many paths name it |
| `i` | is a file of an input of the step with that checksum, and is not sent |
| `b` | is 64 KiB or larger and stored apart, at `<blob>/<checksum>` |
| `d` | is an empty folder, written `d - 0 <path>` |

A **large file** is stored at `<blob>/<checksum>` as its bytes: `<blob>` is the
hexadecimal form of the text `blob:` and the digest algorithm in lower case,
`626c6f623a7368612d323536` for SHA-256.

To store a step, a build asks with `HEAD` for each large file it has not asked
for before, sends with `PUT` those the server answers `404` for, and only then
sends the step, so that no step names a file the server was not given. An entry
of the zip deflates fast, and one that is an archive already is stored as it is.

To restore a step, a build reads its zip, takes each `i` file from the folder of
the input that holds it and each `b` file from this machine or, where it is not
held, from the server with `GET`, and checks every file against its checksum. A
file that fails the check, an entry the manifest does not name, or a large file
the server does not hold makes the step a miss, and it runs. Any other answer of
the server - a refused credential, an entry too large, no answer within the
timeouts - is a miss or a step left unstored, never a failed build.

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

The count caps (`steps`, `versions`) are applied as each step is stored. The byte
cap (`size`) and `ttl` are applied once per build, in the background, when the
build first stores a step, so a build that stores nothing evicts nothing and no
step waits for a pass over the cache. The count caps and `ttl` evict manifests by
their timestamp, which `touch` keeps fresh, and a file that no remaining manifest
names leaves with them. The byte cap evicts
files instead: a file that an output folder still links costs no space of its own
and stays, and of the others the one linked least recently goes first, by the
change time a file system records for it; a manifest naming an evicted file is
dropped the next time it is asked for. On a file system without link counts every
file is a candidate, oldest first. `read` and `write` are the typical CI split: a
privileged job builds with the defaults (both `true`) to populate the cache, while
everyone else sets `write=false` to consume it read-only without mutating it.
Setting both to `false` turns the cache off entirely. A build reads the cache alone
without a change to the folder under `-Djenesis.cache.write=false`, which holds for a
cache server as well: such a build sends a server nothing, and keeps what it
downloads in the folder of this machine, which is its own. A folder that cannot be written to - a read-only mount, a
folder of another user - is read as it is: what a build would store or evict is left
out, and the build passes.

Where this fits
---------------

A folder, a cache server, and one layered in front of another are the same cache
as far as a build is concerned: `jenesis.cache.uri` names the shared one and
`jenesis.project.cache` keeps a local one in front of it. A step
compiled once is reused everywhere its inputs are identical, whether "everywhere"
means the next build on your laptop or every job on the cluster.
