/**
 * An application that adds two numbers in C, through a native method whose
 * library a plugin compiles into the module's jar.
 *
 * @jenesis.release 25
 * @jenesis.main sample.adder.Main
 * @jenesis.native demo.adder
 * @jenesis.pin plugin.jni/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 */
module demo.adder {
    exports sample.adder;
}
