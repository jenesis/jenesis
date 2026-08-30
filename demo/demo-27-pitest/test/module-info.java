/**
 * @jenesis.release 25
 * @jenesis.test demo.mutation
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.junit.jupiter 5.11.3
 * @jenesis.pin org.junit.jupiter/junit-jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 5.11.3 SHA-256/5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 5.11.3 SHA-256/e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589
 * @jenesis.pin org.junit.jupiter/junit-jupiter-params 5.11.3 SHA-256/0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec
 * @jenesis.pin org.junit.platform.console 1.11.3
 * @jenesis.pin org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin org.junit.platform/junit-platform-console 1.11.3 SHA-256/a21b34807eb7d8aa56295d152ff7e0988bd22bbd5f17086c10f42b5c5ac46033
 * @jenesis.pin org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin org.junit.platform/junit-platform-launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin org.junit.platform/junit-platform-reporting 1.11.3 SHA-256/b8e19dbebcae7d1ff30b9d767047fbf3694027c33dfa423b371693b7f6679ed1
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 * @jenesis.pin pitest/maven/antlr/antlr 2.7.7 SHA-256/88fbda4b912596b9f56e8e12e580cc954bacfb51776ecfddd3e18fc1cf56dc4c
 * @jenesis.pin pitest/maven/net.sf.jopt-simple/jopt-simple 4.9 SHA-256/26c5856e954b5f864db76f13b86919b59c6eecf9fd930b96baa8884626baf2f5
 * @jenesis.pin pitest/maven/org.antlr/stringtemplate 3.2.1 SHA-256/f66ce72e965e5301cb0f020e54d2ba6ad76feb91b3cbfc30dbbf00c06a6df6d7
 * @jenesis.pin pitest/maven/org.apache.commons/commons-lang3 3.18.0 SHA-256/4eeeae8d20c078abb64b015ec158add383ac581571cddc45c68f0c9ae0230720
 * @jenesis.pin pitest/maven/org.apache.commons/commons-text 1.14.0 SHA-256/121fce2282910c8f0c3ba793a5436b31beb710423cbe2d574a3fb7a73c508e92
 * @jenesis.pin pitest/maven/org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin pitest/maven/org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin pitest/maven/org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin pitest/maven/org.junit.platform/junit-platform-launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin pitest/maven/org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 * @jenesis.pin pitest/maven/org.ow2.asm/asm 9.9.1 SHA-256/6f3828a215c920059a5efa2fb55c233d6c54ec5cadca99ce1b1bdd10077c7ddd
 * @jenesis.pin pitest/maven/org.ow2.asm/asm-analysis 9.9.1 SHA-256/6260bffc8ec008dd1b713702c7994e2c94d188a3da5bef9e87278a16df6a7522
 * @jenesis.pin pitest/maven/org.ow2.asm/asm-commons 9.9.1 SHA-256/c2319e014ce7199f2b7f7d56d6bb991863168c3f4b6cd6c9f542a4937ef7ef88
 * @jenesis.pin pitest/maven/org.ow2.asm/asm-tree 9.9.1 SHA-256/0f3555096b720b820bbacab0b515589bee0200bee099bda14c561738ae837ba1
 * @jenesis.pin pitest/maven/org.ow2.asm/asm-util 9.9.1 SHA-256/c5ebbbeaf68126af094b42fa4800f59bc4413abd02d95b9aefad722cd257e207
 * @jenesis.pin pitest/maven/org.pitest/pitest 1.25.4 SHA-256/b4be065f597b72da80e9d74aec0af418fce5c47629abff7b5e94557927c039f6
 * @jenesis.pin pitest/maven/org.pitest/pitest-command-line 1.25.4 SHA-256/b8a013296eb208ae2d86aa6b2f6c2a2d0e073990f67b154fef01be0f0e000018
 * @jenesis.pin pitest/maven/org.pitest/pitest-entry 1.25.4 SHA-256/eb49ebafdac8338ae43d14b68fbf6ff697cafb633eb70c5e09d1ee98c7d599e1
 * @jenesis.pin pitest/maven/org.pitest/pitest-html-report 1.25.4 SHA-256/da45d9aa9418b12dfa64c352eda94d10afef6d3b108299a356e6a4a6d02edb17
 * @jenesis.pin pitest/maven/org.pitest/pitest-junit5-plugin 1.2.3 SHA-256/d3e2fcc8db5fd52c0c4ac2fe0d937e64da3df39b657093a1581b5fd463f9be64
 */
open module demo.mutation.test {
    requires demo.mutation;
    requires org.junit.jupiter;
}
