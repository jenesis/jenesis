/**
 * The same dependency, declared to come from a different repository. Its bundle verifies perfectly
 * and its log entry is real, so only the comparison against this declaration catches the swap.
 *
 * @jenesis.release 25
 * @jenesis.alias protobuf.specs dev.sigstore/protobuf-specs
 * @jenesis.signature Sigstore/github.com/acme/protobuf-specs dev.sigstore/*
 * @jenesis.pin dev.sigstore/protobuf-specs 0.5.2 SHA-256/e2368fd262a9bec078dee8868fc82682cb648ec21906f8b399e348977f3e3a3e
 */
module demo.forked {
    requires protobuf.specs;

    exports sample;
}
