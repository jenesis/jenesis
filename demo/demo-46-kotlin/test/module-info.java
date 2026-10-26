/**
 * The test module for sample.kotlin. It exercises the Kotlin Sample, which reads
 * its greeting from a packaged resource (messages.properties) through the Java
 * Greeter, proving that a root resource is copied into the jar of a mixed
 * Java + Kotlin module. Its JUnit closure plus the kotlin.stdlib runtime it
 * inherits from sample.kotlin are pinned on the plain module trail.
 *
 * @jenesis.release 25
 * @jenesis.test sample.kotlin
 * @jenesis.pin kotlin/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin kotlin/module/build.jenesis.plugin.kotlin 0.1.0 SHA-256/8ae119271454d849d712d61b0166b5879f6c6493897beaa4d76b1aa5319545c6
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-build-tools-api 2.4.21 SHA-256/c5cd6a49bd072b529ae10809111c16a7aa0f92147e95d288354017fd65799461
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-build-tools-cri-impl 2.4.21 SHA-256/d1849f9b98033f5333905409074ca90e6a0e17258cd0eb8befa47d795e695841
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-build-tools-impl 2.4.21 SHA-256/3152b3e1fbaa4d48f3681734a2c0efc438359a48c8a3c5cac00d874670de1377
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.4.21 SHA-256/ef19419c765e7ac8404465fa026ca8fc4dbb8a822de0fc79aa53c8dce29f1d02
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-compiler-runner 2.4.21 SHA-256/75cd5956405697559e6925ebc5808449a2e12002d4f017275281ad3d4c0c7985
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-daemon-client 2.4.21 SHA-256/095903c4c4713bf91a562d226d7ba0464cf5424e76342d487deec494451ab29b
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.4.21 SHA-256/7b3ef24674e5bcffcadfc4e43202ea314194939ecbcdd90a4770b2f1b04e0d07
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.4.21 SHA-256/a658cf5902a1525a06c264a5e42b437e97a6679ddeef8ca641246f270cf6a013
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-stdlib 2.4.21 SHA-256/bd8250210584cb659847dce9cda660f8e9906e5def7fbebcea93dc6dcb6a88a3
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlin/kotlin-tooling-core 2.4.21 SHA-256/5fc5db083803232857408ed5c25bcc307c703b04bdef6cb2c34ebf4db611a622
 * @jenesis.pin kotlin:tool/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
 * @jenesis.pin kotlin:tool/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin maven/org.apiguardian/apiguardian-api/pom 1.1.2 SHA-256/32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0
 * @jenesis.pin maven/org.jetbrains.kotlin/kotlin-stdlib-common/pom 1.9.10 SHA-256/7d4b70547910676b3bdfc8925a88f3b6bfb24582c9784542805544ceef490a92
 * @jenesis.pin maven/org.jetbrains.kotlin/kotlin-stdlib/pom 1.9.10 SHA-256/7e29fbf73fdf71e0679d3dee7e680fd573464fa01644a4f58ab819d2c088d3d2
 * @jenesis.pin maven/org.jetbrains/annotations/pom 13.0 SHA-256/965aeb2bedff369819bdde1bf7a0b3b89b8247dd69c88b86375d76163bb8c397
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-api/pom 5.11.3 SHA-256/f19afe0923b01935c40f2e7e96d66037e2008b7017917cc7051338377849618e
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-engine/pom 5.11.3 SHA-256/789e378d37c49dd8654d66e3ad3ade418e5884ae6f48723901dd3be7c9ec9e03
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-params/pom 5.11.3 SHA-256/ccec3424a0714944c677b94fd503fd0c1ca243ce15c40160d60ce628aafa35ff
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter/pom 5.11.3 SHA-256/cbe9f385a0a13b6fed8c6c6ed1f16dc605a92657f3b25b196838c73d97684806
 * @jenesis.pin maven/org.junit.platform/junit-platform-commons/pom 1.11.3 SHA-256/816ebd3244a770d915d9c1d40d3b4651fe348f42f89b7cb7ebd0dee209c52040
 * @jenesis.pin maven/org.junit.platform/junit-platform-console/pom 1.11.3 SHA-256/a5170187507e7c53eb93a5391b06d8412a09af4a40d8d110f18df283a736e942
 * @jenesis.pin maven/org.junit.platform/junit-platform-engine/pom 1.11.3 SHA-256/fe26dc5da911b94b68c12b2241257a208135bbb9b8c91cc1a13433a80a7a791e
 * @jenesis.pin maven/org.junit.platform/junit-platform-launcher/pom 1.11.3 SHA-256/12514367b93ce2850e5dae23b7c3d64a366e54cb8b8237f914d883fd9dba1bfa
 * @jenesis.pin maven/org.junit.platform/junit-platform-reporting/pom 1.11.3 SHA-256/78560c03c04f4810a152f291894b90d290219ec5b492dbb605c25ada77a4ee77
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.11.3 SHA-256/f13df2e4caf1febce567667e7c378101a0331d53d1324d6e2dfe3aecfb1f7774
 * @jenesis.pin maven/org.opentest4j/opentest4j/pom 1.3.0 SHA-256/9bf7cffc410f3e8372c2522578df9ca56d9d43bd937e30948706c232a943b355
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib 1.9.10 SHA-256/55e989c512b80907799f854309f3bc7782c5b3d13932442d0379d5c472711504
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib-common 1.9.10 SHA-256/cde3341ba18a2ba262b0b7cf6c55b20c90e8d434e42c9a13e6a3f770db965a88
 * @jenesis.pin org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin org.junit.jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter/junit-jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 5.11.3 SHA-256/5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 5.11.3 SHA-256/e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589
 * @jenesis.pin org.junit.jupiter/junit-jupiter-params 5.11.3 SHA-256/0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec
 * @jenesis.pin org.junit.platform.console 1.11.3 SHA-256/a21b34807eb7d8aa56295d152ff7e0988bd22bbd5f17086c10f42b5c5ac46033
 * @jenesis.pin org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin org.junit.platform/junit-platform-console 1.11.3 SHA-256/a21b34807eb7d8aa56295d152ff7e0988bd22bbd5f17086c10f42b5c5ac46033
 * @jenesis.pin org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin org.junit.platform/junit-platform-launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin org.junit.platform/junit-platform-reporting 1.11.3 SHA-256/b8e19dbebcae7d1ff30b9d767047fbf3694027c33dfa423b371693b7f6679ed1
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 */
module sample.kotlin.test {
    requires sample.kotlin;
    requires org.junit.jupiter;
}
