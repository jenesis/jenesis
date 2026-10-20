/**
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin net.bytebuddy/byte-buddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin plugin.enhance/module/build.jenesis 0.17.0 SHA-256/a875ab473238e7191332698fab64db7b4a4d7a484da6beb313a4a4beb10c60fc
 * @jenesis.pin plugin.enhance/module/net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 */
module demo.bytebuddy {
    requires static net.bytebuddy;
    exports sample;
}
