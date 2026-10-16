/**
 * An application that adds two numbers in C, through a native method whose
 * library a plugin compiles into the module's jar.
 *
 * @jenesis.release 25
 * @jenesis.main sample.adder.Main
 * @jenesis.native demo.adder
 * @jenesis.pin jni/module/build.jenesis 0.16.0 SHA-256/bf8d90a8e3251fcb2519e11793ab87a68f37419f12a6b3275f9dd1d263149c98
 */
module demo.adder {
    exports sample.adder;
}
