Supply-chain security demo
==========================

Three questions about a dependency, and what answers each:

| Question | Answered by |
|----------|-------------|
| May we use a dependency we cannot verify at all? | **strict pinning** |
| Are these the exact bytes we vetted? | **a pin checksum** |
| Were the bytes we vetted the ones upstream produced? | **`@jenesis.signature`**, naming a **key** or an **identity** |

The first two are pinning's halves. The third is the one no hash can reach: a checksum is
taken from whatever the repository served, so a swapped artifact is written in as an
accepted pin just the same. It has two answers here, because there are two ways to say who
produced an artifact: an OpenPGP key somebody keeps, and a Sigstore identity nobody keeps a
key for at all. This demo proves all of it by getting each wrong on purpose.

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
    [ok]      expired: what the key signed before it lapsed is still accepted
    [blocked] expired: the same signature under expiry measured against today
    [ok]      attested: a dependency verified against the identity its declaration names
    [blocked] forked: the same bundle, declared to come from a different repository
    [ok]      attested: under strict, the POM carries a bundle from that identity too
    [blocked] named: the same build against a trust root that vouches for nothing

The key half is self-contained and reaches no network, and **nothing binary is
committed**: `Demo.java` builds a byte-reproducible jar, generates a throwaway key in a
gpg home under `target/`, signs both the jar and its POM with it, and publishes all four
files to a `file:` Maven repository - then builds three standalone projects against it. If
gpg is not installed the run prints `[skipped]` and stops after the pinning half, because
there is nothing the remaining six cases could verify.

Pinning: whether, and which bytes
---------------------------------

`Demo.java` builds one project at a time, in process. Both get one guarantee wrong:

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

Every project here is **modular**, so each declaration is a javadoc tag on
`module-info.java` beside the `@jenesis.pin` lines, exactly as `@jenesis.bom` is. A
`pom.xml` has no equivalent form: a Maven project states its BOM imports in
`<dependencyManagement>` and has no place for a key or an identity, which is why there is
no `pom.xml` in this demo at all.

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

`-Djenesis.print.signatures` names each coordinate that was checked with the key that signed it, and each one
no declaration covers - under `declared` that second list is exactly what `strict` would refuse, so it is how
you find out what to declare before switching. A coordinate accepted only because its key expired after it
signed is marked `[EXPIRED]` rather than `[VERIFIED]`, with the signing and expiry dates, so the relaxation is
visible rather than silent.

## An expired signing key

A key expires; the release it signed years earlier does not change. Since the expiry lives in a self-signature
rather than in the fingerprint, an `@jenesis.signature` line survives an expiry extension untouched - but where
no extension is published, the signature is still the one the key made while it was trusted.
`jenesis.openpgp.expiry` says how much that counts for:

| Value     | An expired signing key                                                     |
|-----------|-----------------------------------------------------------------------------|
| `ignored` | is accepted, whenever it signed                                             |
| `signing` | is accepted for what it signed before it expired; the default               |
| `current` | is always rejected, however old the signature                               |

The two `expired` rows above are the same artifact and the same signature, read once under the default and
once under `current`. The key is given seconds to live and the demo waits for it to lapse, because the
verifier reads the real clock: `gpgv` has no option to pretend otherwise.

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

When verification is switched on for an OpenPGP declaration - and only then - it forks
`gpg`, which must then be installed and on the `PATH`. A coordinate declared by a Sigstore
identity instead, which [`sigstore`](../demo-52-sigstore/README.md) covers, forks nothing:
that bundle carries its own certificate and log entry and is checked in process. A build that has not enabled it needs none of this. Forking
rather than linking is deliberate: a
Java OpenPGP library would have to be resolved from a repository - the very thing being
verified - and a verifier you downloaded on trust verifies nothing. It is also why a
hardened machine for writing pins is one where the JDK and gpg are already present and
vetted, rather than one that fetches them on the way. `jenesis.print.gpg` shows each
invocation and `jenesis.openpgp.command` names a different binary. A broken invocation fails loudly
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

Identities: signing without keeping a key
-----------------------------------------

Everything above rests on somebody holding a private key for years. Sigstore answers the
same question without one: the signer authenticates to an identity provider, a certificate
authority issues a certificate that is valid for **ten minutes** and names that identity,
the signature goes into a public append-only log, and the private key is discarded. What a
consumer verifies afterwards is an identity and a log entry rather than a key somebody
kept.

`@jenesis.signature` therefore takes a second form, and `attested/sources/module-info.java`
carries it beside an ordinary pin:

    @jenesis.alias protobuf.specs dev.sigstore/protobuf-specs
    @jenesis.pin dev.sigstore/protobuf-specs 0.5.2 SHA-256/e2368fd2...
    @jenesis.signature Sigstore/github.com/sigstore/protobuf-specs dev.sigstore/*

Read against the certificate that signed that release, the declaration is:

    Sigstore / github.com / sigstore / protobuf-specs
       |           |           |           `- repository
       |           |           `- owner
       |           `- issuer token.actions.githubusercontent.com
       `- material comes from the bundle beside the artifact, nothing is fetched to check it

    accepts  https://github.com/sigstore/protobuf-specs/.github/workflows/java-release.yml@refs/tags/release/java/v0.5.2
                              ~~~~~~~~~~~~~~~~~~~~~~~~~

**The path is a prefix, narrowing by segment.** `Sigstore/github.com/sigstore` covers every
repository of an owner, the line above covers one repository, and a workflow file may
follow it to cover one workflow. A prefix ends at a `/` or an `@`, so `protobuf-spec` never
covers `protobuf-specs`.

**The ref is never written.** The identity ends in `@refs/tags/release/java/v0.5.2`, and
that is the one part that moves with every release. Stopping before it is what lets a
single line cover every future version, the way a fingerprint does.

**The host names the issuer too.** It is the host itself unless `jenesis.sigstore.issuers`
names another, which today it does only for
`github.com=token.actions.githubusercontent.com`. GitLab and anything self-hosted need no
entry, because `gitlab.com` issues its own identities.

`forked/` declares `Sigstore/github.com/acme/protobuf-specs` for the same dependency. Its
bundle verifies perfectly and its log entry is real, so only the comparison against the
declaration catches a release that came from somewhere else - exactly what `rotated/` shows
for a key, and what no checksum can see.

Why a certificate that expired still verifies
----------------------------------------------

The certificate in that bundle was valid for ten minutes on one day in August. It is
expired now, and that is the design rather than a lapse. Verification does not ask whether
the certificate is valid today; it asks whether it was valid at the moment the transparency
log recorded the signature. A ten-minute certificate plus an unforgeable timestamp is what
replaces a long-lived key: there is nothing left to steal after the signature is made, and
no key to revoke, rotate or outlive - which is also why this half needs no `gpg`, no
keyring and no key server. The check is JDK cryptography in process, over a bundle the
repository publishes beside the artifact.

Where the trust root comes from
-------------------------------

A bundle carries its own certificate and log entry, but something has to say which
certificate authority and which log are the real ones. **The tool carries that trust root
as source**: the published root of the public Sigstore instance, vendored into every
project that vendors Jenesis and reviewed in the same diff. So a project that adds a
declaration needs nothing else, and no build downloads a trust root.

`jenesis.sigstore.uri` names another, and is the only way another is read - for a private
Sigstore instance, or a root that has rotated since this one was vendored. The last
`[blocked]` line is that: the same build and the same declaration, against a trust root
that vouches for nothing.

Unlike the key half, this one **needs the network**: a bundle exists only once a
certificate authority has seen an identity and a public log has recorded the entry, so it
cannot be manufactured here the way a throwaway key can. The demo verifies a published
release, `dev.sigstore:protobuf-specs:0.5.2`, and prints `[skipped]` for this half if it
cannot be reached.

Two answers, and when each applies
-----------------------------------

|                         | OpenPGP | Sigstore |
|-------------------------|---------|----------|
| What a project declares | a key fingerprint | an identity prefix, and the issuer that authenticated it |
| What the signer keeps   | a private key, for years | nothing, after ten minutes |
| What a rotation costs   | a new fingerprint in the diff | nothing, until the workflow or repository moves |
| Verifier                | forked `gpgv` | in process, `java.security` alone |
| Trust material          | keys fetched by fingerprint | a trust root the tool carries |
| Coverage on Maven Central | almost everything | a small minority |

Both forms may cover one coordinate, and each is then verified against whatever that
coordinate publishes, so a project migrating from one to the other declares both and
neither is weakened. That last row is what decides how this is used today: of nine widely
used artifacts checked while writing this demo, exactly one carried a `.sigstore.json` and
all nine carried a `.asc`.

Layout
------

    demo-46-supply-chain-security
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds and signs the fixture, asserts all fifteen outcomes
    |-- unpinned/                commons-lang3 with a version but no checksum
    |-- tampered/                commons-lang3 pinned to a deliberately wrong SHA-256
    |-- signed/                  declares no key, then the right one
    |-- rotated/                 declares a key that did not sign the artifact
    |-- vendored/                the same wrong key, taken from a local key list
    |-- attested/                declares the identity that really signed a published release
    `-- forked/                  declares another repository for that same release

Every project is a standalone modular project; there is no `pom.xml` anywhere. The jar, its
POM, both signatures, the keys and the `file:` repository are built under `target/` at run
time, so nothing binary is committed.

Several projects are wrong on purpose, so unlike the other demos this one is not only a
project that builds - it is partly a set of projects that must *not* build.

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
written in as an accepted pin just the same. Note that `ignore` on its own verifies
nothing - `jenesis.dependency.signature` is `none` until you set it, and declarations alone
never switch it on, so the refresh needs both flags. This is the one build a pin cannot
protect:
every other build checks the bytes against a pin your project already reviewed, but the run
that *writes* the pin has nothing to check against, so whatever arrives becomes the
definition of correct. A signature is what still has something to say there, which is why
the refresh is the run to pair with `-Djenesis.dependency.signature=strict` rather than
hardening every ordinary build. So because `ignore` bypasses checksum
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
