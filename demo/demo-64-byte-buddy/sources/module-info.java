/**
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin net.bytebuddy/byte-buddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin plugin.enhance/module/build.jenesis 0.17.0 SHA-256/c73dbb0a30f1bae340e0c6adfa433c76980528cf45a7a8836119b038e710886f
 * @jenesis.pin plugin.enhance/module/net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 */
module demo.bytebuddy {
    requires static net.bytebuddy;
    exports sample;
}
