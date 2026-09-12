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
    [ok]      signed: an undeclared coordinate is left alone under declared
    [blocked] signed: an undeclared coordinate under strict verification
    [ok]      signed: the artifact and its POM both verify against the declared key
    [blocked] rotated: the declared key is not the one that signed the artifact
    [blocked] vendored: a local key list is consulted like an inline declaration
    [ok]      rotated: the same contradiction is never looked for by default

The signature half is self-contained and reaches no network, and **nothing binary is
committed**: `Demo.java` builds a byte-reproducible jar, generates a throwaway key in a
gpg home under `target/`, signs both the jar and its POM with it, and publishes all four
files to a `file:` Maven repository - then builds three standalone projects against it. If
gpg is not installed the run prints `[skipped]` and stops after the pinning half, because
there is nothing the remaining six cases could verify.

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

`@jenesis.signature` names the OpenPGP key that signs a coordinate's artifacts. The
fingerprint comes first, because one key normally signs many artifacts, and the
coordinates it covers follow - using the same token grammar as every other `@jenesis`
tag, so one form serves a Maven coordinate and a module repository alike:

    @jenesis.signature OpenPGP/360FF3E0DF4B835B5E983E9E40278877A3DE7949 org.example/lib some.module

A Maven token may end in `/*` to cover **every artifact of one groupId**, which is what
the two projects here use:

    @jenesis.signature OpenPGP/360FF3E0DF4B835B5E983E9E40278877A3DE7949 org.example/*

**Nothing writes these lines.** A fingerprint is obtained out of band, checked against the
upstream project's published KEYS, and added by hand - that judgement is the thing the
whole mechanism rests on, and a tool that filled the line in from whatever it downloaded
would be recording its own guess. Widening trust across a whole group with `/*` is the
same kind of decision, made once and visible in the diff.

These are **modular** projects, so the declaration is a javadoc tag on `module-info.java`
beside the `@jenesis.pin` lines, exactly as `@jenesis.bom` is. A `pom.xml` has no
equivalent form: a Maven project states its BOM imports in `<dependencyManagement>` and
has no place for a key, so the signature half of this demo is modular while the pinning
half is Maven.

One vetted list can serve many modules. A declaration naming a lone
`signature-<name>.properties` reads its keys from a local file instead, the shape
`@jenesis.bom` already uses for a local `pin-<name>.properties`:

    @jenesis.signature signature-vendor.properties

```properties
# build.jenesis/signature-vendor.properties
OpenPGP/FF6E2C001948C5F2F38B0CC385911F425EC61B51 = org.apiguardian/* org.junit.jupiter/*
OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 = org.assertj/*
```

The fingerprint is the properties key rather than the coordinate, so one coordinate can
sit under two keys through a rotation. The file is found in `jenesis.project.signatures`,
which defaults to the configuration folders. A list is **only ever read from disk**: there
is no form that resolves one from a repository, because a list you had to download would
itself need verifying, which is the problem the mechanism exists to solve.

The line carries **no version**: one key signs every release it signs, so the declaration
stays put across version bumps. That is the whole economy of this approach - vetting a key
once covers every future release from that key, where a hash covers exactly one file and
every version bump is a fresh, unvetted trust event.

When verification happens
-------------------------

Right after a dependency is downloaded, inside the `Dependencies` module and as its own
step. `jenesis.dependency.signature` selects how much it checks, and defaults to `none`:

| Value      | Verifies                                                                |
|------------|-------------------------------------------------------------------------|
| `none`     | nothing; the default, so an ordinary build needs no gpg at all          |
| `declared` | every coordinate an `@jenesis.signature` line covers                     |
| `strict`   | the same, and additionally rejects a coordinate no line covers, or whose artifact or POM publishes no signature |

Because it is a separate step, switching the property on does not re-download anything:
the `artifacts` step's output is unchanged, and only `signatures` runs. The fetched `.asc`
files are cached beside the jars they verify, like any other artifact.

Verification is deliberately **not** part of `pin`. `pin` pins: it records the versions
and checksums a resolution produced, and it never adds, removes or reads a signature line.
The two are separate answers to separate questions, and running them together would make
the dangerous operation - `pin` with `-Djenesis.dependency.pin=ignore`, which re-blesses
whatever the repository currently serves - look safer than it is. Run that operation *with*
`-Djenesis.dependency.signature=strict` and the bytes it is about to bless are the ones
their author released.

The three projects
------------------

- **`signed`** declares **nothing** for the coordinate. Under `declared` it builds
  untouched: that mode verifies what is declared and claims nothing about the rest. Under
  `strict` it is blocked, because an external coordinate nobody vouches for is exactly
  what strict refuses. The demo then writes the real fingerprint into `module-info.java`
  and `strict` accepts it - the artifact and its POM both, against the same key.
- **`rotated`** declares a different fingerprint. The signature on the artifact is
  perfectly valid - gpg is happy with it - so **only** the comparison against the
  declaration catches the substitution. That is precisely what naming the key buys you,
  and what a checksum alone cannot see.
- **`vendored`** makes the same mistake through a local key list rather than an inline
  line, and is blocked identically. The list is the only difference: where the trust is
  written down, not how much of it is checked.

The failure names both keys and the fix:

    main/maven/org.example/lib 1.0: signed by OpenPGP/360FF3E0... but only
    OpenPGP/00000000...DEADBEEF is declared for it; add OpenPGP/360FF3E0... to a
    @jenesis.signature line to accept a key rotation

Accepting a genuine key rotation is therefore an **addition**: list the new key too, and
both are accepted until you drop the old one. That lands in the diff where a reviewer
sees it.

The last line of the run is the one worth reading twice. With the property unset, the
`rotated` project **builds**, contradiction and all. Verification is opt-in, and a
declaration alone does not switch it on: set the property in `jenesis.properties` the way
any other project default is set, or pass it on the runs that matter - a dependency
update, and CI.

POMs are verified too
---------------------

A coordinate's **POM is verified with its artifact**, and must carry the same signer. POMs
are read during resolution but never pinned, because some servers re-serialise them and a
byte checksum would then mismatch for no reason. A signature closes that gap directly
rather than relying on strict pinning to catch what a tampered POM adds - at the cost that
a repository which re-serialises POMs invalidates their signatures, so resolve from one
that serves the published bytes. The demo signs both the jar and the POM, so it forks gpg
twice for the one coordinate.

Verification is an ordinary forked tool: `jenesis.print.gpg` shows each invocation and
`jenesis.signature.command` names a different binary. A broken invocation fails loudly
with the command to reproduce, rather than passing for "no signature found".

Where the keys come from
------------------------

Jenesis records a fingerprint; gpg supplies the key material from the local keyring, which
`GNUPGHOME` and `gpg.conf` configure as they do for any other gpg use. An unknown key is
reported as `NO_PUBKEY` rather than retrieved unattended - obtaining a key and checking it
against the project's published KEYS file is the human judgement this whole mechanism
rests on, and it should not happen behind your back. For the same reason there is no form
that reads a key list from a repository: a list that had to be downloaded would itself
need verifying.

The key this demo uses is generated fresh on every run and thrown away with `target/`. It
signs nothing else and is worth no trust whatsoever - which is also why none of it is
committed: a repository of sources should not carry jars, signatures or keys.

Layout
------

    demo-46-supply-chain-security
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds and signs the fixture, asserts all nine outcomes
    |-- pom.xml                  aggregator over the two pinning modules
    |-- unpinned/pom.xml         commons-lang3 with a version but no checksum
    |-- tampered/pom.xml         commons-lang3 pinned to a deliberately wrong SHA-256
    |-- signed/                  standalone modular project declaring no key
    |-- rotated/                 standalone modular project declaring the wrong key
    `-- vendored/                the same wrong key, taken from a local key list

The jar, its POM, both signatures, the key and the `file:` repository are all built under
`target/` at run time, so nothing binary is committed.

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
enforcing it - run it only on a **trusted machine** against a **trusted repository**, and
pair it with `-Djenesis.dependency.signature=strict`, which is the one check that still
has something to say when the checksums are being rewritten. Review the resulting diff,
commit it, and every subsequent build enforces the new pins against the artifacts you just
vetted.

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

Verification rides along with every resolution, because it is a step of the `Dependencies`
module rather than something wired beside it: a module's own closure, and equally a linter,
a formatter, an alternative compiler or the test launcher that a build module resolves for
itself. A valid signature proves only that the holder of a key asserted these bytes. It does not prove the artifact is benign, that it matches its published source, or
that the key was not stolen. It closes one specific gap: that the artifact you accepted is
the one its author released.
