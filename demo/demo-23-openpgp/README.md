OpenPGP signature demo
======================

A checksum proves a dependency has not changed since you vetted it. It cannot prove that what you
vetted was genuine, because the checksum was taken from whatever the repository served.
`@jenesis.signature` answers the other question: who produced these bytes.

This demo verifies a real dependency, `org.assertj:assertj-core`, against the key AssertJ really
signs with.

Run it
------

    java build/Demo.java

              [VERIFIED]  main/maven/org.assertj/assertj-core 3.27.0 OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 signed 2024-12-19
    [ok]      declared: the key that signed the dependency is the key declared for it
    [blocked] rotated: a different key is declared for the same dependency
    [ok]      vendored: the same key, read from a list rather than a line
    [blocked] declared: under strict, a transitive dependency nothing vouches for
    [ok]      complete: a key declared for everything the closure resolves

Verification forks `gpgv`, so it must be installed; the run prints `[skipped]` otherwise. Nothing
else is set up: no key is generated, no artifact is signed here, and no repository is faked. The
signatures are the ones AssertJ published.

Declaring a key
---------------

`declared/sources/module-info.java` names the fingerprint and the coordinates it covers:

```java
/**
 * @jenesis.alias org.assertj.core org.assertj/assertj-core
 * @jenesis.pin org.assertj/assertj-core 3.27.0 SHA-256/0b4d1400...
 * @jenesis.signature OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 org.assertj/*
 */
```

The fingerprint comes first, because one key normally signs many artifacts, and a Maven token may
end in `/*` to cover a whole groupId. Right after an artifact is downloaded, the build fetches the
detached `.asc` published beside it, assembles a keyring from the fingerprints declared - fetching
each by fingerprint from a key server - and asks `gpgv` to check the signature against it.

**The line carries no version**, and that is the point: one key signs every release it signs, so
vetting a key once covers every future release from it, where a checksum covers exactly one file.

**Nothing writes this line.** A fingerprint is obtained out of band and checked against what the
project publishes - AssertJ's is on its site and in its release documentation - and added by hand.
A tool that filled it in from whatever it downloaded would only be recording its own guess.

`rotated/` declares a different fingerprint for the same dependency. The signature AssertJ published
is perfectly valid, so only the comparison against the declaration catches the substitution, and the
failure names both sides.

One list for many modules
-------------------------

`vendored/` declares no fingerprint inline. It names a list instead:

```java
/**
 * @jenesis.signature signature-vendor.properties
 */
```

```properties
# build.jenesis/signature-vendor.properties
OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 = org.assertj/*
```

The fingerprint is the key of the properties file rather than the coordinate, so one coordinate can
sit under two fingerprints through a rotation. A list is **only ever read from disk**: there is no
form that resolves one from a repository, because a list you had to download would itself need
verifying.

How much is checked
-------------------

`-Djenesis.dependency.signature` decides, and defaults to `none`, so a declaration alone never
switches verification on:

| Value | Verifies |
|---|---|
| `none` | nothing; an ordinary build needs no gpg at all |
| `declared` | every coordinate a line covers, and claims nothing about the rest |
| `strict` | the same, and refuses a coordinate no line covers or that publishes no signature |

The fourth case is that difference. `declared/` names a key for AssertJ but not for Byte Buddy,
which AssertJ brings with it, so `strict` refuses the build: one unvetted coordinate otherwise costs
the whole mode. `complete/` declares a key for both and passes.

`-Djenesis.print.signatures` names each coordinate that was checked and each one no line covers,
which is how you find out what to declare before moving from `declared` to `strict`.

Layout
------

    demo-23-openpgp
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds each project and asserts which of them may not build
    |-- declared/                the fingerprint AssertJ signs with
    |-- rotated/                 a different fingerprint for the same dependency
    |-- vendored/                the same fingerprint, read from a local list
    `-- complete/                a fingerprint for every coordinate the closure resolves

A key proves who released an artifact, not that it is safe, that it matches its published source, or
that the key was not stolen. The [`sigstore`](../demo-24-sigstore/README.md) demo answers the same
question without anyone holding a key at all.
