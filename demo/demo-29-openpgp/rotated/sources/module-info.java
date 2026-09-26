/**
 * A different key, declared for the same dependency. The signature upstream published is perfectly
 * band and checked against what the project publishes; nothing here writes it for you.
 *
 * @jenesis.release 25
 * @jenesis.alias org.assertj.core org.assertj/assertj-core
 * @jenesis.pin net.bytebuddy/byte-buddy 1.15.11 SHA-256/fa08998aae1e7bdae83bde0712c50e8444d71c0e0c196bb2247ade8d4ad0eb90
 * @jenesis.pin org.assertj/assertj-core 3.27.0 SHA-256/0b4d14008475fb362c2db090bc89c41b864d870216ccf8e8188fb60eb112ad68
 * @jenesis.signature OpenPGP/00000000000000000000000000000000DEADBEEF org.assertj/*
 */
module demo.rotated {
    requires org.assertj.core;

    exports sample;
}
