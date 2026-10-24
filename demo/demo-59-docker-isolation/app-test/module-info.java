/**
 * The test module for demo.dockerisolation. The @jenesis.test tag marks it as a
 * test variant, so it is compiled and run during the build but never staged as a
 * published artifact. Its JUnit closure is pinned on the plain module trail.
 *
 * @jenesis.release 25
 * @jenesis.test demo.dockerisolation
 * @jenesis.pin maven/org.apiguardian/apiguardian-api/pom 1.1.2 SHA-256/32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0
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
module demo.dockerisolation.test {
    requires demo.dockerisolation;
    requires org.junit.jupiter;
}
