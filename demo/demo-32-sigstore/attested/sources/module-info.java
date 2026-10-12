/**
 * A project whose dependency is vouched for by the identity that signed it rather than by a key.
 * The declaration names a repository, not a release: the identity in the certificate ends in the
 * tag it was built from, and that part moves with every version.
 *
 * @jenesis.release 25
 * @jenesis.alias protobuf.specs dev.sigstore/protobuf-specs
 * @jenesis.signature Sigstore/github.com/sigstore/protobuf-specs dev.sigstore/*
 * @jenesis.pin dev.sigstore/protobuf-specs 0.5.2 SHA-256/e2368fd262a9bec078dee8868fc82682cb648ec21906f8b399e348977f3e3a3e
 */
module demo.attested {
    requires protobuf.specs;

    exports sample;
}
