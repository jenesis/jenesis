/**
 * An application that adds two numbers in C, through a native method whose
 * library a plugin compiles into the module's jar.
 *
 * @jenesis.release 25
 * @jenesis.main sample.adder.Main
 * @jenesis.native demo.adder
 * @jenesis.pin plugin.jni/module/build.jenesis 0.18.2
 */
module demo.adder {
    exports sample.adder;
}
