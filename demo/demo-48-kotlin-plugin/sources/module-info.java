/**
 * @jenesis.release 25
 * @jenesis.plugin kotlin:tool maven/org.jetbrains.kotlin/kotlin-serialization-compiler-plugin
 * @jenesis.pin kotlin.stdlib 1.9.10 SHA-256/55e989c512b80907799f854309f3bc7782c5b3d13932442d0379d5c472711504
 * @jenesis.pin kotlin/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin kotlin/module/build.jenesis.plugin.kotlin 0.1.0 SHA-256/8ae119271454d849d712d61b0166b5879f6c6493897beaa4d76b1aa5319545c6
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-build-tools-api 2.4.0-RC2 SHA-256/ca5f829936a739cb90146359159a8ecdfe2f1a0ae21f8e2da5868b6413320a1d
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.4.0-RC2 SHA-256/923181ab0d2dc773b36d526081567bd7755f06e26d32c0f8a6537a31cf0a0229
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.4.0-RC2 SHA-256/2fc1f1ee5f7fccda091bf34473c6cee44f131ea1b7da9f600a706fdc68716dba
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.4.0-RC2 SHA-256/676934238966d037834d8285bfd7bd14c0c57f3b7ddd597f7ebd9e3c574a812f
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-serialization-compiler-plugin 2.4.0-RC2 SHA-256/1320eee4f4f4fa3318b3af69dc6838227d8533408878f749be1c2b91087c5b95
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-serialization-compiler-plugin/pom 2.4.0-RC2 SHA-256/925f8cb2d4fdba40cefc0ee7f2406ac6cae874e160e3630192a58f2924654dc3
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-stdlib 2.4.0-RC2 SHA-256/c67ed4aa99b5766e016f7c1a5d76424b67512cc8b6ca3a9f4ea97526bfee7a5e
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
 * @jenesis.pin kotlin:tool/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin kotlinx.serialization.core 1.7.3 SHA-256/f0adde45864144475385cf4aa7e0b7feb27f61fcf9472665ed98cc971b06b1eb
 * @jenesis.pin maven/org.jetbrains.kotlin/kotlin-stdlib-common/pom 1.9.10 SHA-256/7d4b70547910676b3bdfc8925a88f3b6bfb24582c9784542805544ceef490a92
 * @jenesis.pin maven/org.jetbrains.kotlin/kotlin-stdlib/pom 1.9.10 SHA-256/7e29fbf73fdf71e0679d3dee7e680fd573464fa01644a4f58ab819d2c088d3d2
 * @jenesis.pin maven/org.jetbrains.kotlinx/kotlinx-serialization-bom/pom 1.7.3 SHA-256/4226a491c5b59ce909f73b65aa98944191c8dcac3825637c6be1069e6b589a46
 * @jenesis.pin maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/pom 1.7.3 SHA-256/734f5f749208dd0bef3d98caa594cf9222afdf0fee5b6843347a8fe64e240427
 * @jenesis.pin maven/org.jetbrains/annotations/pom 13.0 SHA-256/965aeb2bedff369819bdde1bf7a0b3b89b8247dd69c88b86375d76163bb8c397
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib 1.9.10 SHA-256/55e989c512b80907799f854309f3bc7782c5b3d13932442d0379d5c472711504
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib-common 1.9.10 SHA-256/cde3341ba18a2ba262b0b7cf6c55b20c90e8d434e42c9a13e6a3f770db965a88
 * @jenesis.pin org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.7.3 SHA-256/f0adde45864144475385cf4aa7e0b7feb27f61fcf9472665ed98cc971b06b1eb
 * @jenesis.pin org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 */
module sample.kotlin.serialization {
    requires kotlin.stdlib;
    requires kotlinx.serialization.core;

    exports sample;
}
