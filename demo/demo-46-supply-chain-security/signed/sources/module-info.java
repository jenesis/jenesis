/**
 * A modular project whose dependency is signed but for which no key is declared. Under
 * {@code declared} it builds untouched, under {@code strict} it is rejected, and once the
 * demo writes the signer's fingerprint in below, {@code strict} accepts it.
 *
 * @jenesis.release 25
 * @jenesis.alias org.example.lib org.example/lib
 * @jenesis.pin org.example/lib 1.0 SHA-256/6ff62b35d2ffb3dfb87fa8ee1875f5469fa84832238767719e347188ea603212
 */
module demo.signed {
    requires org.example.lib;

    exports sample;
}
