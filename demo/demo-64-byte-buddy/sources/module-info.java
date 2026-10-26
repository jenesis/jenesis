/**
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin maven/net.bytebuddy/byte-buddy-parent/pom 1.18.14-jdk5 SHA-256/fd4e64fd0ceef0315d4ef53d8ce56246a510cf21b458371c222350d9988565da
 * @jenesis.pin maven/net.bytebuddy/byte-buddy/pom 1.18.14-jdk5 SHA-256/84b074b9a485a77a3526d52910a9c073e87113820f5d4e4a98a256aa4d6c2d7f
 * @jenesis.pin net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin net.bytebuddy/byte-buddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin plugin.enhance/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin plugin.enhance/module/net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 */
module demo.bytebuddy {
    requires static net.bytebuddy;
    exports sample;
}
