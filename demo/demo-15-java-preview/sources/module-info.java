/**
 * A module that uses a preview feature of Java 25: a switch over a long whose
 * cases are primitive type patterns. Its release is written 25-preview, which
 * compiles it with the preview features of Java 25 enabled, and every run of it
 * enables them as well.
 *
 * @jenesis.release 25-preview
 * @jenesis.main sample.Sample
 */
module demo.preview {
    exports sample;
}
