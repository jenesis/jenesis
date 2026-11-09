/**
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin maven/net.bytebuddy/byte-buddy-parent/pom 1.18.14-jdk5 SHA-256/fd4e64fd0ceef0315d4ef53d8ce56246a510cf21b458371c222350d9988565da
 * @jenesis.pin maven/net.bytebuddy/byte-buddy/pom 1.18.14-jdk5 SHA-256/84b074b9a485a77a3526d52910a9c073e87113820f5d4e4a98a256aa4d6c2d7f
 * @jenesis.pin net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin net.bytebuddy/byte-buddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin plugin.enhance/module/build.jenesis 0.18.2 SHA-256/18494e5406c1a6da0718318cc4037b98ba2c28ce1b1b250580e1b33afd6db7a9
 * @jenesis.pin plugin.enhance/module/net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 */
module demo.bytebuddy {
    requires static net.bytebuddy;
    exports sample;
}
