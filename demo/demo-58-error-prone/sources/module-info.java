/**
 * A module compiled with Error Prone. The plugin is declared the way every
 * compiler plugin is, with `@jenesis.plugin <compiler> <coordinate>`, so it
 * resolves in the `javac` group and joins the compiler's processor path. An
 * `errorprone.properties` in the configuration folder is what turns it on and
 * carries its flags; here it promotes one of its checks to an error, so the
 * reference comparison in `Comparison` fails the build. `javac` forks so that
 * the compiler internals the plugin reads can be exported to it.
 *
 * @jenesis.release 25
 * @jenesis.main demo.errorprone.Comparison
 * @jenesis.plugin javac maven/com.google.errorprone/error_prone_core
 * @jenesis.pin javac/maven/com.github.ben-manes.caffeine/caffeine 3.0.5 SHA-256/8a9b54d3506a3b92ee46b217bcee79196b21ca6d52dc2967c686a205fb2f9c15
 * @jenesis.pin javac/maven/com.github.kevinstern/software-and-algorithms 1.0 SHA-256/61ab82439cef37343b14f53154c461619375373a56b9338e895709fb54e0864c
 * @jenesis.pin javac/maven/com.google.auto.service/auto-service-annotations 1.0.1 SHA-256/c7bec54b7b5588b5967e870341091c5691181d954cf2039f1bf0a6eeb837473b
 * @jenesis.pin javac/maven/com.google.auto.value/auto-value-annotations 1.9 SHA-256/fa5469f4c44ee598a2d8f033ab0a9dcbc6498a0c5e0c998dfa0c2adf51358044
 * @jenesis.pin javac/maven/com.google.auto/auto-common 1.2.2 SHA-256/f50b1ce8a41fad31a8a819c052f8ffa362ea0a3dbe9ef8f7c7dc9a36d4738a59
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotation 2.50.0 SHA-256/aa9b67a3aa3418b366e26594e1ae35d100ad3369cd1010ae7d4782d984ea9aaf
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotations 2.50.0 SHA-256/4667724877f1d37a689202da191e23efa7657c62eef93ccdac406eccfe5cdd0a
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_check_api 2.50.0 SHA-256/ee935a4f42ac409fb0a471affea938c3050c6887dbaadb106868936d83d8c8f1
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_core 2.50.0 SHA-256/40a88d3ea732fac3a57ae0e2b27f1729ce76cc41857bcd63d5845e79eae5f72e
 * @jenesis.pin javac/maven/com.google.googlejavaformat/google-java-format 1.35.0 SHA-256/b2a52e363d51dd86b3b38d1a8dd51538541b42ff1048769684681c676550f043
 * @jenesis.pin javac/maven/com.google.guava/failureaccess 1.0.3 SHA-256/cbfc3906b19b8f55dd7cfd6dfe0aa4532e834250d7f080bd8d211a3e246b59cb
 * @jenesis.pin javac/maven/com.google.guava/guava 33.5.0-jre SHA-256/1e301f0c52ac248b0b14fdc3d12283c77252d4d6f48521d572e7d8c4c2cc4ac7
 * @jenesis.pin javac/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
 * @jenesis.pin javac/maven/com.google.j2objc/j2objc-annotations 3.1 SHA-256/84d3a150518485f8140ea99b8a985656749629f6433c92b80c75b36aba3b099b
 * @jenesis.pin javac/maven/com.google.protobuf/protobuf-java 4.33.2 SHA-256/c5b582aa127fb62c5fc3077329d522dfb7930b4e9a625c08760b681b2ba5aab7
 * @jenesis.pin javac/maven/io.github.eisop/dataflow-errorprone 3.41.0-eisop1 SHA-256/10434fba4e53f55fa9c76904cde414b918932548c9dfc4e2d634ac05ff7a7d10
 * @jenesis.pin javac/maven/io.github.java-diff-utils/java-diff-utils 4.12 SHA-256/9990a2039778f6b4cc94790141c2868864eacee0620c6c459451121a901cd5b5
 * @jenesis.pin javac/maven/javax.inject/javax.inject 1 SHA-256/91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff
 * @jenesis.pin javac/maven/org.checkerframework/checker-qual 3.19.0 SHA-256/a827c49183f3a632277d27a0a4673686cb341507447b9d570261094bd748aa68
 * @jenesis.pin javac/maven/org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
 * @jenesis.pin javac/maven/org.pcollections/pcollections 4.0.1 SHA-256/1f82766d7c3221930854033bebff5073ea46b43f27326074bbe15d148c18bfb3
 */
module demo.errorprone {
    exports demo.errorprone;
}
