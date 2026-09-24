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

Three projects, one public dependency
-------------------------------------

Each project is an ordinary modular project that requires `org.apache.commons.lang3` from Maven
Central. They differ in one line:

```java
/**
 * @jenesis.alias org.apache.commons.lang3 org.apache.commons/commons-lang3
 * @jenesis.pin org.apache.commons/commons-lang3 3.20.0 SHA-256/69e5c9fa...
 */
```

- **`pinned`** carries the version and the checksum above, and builds.
- **`unpinned`** carries the version alone.
- **`tampered`** carries the same version and a checksum of zeroes.

Nothing writes these lines by hand: `java build/jenesis/Make.java pin` resolves the closure and
writes the version and checksum it found, which is how the `pinned` line above was produced.

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
[`openpgp`](../demo-27-openpgp/README.md) and [`sigstore`](../demo-28-sigstore/README.md) demos are
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

Layout
------

    demo-26-pinning
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds each project and asserts which of them may not build
    |-- pinned/                  a version and a matching checksum
    |-- unpinned/                a version and no checksum
    `-- tampered/                a version and a checksum of zeroes

The two failing projects are wrong on purpose, so unlike most demos this one is partly a set of
projects that must *not* build.
