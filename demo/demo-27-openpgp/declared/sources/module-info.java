/**
 * The key that signs a dependency, declared by its fingerprint. The fingerprint is obtained out of
 * band and checked against what the project publishes; nothing here writes it for you.
 *
 * @jenesis.release 25
 * @jenesis.alias org.assertj.core org.assertj/assertj-core
 * @jenesis.pin net.bytebuddy/byte-buddy 1.15.11 SHA-256/fa08998aae1e7bdae83bde0712c50e8444d71c0e0c196bb2247ade8d4ad0eb90
 * @jenesis.pin org.assertj/assertj-core 3.27.0 SHA-256/0b4d14008475fb362c2db090bc89c41b864d870216ccf8e8188fb60eb112ad68
 * @jenesis.signature OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 org.assertj/*
 */
module demo.declared {
    requires org.assertj.core;

    exports sample;
}
