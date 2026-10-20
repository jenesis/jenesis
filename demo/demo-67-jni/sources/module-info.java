/**
 * An application that adds two numbers in C, through a native method whose
 * library a plugin compiles into the module's jar.
 *
 * @jenesis.release 25
 * @jenesis.main sample.adder.Main
 * @jenesis.native demo.adder
 * @jenesis.pin plugin.jni/module/build.jenesis 0.17.0 SHA-256/0dfd1715ecb35e8dd398b5fc9ceb49c120aa59e3d6063606882717f41d84b284
 */
module demo.adder {
    exports sample.adder;
}
