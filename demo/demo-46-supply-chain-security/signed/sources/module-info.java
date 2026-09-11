/**
 * A modular project whose dependency carries no signature declaration yet. Running
 * {@code pin} verifies the detached signature published beside the artifact and
 * records the key it found as a {@code @jenesis.signature} tag below, for review.
 *
 * @jenesis.release 25
 * @jenesis.alias org.example.lib org.example/lib
 * @jenesis.pin org.example/lib 1.0 SHA-256/6ff62b35d2ffb3dfb87fa8ee1875f5469fa84832238767719e347188ea603212
 */
module demo.signed {
    requires org.example.lib;

    exports sample;
}
