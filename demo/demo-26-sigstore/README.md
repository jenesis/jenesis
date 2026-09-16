Sigstore demo
=============

The same question as the [`openpgp`](../demo-25-openpgp/README.md) demo - who produced these bytes -
answered without anyone holding a key. The signer authenticates to an identity provider, a
certificate authority issues a certificate that is valid for **ten minutes** and names that
identity, the signature is recorded in a public append-only log, and the private key is discarded.
What a build verifies afterwards is an identity and a log entry.

This demo verifies a real dependency, `dev.sigstore:protobuf-specs`, against the workflow that
really released it.

Run it
------

    java build/Demo.java

              [VERIFIED]  main/maven/dev.sigstore/protobuf-specs 0.5.2 Sigstore/github.com/sigstore/protobuf-specs as https://github.com/sigstore/protobuf-specs/.github/workflows/java-release.yml@refs/tags/release/java/v0.5.2, recorded 2026-08-21T20:08:54Z
    [ok]      attested: a dependency verified against the identity its declaration names
    [blocked] forked: the same bundle, declared to come from a different repository
    [ok]      attested: under strict, the POM carries a bundle from that identity too
    [blocked] named: the same build against a trust root that vouches for nothing

Nothing is installed and nothing is configured: no key is fetched, no keyring assembled and no `gpg`
forked. A coordinate covered only by an identity is checked in process, with JDK cryptography alone.

Declaring an identity
---------------------

`attested/sources/module-info.java` carries it beside an ordinary pin:

```java
/**
 * @jenesis.alias protobuf.specs dev.sigstore/protobuf-specs
 * @jenesis.pin dev.sigstore/protobuf-specs 0.5.2 SHA-256/e2368fd2...
 * @jenesis.signature Sigstore/github.com/sigstore/protobuf-specs dev.sigstore/*
 */
```

Read against the certificate that signed that release:

    Sigstore / github.com / sigstore / protobuf-specs
       |           |           |           `- repository
       |           |           `- owner
       |           `- issuer token.actions.githubusercontent.com
       `- the bundle beside the artifact carries the material; nothing is fetched to check it

    accepts  https://github.com/sigstore/protobuf-specs/.github/workflows/java-release.yml@refs/tags/release/java/v0.5.2

**The path is a prefix, and it narrows a segment at a time.** `Sigstore/github.com/sigstore` covers
every repository of one owner, the line above covers one repository, and a workflow file may follow
to cover a single workflow. A prefix ends at a `/` or an `@`, so `protobuf-spec` never covers
`protobuf-specs`.

**The tag is never written.** The identity ends in `@refs/tags/release/java/v0.5.2`, and that is the
part that moves with every version. Stopping before it is what lets one line cover every future
release, the way a fingerprint does.

**The host names the issuer too.** It is the host itself unless `-Djenesis.sigstore.issuers` names
another, which today it does only for `github.com=token.actions.githubusercontent.com`. GitLab and
anything self-managed need no entry.

`forked/` declares `Sigstore/github.com/acme/protobuf-specs` for the same dependency. Its bundle
verifies perfectly and its log entry is genuine, so only the comparison against the declaration
catches a release built somewhere else - the `rotated` case of the key demo, for an identity.

What is in a bundle
-------------------

A `.sigstore.json` sits beside the artifact the way a `.asc` does, and carries four things:

| Part | Checked against |
|---|---|
| the signature, and the digest it was made over | the artifact's own bytes |
| the signing certificate | the certificate authority in the trust root, **as of the time the log recorded** |
| the identity in that certificate | what the project declares it expects |
| the log entry, its inclusion proof and the log's checkpoint | the log's public key, named in the trust root |

The certificate is expired by the time anyone reads it, which is the design rather than a lapse:
verification asks whether it was valid at the moment the log recorded the signature, not whether it
is valid today. A ten-minute certificate plus a countersigned timestamp is what replaces a key kept
for years - there is nothing left to steal once the signature is made.

Under `strict` a coordinate's **POM must carry a bundle from the same identity**, which closes the
same gap the key form closes: POMs are read during resolution but never pinned.

Where the trust root comes from
-------------------------------

Something has to say which certificate authority and which log are the real ones. The tool **carries
that trust root as source**: the published root of the public Sigstore instance, vendored with the
rest of Jenesis and reviewed in the same diff. So a project that adds a declaration needs nothing
else, and no build downloads a trust root.

`-Djenesis.sigstore.uri` names another, and is the only way another is read - for a private Sigstore
instance, or a root that has rotated since. The last `[blocked]` line is that: the same build and
the same declaration, against a trust root that vouches for nothing.

Two answers, and when each applies
----------------------------------

|                         | OpenPGP | Sigstore |
|---|---|---|
| What a project declares | a key fingerprint | an identity prefix, and the issuer that authenticated it |
| What the signer keeps | a private key, for years | nothing, after ten minutes |
| Verifier | forked `gpgv` | in process, `java.security` alone |
| Trust material | keys fetched by fingerprint | a trust root the tool carries |
| Coverage on Maven Central | almost everything | a small minority |

Both forms may cover one coordinate, and each is verified against whatever that coordinate
publishes, so a project moving from one to the other declares both and neither is weakened. What a
repository publishes decides which applies: a detached `.asc` is near-universal, while bundles are
still the exception, so an identity is an additional answer where one exists rather than a
replacement.

Layout
------

    demo-26-sigstore
    |-- build/jenesis            symlink to ../../../sources/build/jenesis
    |-- build/Demo.java          builds each project and asserts which of them may not build
    |-- attested/                the identity that really released the dependency
    `-- forked/                  another repository declared for that same release

This half needs the network, because a bundle exists only once a certificate authority has seen an
identity and a public log has recorded the entry - it cannot be manufactured locally the way a
throwaway key can.
