/**
 * A dependency named by version and nothing else. It builds by default, and strict pinning refuses
 * it: there is no checksum to verify the download against, which is what a hardened build rejects.
 *
 * @jenesis.release 25
 * @jenesis.alias org.apache.commons.lang3 org.apache.commons/commons-lang3
 * @jenesis.pin org.apache.commons/commons-lang3 3.14.0
 */
module demo.unpinned {
    requires org.apache.commons.lang3;

    exports sample;
}
