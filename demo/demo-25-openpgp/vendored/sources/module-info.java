/**
 * The same key, read from a list a project vets once and shares between modules, rather than from a
 * line of its own. A list is only ever read from disk.
 *
 * @jenesis.release 25
 * @jenesis.alias org.assertj.core org.assertj/assertj-core
 * @jenesis.pin net.bytebuddy/byte-buddy 1.15.11 SHA-256/fa08998aae1e7bdae83bde0712c50e8444d71c0e0c196bb2247ade8d4ad0eb90
 * @jenesis.pin org.assertj/assertj-core 3.27.0 SHA-256/0b4d14008475fb362c2db090bc89c41b864d870216ccf8e8188fb60eb112ad68
 * @jenesis.signature signature-vendor.properties
 */
module demo.vendored {
    requires org.assertj.core;

    exports sample;
}
