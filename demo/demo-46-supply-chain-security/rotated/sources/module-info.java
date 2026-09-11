/**
 * A modular project declaring a key that did not sign its dependency. The signature
 * on the artifact is perfectly valid, so only the comparison against the declaration
 * below catches the substitution.
 *
 * @jenesis.release 25
 * @jenesis.alias org.example.lib org.example/lib
 * @jenesis.pin org.example/lib 1.0 SHA-256/6ff62b35d2ffb3dfb87fa8ee1875f5469fa84832238767719e347188ea603212
 * @jenesis.signature OpenPGP/00000000000000000000000000000000DEADBEEF org.example/*
 */
module demo.rotated {
    requires org.example.lib;

    exports sample;
}
