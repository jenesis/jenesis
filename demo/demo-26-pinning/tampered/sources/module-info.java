/**
 * The same dependency, pinned to a checksum it does not have. Every download is compared against
 * its pin, so this one fails without strict pinning being asked for at all.
 *
 * @jenesis.release 25
 * @jenesis.alias org.apache.commons.lang3 org.apache.commons/commons-lang3
 * @jenesis.pin org.apache.commons/commons-lang3 3.14.0 SHA-256/0000000000000000000000000000000000000000000000000000000000000000
 */
module demo.tampered {
    requires org.apache.commons.lang3;

    exports sample;
}
