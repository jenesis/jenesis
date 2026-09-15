/**
 * A dependency pinned to a version and the checksum of the bytes that version served.
 *
 * @jenesis.release 25
 * @jenesis.alias org.apache.commons.lang3 org.apache.commons/commons-lang3
 * @jenesis.pin org.apache.commons/commons-lang3 3.20.0 SHA-256/69e5c9fa35da7a51a5fd2099dfe56a2d8d32cf233e2f6d770e796146440263f4
 */
module demo.pinned {
    requires org.apache.commons.lang3;

    exports sample;
}
