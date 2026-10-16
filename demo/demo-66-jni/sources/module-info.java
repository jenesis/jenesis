/**
 * An application that adds two numbers in C, through a native method whose
 * library a plugin compiles into the module's jar.
 *
 * @jenesis.release 25
 * @jenesis.main sample.adder.Main
 * @jenesis.native demo.adder
 * @jenesis.pin jni/module/build.jenesis 0.16.0 SHA-256/6c7ca48b61d71ca04a929bfc937f49ca5ba899c083b0a011fc29115d44b522c4
 */
module demo.adder {
    exports sample.adder;
}
