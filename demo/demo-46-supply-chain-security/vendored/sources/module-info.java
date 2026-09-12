/**
 * A modular project that takes its trusted keys from a local list rather than declaring
 * them inline, the way {@code @jenesis.bom} names a local pin file. The list is read from
 * the configuration folder; one is never resolved from a repository, because a list that
 * had to be downloaded would itself need verifying.
 *
 * @jenesis.release 25
 * @jenesis.alias org.example.lib org.example/lib
 * @jenesis.pin org.example/lib 1.0 SHA-256/6ff62b35d2ffb3dfb87fa8ee1875f5469fa84832238767719e347188ea603212
 * @jenesis.signature signature-vendor.properties
 */
module demo.vendored {
    requires org.example.lib;

    exports sample;
}
