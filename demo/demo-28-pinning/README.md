Pinning demo
============

A pin records two things about a dependency in your own sources: the exact version, and the
`SHA-256` of the bytes that version served. Every later build re-hashes what it downloads and
compares. This demo gets each half wrong on purpose.

Run it
------

    java build/Demo.java

    [ok]      pinned: a dependency pinned to a version and a checksum
    [ok]      unpinned: a version-only dependency builds by default
    [blocked] unpinned: the same dependency under strict pinning
    [blocked] tampered: a dependency whose pinned checksum does not match
    [ok]      pinned: rebuilt offline from the artifacts it stored
    [blocked] pinned: offline, with nothing stored to build from

Three projects, one public dependency
-------------------------------------

Each project is an ordinary modular project that requires `org.apache.commons.lang3` from Maven
Central. They differ only in their pins, one for the module and one for the Maven coordinate it
resolves to, as `../demo-02-java-modular` introduced them:

```java
/**
 * @jenesis.pin org.apache.commons.lang3 3.20.0 SHA-256/69e5c9fa...
 * @jenesis.pin org.apache.commons/commons-lang3 3.20.0 SHA-256/69e5c9fa...
 */
```

- **`pinned`** carries the versions and the checksums above, and builds.
- **`unpinned`** carries the versions alone.
- **`tampered`** carries the same versions and checksums of zeroes.

Nothing writes these lines by hand: `java build/jenesis/Make.java pin` resolves the closure and
writes the versions and checksums it found, which is how the `pinned` lines above were produced.

Whether, and which bytes
------------------------

The two failures answer different questions.

**`unpinned` builds by default.** A version was asked for and that version arrived; nothing more
was claimed. Under **strict pinning** it fails, because there is nothing to verify the download
against. That is the mode a hardened build wants: one unpinned coordinate otherwise costs the
guarantee for the whole closure. The demo passes it in code, as `pinning(Pinning.STRICT)`; on a
command line it is `-Djenesis.dependency.pin=strict`.

**`tampered` fails without strict pinning being asked for at all.** Every download is compared
against its pin, so a coordinate whose bytes do not match is refused outright - which is what a
swapped or re-signed artifact looks like from inside a build.

What a checksum does not say
----------------------------

A pin is taken from whatever the repository served when you wrote it. It proves an artifact has not
changed since, not that what you recorded was genuine: an artifact swapped before your first `pin`
is frozen as an accepted pin just the same. Who produced the bytes is a different question, and the
[`openpgp`](../demo-29-openpgp/README.md) and [`sigstore`](../demo-30-sigstore/README.md) demos are
the two answers to it.

Refreshing a pin
----------------

Pins do not float, so a pinned project never picks up a newer version on its own. To refresh
deliberately:

    java -Djenesis.dependency.pin=ignore build/jenesis/Make.java pin

`ignore` drops the existing pins, so versions resolve afresh and the recorded checksums are not
consulted; `pin` then writes what that resolution found. Run against `tampered` it would heal the
wrong checksum by replacing it - which is exactly why that run is the one to review before
committing.

Building offline
----------------

A pinned dependency that was downloaded once is stored in `.jenesis/artifacts/`, checked against its
pin, and every later build takes it from there. Once a build has run, a change to your own code needs
no network at all, and you can say so:

    java -Djenesis.repository.offline=true build/jenesis/Make.java

Offline, Jenesis downloads nothing: what it needs comes from `.jenesis/artifacts/`, from your local
Maven repository or from a local module folder, and a file that is in none of them fails the build
with its URL named rather than being fetched. The demo shows both: the `pinned` project rebuilds
offline from what its first build stored, and fails once that store is gone and no local Maven
repository holds the dependency either. A version range or the newest release is answered from the Maven
metadata an earlier build stored beside the artifacts, at the versions it named then - one more reason
to pin, since a pin never depends on when it was last resolved.

Layout
------

    demo-28-pinning
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds each project and asserts which of them may not build
    |-- pinned/                  a version and a matching checksum
    |-- unpinned/                a version and no checksum
    `-- tampered/                a version and a checksum of zeroes

The two failing projects are wrong on purpose, so unlike most demos this one is partly a set of
projects that must *not* build.
