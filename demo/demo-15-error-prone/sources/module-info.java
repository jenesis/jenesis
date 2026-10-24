/**
 * A module compiled with Error Prone. The plugin is declared the way every
 * compiler plugin is, with `@jenesis.plugin <compiler> <coordinate>`, so it
 * resolves in the `javac` group and joins the compiler's processor path. The
 * `process-javac.properties` of the `errorprone` profile is what turns it on:
 * it hands `javac` the options Error Prone needs and promotes one of its checks
 * to an error, so the reference comparison in `Comparison` fails the build.
 *
 * @jenesis.release 25
 * @jenesis.main demo.errorprone.Comparison
 * @jenesis.plugin javac maven/com.google.errorprone/error_prone_core
 * @jenesis.pin javac/maven/com.github.ben-manes.caffeine/caffeine 3.0.5 SHA-256/8a9b54d3506a3b92ee46b217bcee79196b21ca6d52dc2967c686a205fb2f9c15
 * @jenesis.pin javac/maven/com.github.ben-manes.caffeine/caffeine/pom 3.0.5 SHA-256/bccfdb247ed76b52bbb1430c2282c43bad418ed3c697b4e2a95ad099bbf25cf8
 * @jenesis.pin javac/maven/com.github.kevinstern/software-and-algorithms 1.0 SHA-256/61ab82439cef37343b14f53154c461619375373a56b9338e895709fb54e0864c
 * @jenesis.pin javac/maven/com.github.kevinstern/software-and-algorithms/pom 1.0 SHA-256/ed971f009354e68c09414c964fbe1a2c85c2c8bbd460122b7497a7b8c5d4f388
 * @jenesis.pin javac/maven/com.google.auto.service/auto-service-aggregator/pom 1.0.1 SHA-256/0481460db59903362b26fabd3bdcd5ec200750f9391bbb6502fe3c7dc2df3e4c
 * @jenesis.pin javac/maven/com.google.auto.service/auto-service-annotations 1.0.1 SHA-256/c7bec54b7b5588b5967e870341091c5691181d954cf2039f1bf0a6eeb837473b
 * @jenesis.pin javac/maven/com.google.auto.service/auto-service-annotations/pom 1.0.1 SHA-256/e9bbf1b3d66ca18004a6a07a20dbfa10ca2ce8367348f741de2fe8fefce68cc2
 * @jenesis.pin javac/maven/com.google.auto.value/auto-value-annotations 1.9 SHA-256/fa5469f4c44ee598a2d8f033ab0a9dcbc6498a0c5e0c998dfa0c2adf51358044
 * @jenesis.pin javac/maven/com.google.auto.value/auto-value-annotations/pom 1.9 SHA-256/9220a3dabe75c79334f061b0e40df833448666bac28a8b823bc1e8fd52f8c98a
 * @jenesis.pin javac/maven/com.google.auto.value/auto-value-parent/pom 1.9 SHA-256/2e3d9dff639601cab881d7e170806bcbd8e031f7ebff259cbb45be57b4cbd787
 * @jenesis.pin javac/maven/com.google.auto/auto-common 1.2.2 SHA-256/f50b1ce8a41fad31a8a819c052f8ffa362ea0a3dbe9ef8f7c7dc9a36d4738a59
 * @jenesis.pin javac/maven/com.google.auto/auto-common/pom 1.2.2 SHA-256/03a992fba4fcfbbcf77a460981181630046e5bae09673a48193c6e0f761271ea
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotation 2.50.0 SHA-256/aa9b67a3aa3418b366e26594e1ae35d100ad3369cd1010ae7d4782d984ea9aaf
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotation/pom 2.50.0 SHA-256/27b78b0236df853dc506c470f6f60a8d0f5cf25c6ae22b347f3369307823e40a
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotations 2.50.0 SHA-256/4667724877f1d37a689202da191e23efa7657c62eef93ccdac406eccfe5cdd0a
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_annotations/pom 2.50.0 SHA-256/71a0680d277324bab46bec4b00f6f111c1b794a931d0d8cda79bcb9246982f92
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_check_api 2.50.0 SHA-256/ee935a4f42ac409fb0a471affea938c3050c6887dbaadb106868936d83d8c8f1
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_check_api/pom 2.50.0 SHA-256/a493d2ec338fdf8d622f692f4f957b523925e234aad117fc1e4872ba43abe85b
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_core 2.50.0 SHA-256/40a88d3ea732fac3a57ae0e2b27f1729ce76cc41857bcd63d5845e79eae5f72e
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_core/pom 2.50.0 SHA-256/88230678dca735cc2dc10d6331bcaf7428d2bd108d621d4dc69178224297e1f6
 * @jenesis.pin javac/maven/com.google.errorprone/error_prone_parent/pom 2.50.0 SHA-256/abf45a019db125b50ff55134ec2af94ae3519761038adf0a60b9bd7b4b8b6254
 * @jenesis.pin javac/maven/com.google.googlejavaformat/google-java-format 1.35.0 SHA-256/b2a52e363d51dd86b3b38d1a8dd51538541b42ff1048769684681c676550f043
 * @jenesis.pin javac/maven/com.google.googlejavaformat/google-java-format-parent/pom 1.35.0 SHA-256/9aa329c60659bf429cb1d99341401d6f92088afc69be5f4bc09c6ef8c154f5e3
 * @jenesis.pin javac/maven/com.google.googlejavaformat/google-java-format/pom 1.35.0 SHA-256/2d7c391affb930d6263d3b18a139a530bb9bbaf367211013aeb2f806d106d61c
 * @jenesis.pin javac/maven/com.google.guava/failureaccess 1.0.3 SHA-256/cbfc3906b19b8f55dd7cfd6dfe0aa4532e834250d7f080bd8d211a3e246b59cb
 * @jenesis.pin javac/maven/com.google.guava/failureaccess/pom 1.0.3 SHA-256/c54beff37f6d42d43e14722d54a522c9ad51ef97fc5b79277e62a3ebf882f3bb
 * @jenesis.pin javac/maven/com.google.guava/guava 33.5.0-jre SHA-256/1e301f0c52ac248b0b14fdc3d12283c77252d4d6f48521d572e7d8c4c2cc4ac7
 * @jenesis.pin javac/maven/com.google.guava/guava-parent/pom 26.0-android SHA-256/f8698ab46ca996ce889c1afc8ca4f25eb8ac6b034dc898d4583742360016cc04
 * @jenesis.pin javac/maven/com.google.guava/guava-parent/pom 33.4.0-android SHA-256/7220ede61026596fbc720ee4b93246fa2d14f328058532b59ef053de397c7d83
 * @jenesis.pin javac/maven/com.google.guava/guava-parent/pom 33.5.0-jre SHA-256/68719e687c6e4c9ff3e0fecbef7bd20896f0f4f7b314743ed33c72f962568215
 * @jenesis.pin javac/maven/com.google.guava/guava/pom 33.5.0-jre SHA-256/0478fa78a908b3c31fe6d77be9978aaa621e2a181db84007e3d3b37424a2ac61
 * @jenesis.pin javac/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
 * @jenesis.pin javac/maven/com.google.guava/listenablefuture/pom 9999.0-empty-to-avoid-conflict-with-guava SHA-256/18d4b1db26153d4e55079ce1f76bb1fe05cdb862ef9954a88cbcc4ff38b8679b
 * @jenesis.pin javac/maven/com.google.j2objc/j2objc-annotations 3.1 SHA-256/84d3a150518485f8140ea99b8a985656749629f6433c92b80c75b36aba3b099b
 * @jenesis.pin javac/maven/com.google.j2objc/j2objc-annotations/pom 3.1 SHA-256/14570838500034fc1b47c8205ce1c9d6b255c1ccdd2d1afdcdcbf75e89b24a33
 * @jenesis.pin javac/maven/com.google.protobuf/protobuf-bom/pom 4.33.2 SHA-256/6ca5f1e4380c5323cb103b034edf1160c9fbb676aede5d9989f4def172c7c4d8
 * @jenesis.pin javac/maven/com.google.protobuf/protobuf-java 4.33.2 SHA-256/c5b582aa127fb62c5fc3077329d522dfb7930b4e9a625c08760b681b2ba5aab7
 * @jenesis.pin javac/maven/com.google.protobuf/protobuf-java/pom 4.33.2 SHA-256/1a72c68ec07a5c0377474b1997a363ad8347a5f206b871db40b388b7f2e78196
 * @jenesis.pin javac/maven/com.google.protobuf/protobuf-parent/pom 4.33.2 SHA-256/c8c22112c7860e6eddc7ecb01ce9176fcbb4049eda5b0a26be3d4bb9fbd80211
 * @jenesis.pin javac/maven/io.github.eisop/dataflow-errorprone 3.41.0-eisop1 SHA-256/10434fba4e53f55fa9c76904cde414b918932548c9dfc4e2d634ac05ff7a7d10
 * @jenesis.pin javac/maven/io.github.eisop/dataflow-errorprone/pom 3.41.0-eisop1 SHA-256/23f1cdfa4613c00cc6ea5c9f5360e1471b449f82633f13cf5ff6cdb34fa31ebd
 * @jenesis.pin javac/maven/io.github.java-diff-utils/java-diff-utils 4.12 SHA-256/9990a2039778f6b4cc94790141c2868864eacee0620c6c459451121a901cd5b5
 * @jenesis.pin javac/maven/io.github.java-diff-utils/java-diff-utils-parent/pom 4.12 SHA-256/d811cf9f118cc2cad130c9427ad55c174d4c0a6f1a00ac1ae1c9bcbec5c44b19
 * @jenesis.pin javac/maven/io.github.java-diff-utils/java-diff-utils/pom 4.12 SHA-256/c26e097edc8ec6805d1319817d23d4e496cc1015cc55dc5202112d8f6a9165fc
 * @jenesis.pin javac/maven/javax.inject/javax.inject 1 SHA-256/91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff
 * @jenesis.pin javac/maven/javax.inject/javax.inject/pom 1 SHA-256/943e12b100627804638fa285805a0ab788a680266531e650921ebfe4621a8bfa
 * @jenesis.pin javac/maven/org.checkerframework/checker-qual 3.19.0 SHA-256/a827c49183f3a632277d27a0a4673686cb341507447b9d570261094bd748aa68
 * @jenesis.pin javac/maven/org.checkerframework/checker-qual/pom 3.19.0 SHA-256/29ba9c5ce1a94b700bd823c4ed612f5827b590f19a49775fd7eb8f997f8aa371
 * @jenesis.pin javac/maven/org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
 * @jenesis.pin javac/maven/org.jspecify/jspecify/pom 1.0.0 SHA-256/cdab929a3b95211f43d2090c5e2d0dfe8465960e378bc32b35841dab324433a6
 * @jenesis.pin javac/maven/org.pcollections/pcollections 4.0.1 SHA-256/1f82766d7c3221930854033bebff5073ea46b43f27326074bbe15d148c18bfb3
 * @jenesis.pin javac/maven/org.pcollections/pcollections/pom 4.0.1 SHA-256/fa3f298b50253b96e9cfb06f011c3bcc78fd9b40d8dcd25909bca04549738c71
 * @jenesis.pin javac/maven/org.sonatype.oss/oss-parent/pom 7 SHA-256/b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454
 * @jenesis.pin javac/maven/org.sonatype.oss/oss-parent/pom 9 SHA-256/fb40265f982548212ff82e362e59732b2187ec6f0d80182885c14ef1f982827a
 */
module demo.errorprone {
    exports demo.errorprone;
}
