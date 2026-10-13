/**
 * @jenesis.release 25
 * @jenesis.main sample.Sample
 * @jenesis.pin net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin net.bytebuddy/byte-buddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 * @jenesis.pin plugin-enhance/module/build.jenesis 0.15.3 SHA-256/a453285c6286a13f5a7989340aaa5e34346673a5a59fda804b4b821a8732ee80
 * @jenesis.pin plugin-enhance/module/net.bytebuddy 1.18.14-jdk5 SHA-256/862359855a4d8582a08e1f1cd8961f6ddf16b67f06773f7298797ccecc5b1585
 */
module demo.bytebuddy {
    requires static net.bytebuddy;
    exports sample;
}
