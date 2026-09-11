Supply-chain security demo
==========================

Three questions about a dependency, and what answers each:

| Question | Answered by |
|----------|-------------|
| May we use a dependency we cannot verify at all? | **strict pinning** |
| Are these the exact bytes we vetted? | **a pin checksum** |
| Were the bytes we vetted the ones upstream produced? | **`@jenesis.signature`** |

The first two are pinning's halves. The third is the one no hash can reach: a checksum is
taken from whatever the repository served, so a swapped artifact is written in as an
accepted pin just the same. This demo proves all three by getting each wrong on purpose.

Run it
------

    java build/Demo.java

    [ok]      unpinned: a version-only dependency builds by default
    [blocked] unpinned: a version-only dependency under strict pinning
    [blocked] tampered: a dependency whose pinned checksum does not match
    [ok]      signed: pin records the key that signed an undeclared coordinate
    [blocked] rotated: the declared key is not the one that signed the artifact
    [ok]      rotated: an already-pinned coordinate is not re-verified by default

The signature half is self-contained and reaches no network, and **nothing binary is
committed**: `Demo.java` builds a byte-reproducible jar, generates a throwaway key in a
gpg home under `target/`, signs the jar with it, and publishes both to a `file:` Maven
repository - then pins two standalone projects against it. If gpg is not installed those
three lines print `[skipped]`, because there is nothing they could verify.

Pinning: whether, and which bytes
---------------------------------

`Demo.java` builds one module at a time with a `+<module>` selector. Both get one
guarantee wrong:

- **`unpinned`** declares a dependency with a version but **no checksum**. It builds by
  default, but fails under **strict pinning** (`pinning(Pinning.STRICT)`, the in-code
  form of `-Djenesis.dependency.pin=strict`) - there is nothing to verify the download
  against, which is what a hardened environment should refuse.
- **`tampered`** pins the same dependency to a **wrong `SHA-256`**. It fails **even
  without** strict pinning: `Dependencies` hashes every fetched file and compares it to
  the pin, so a coordinate whose bytes do not match is rejected outright - exactly what
  would happen if a repository served a swapped artifact.

Signatures: who produced them
-----------------------------

`@jenesis.signature` records the OpenPGP key that signed a coordinate's artifact. The
fingerprint comes first, because one key normally signs many artifacts, and the
coordinates it covers follow - using the same token grammar as every other `@jenesis`
tag, so one form serves a Maven coordinate and a module repository alike:

    @jenesis.signature OpenPGP/360FF3E0DF4B835B5E983E9E40278877A3DE7949 org.example/lib some.module

A Maven token may end in `/*` to cover **every artifact of one groupId**, which is what
the two projects here use:

    @jenesis.signature OpenPGP/360FF3E0DF4B835B5E983E9E40278877A3DE7949 org.example/*

`pin` never writes a wildcard itself. It records the exact coordinates it verified, and
widening trust across a whole group stays a decision you make by hand - but once you have
made it, `pin` adds no redundant line for an artifact the wildcard already covers.

The two signature projects here are **modular**, so they declare this as a javadoc tag on
`module-info.java` beside their `@jenesis.pin` lines - which is also where `pin` writes what
it records. A `pom.xml` carries the same declarations in a `<!--jenesis.signature ... -->`
comment instead, beside the existing `<!--jenesis.pin ... -->` form, so this demo shows both
file formats: the pinning half is Maven, the signature half is modular.

The line carries **no version**: one key signs every release it signs, so the declaration
stays put across version bumps. That is the whole economy of this approach - vetting a key
once covers every future release from that key, where a hash covers exactly one file and
every version bump is a fresh, unvetted trust event.

The two projects
----------------

- **`signed`** declares **nothing** for the coordinate. `pin` verifies the detached
  signature, records the key it found, and the demo asserts the tag now sits in
  `module-info.java` before restoring it. That is what adding a dependency actually looks like: the
  key arrives as a line in your diff, for you to check against the project's published
  KEYS before committing it.
- **`rotated`** declares a different fingerprint. The signature on the artifact is
  perfectly valid - gpg is happy with it - so **only** the comparison against the
  declaration catches the substitution. That is precisely what recording the signer buys
  you, and what a checksum alone cannot see.

The failure names both keys and the fix:

    main/maven/org.example/lib 1.0: signed by OpenPGP/360FF3E0... but only
    OpenPGP/00000000...DEADBEEF is declared for it; add OpenPGP/360FF3E0... to a
    @jenesis.signature line to accept a key rotation, then run pin again

Accepting a genuine key rotation is therefore an **addition**: list the new key too, and
both are accepted until you drop the old one. That lands in the diff where a reviewer
sees it.

Scope: what a pin run actually verifies
---------------------------------------

`jenesis.dependency.signature` selects how much each `pin` run checks. It defaults to
`unpinned` wherever a `@jenesis.signature` line is declared, and to `none` otherwise.

| Value      | Verifies                                                          |
|------------|-------------------------------------------------------------------|
| `none`     | nothing                                                           |
| `unpinned` | only coordinates that arrived without a pin checksum (the default) |
| `all`      | every external coordinate                                         |
| `strict`   | every external coordinate, and an unsigned one fails              |

The last line of the demo is the one worth reading twice. Under the default scope the
`rotated` project **builds**, contradiction and all, because its coordinate already
carries a pin checksum and so is not something this run is establishing. That is not an
oversight: it is the delta that makes this cheap. A routine version bump verifies a
handful of artifacts rather than the whole closure, and the coordinates it skips were
verified when their pin was written. Pass `all` when you want the whole closure re-checked
anyway, as the demo does for its first two signature cases.

Verification is an ordinary forked tool: `process-gpg.properties` in the configuration
folder adds arguments to every invocation, `jenesis.print.gpg` shows each one, and
`jenesis.signature.command` names a different binary. A broken invocation fails loudly
with the command to reproduce, rather than passing for "no signature found".

Where the keys come from
------------------------

Jenesis records a fingerprint; gpg supplies the key material from the local keyring. An
unknown key is reported as `NO_PUBKEY` with the command to fetch it, rather than being
retrieved unattended - obtaining a key and checking it against the project's published
KEYS file is the human judgement this whole mechanism rests on, and it should not happen
behind your back.

An ordinary build reads none of this. It enforces the pin and needs no gpg, no keys and no
keyserver, because the pin already carries the earlier verdict forward: **signatures are an
update-time check**.

The key this demo uses is generated fresh on every run and thrown away with `target/`. It
signs nothing else and is worth no trust whatsoever - which is also why none of it is
committed: a repository of sources should not carry jars, signatures or keys.

Layout
------

    demo-46-supply-chain-security
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds and signs the fixture, asserts all six outcomes
    |-- pom.xml                  aggregator over the two pinning modules
    |-- unpinned/pom.xml         commons-lang3 with a version but no checksum
    |-- tampered/pom.xml         commons-lang3 pinned to a deliberately wrong SHA-256
    |-- signed/                  standalone modular project declaring no key yet
    `-- rotated/                 standalone modular project declaring the wrong key

The jar, its signature, the key and the `file:` repository are all built under `target/`
at run time, so nothing binary is committed.

The pinning modules are wrong on purpose, so unlike the other demos this part is *not* a
project that builds - it is a project that must *not* build.

Updating pins: refresh versions and hashes
------------------------------------------

Pins freeze both the version and the checksum of every dependency, which is what makes a
build reproducible and resistant to a swapped artifact - but it also means a pinned
project never picks up a newer version on its own. To deliberately refresh the pins, run
`pin` with `-Djenesis.dependency.pin=ignore`:

    java -Djenesis.dependency.pin=ignore build/jenesis/Make.java pin

`ignore` drops every existing Jenesis pin: versions float to the latest the repository
offers, and the recorded checksums are not consulted. `pin` then re-resolves that fresh
closure and rewrites each `pom.xml` (or `module-info.java`) with the new versions and
freshly computed `SHA-256` checksums - leaving a normal pinned project again, now tracking
the latest reviewed versions.

Run against the pinning modules, that same step would **heal** `tampered`: `ignore` skips
its deliberately-wrong all-zeros `SHA-256`, `pin` rehashes the real artifact, and the wrong
pin is replaced with the correct one - so the module that fails today would build. (The
demo ships it un-run, so the supply-chain check above still has something to catch.)

That healing power is exactly why the operation is dangerous in the wrong hands: it
re-blesses whatever the repository currently serves, so a *swapped* artifact would be
written in as an accepted pin just the same. So because `ignore` bypasses checksum
verification while it resolves - it is the step that *establishes* trust rather than
enforcing it - run it only on a **trusted machine** against a **trusted repository**.
Review the resulting diff, commit it, and every subsequent build enforces the new pins
against the artifacts you just vetted.

Note that `ignore` leaves every coordinate unpinned as it resolves, so under the default
scope it is also the run where **every** signature is checked - the dangerous operation is
the one that verifies the most.

POMs are not pinned, and why strict pinning matters
---------------------------------------------------

Only the resolved **artifacts** (the jars) carry a checksum; the `pom.xml` files read
during resolution are not pinned. Some servers serve POMs as text and may apply minor
transformations - line-ending or whitespace normalization - that would change a byte
checksum without changing the dependency, so pinning them would produce spurious
mismatches.

That leaves one gap: a tampered POM could try to introduce a dependency the jar checksums
do not cover. **Strict pinning closes it.** Any dependency a POM newly adds - or changes -
arrives as a coordinate with no pin, which strict pinning rejects, so a manipulated POM
cannot quietly pull in an unverified artifact. For that reason, enabling strict pinning is
recommended for builds in unsecured environments (a local machine) and for releases (CI),
where the resolved set should never drift from the one you reviewed.

What a signature does not prove
-------------------------------

Verification runs during `pin`, and `pin` runs after a full build, because a dependency
can be introduced by any step and the closure is only complete at the end. Tools resolved
for the build have therefore already run by the time their signatures are checked. Run
`pin` on a clean checkout when that matters; `-Djenesis.project.docker=true` gives you the
isolated environment for it.

A valid signature also proves only that the holder of a key asserted these bytes. It does
not prove the artifact is benign, that it matches its published source, or that the key was
not stolen. It closes one specific gap: that the artifact you first accepted is the one its
author released.
