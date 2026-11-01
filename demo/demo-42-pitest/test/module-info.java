/**
 * @jenesis.release 25
 * @jenesis.test demo.mutation
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
 * @jenesis.pin pitest/module/build.jenesis 0.18.0 SHA-256/35a94fc0761110ebcd2fa51b2226a3e7e0a942b14cd69d67cc584bf5f3519f70
 * @jenesis.pin pitest/module/build.jenesis.plugin.pitest 0.2.0 SHA-256/af4164731fccd26860d0da8b4887b60ccf7860a19f9d73d7322234b7fb2d7aaf
 * @jenesis.pin pitest:tool/maven/antlr/antlr 2.7.7 SHA-256/88fbda4b912596b9f56e8e12e580cc954bacfb51776ecfddd3e18fc1cf56dc4c
 * @jenesis.pin pitest:tool/maven/antlr/antlr/pom 2.7.7 SHA-256/100f793ba27f8b4e4204edb46171ebf36e54e0f94cfc02527fea07a0bb1fceb7
 * @jenesis.pin pitest:tool/maven/net.sf.jopt-simple/jopt-simple 4.9 SHA-256/26c5856e954b5f864db76f13b86919b59c6eecf9fd930b96baa8884626baf2f5
 * @jenesis.pin pitest:tool/maven/net.sf.jopt-simple/jopt-simple/pom 4.9 SHA-256/7af7e2d8b24b4798f04c2b7da24c9fbd1b7557b4e017c2054481565916079092
 * @jenesis.pin pitest:tool/maven/org.antlr/stringtemplate 3.2.1 SHA-256/f66ce72e965e5301cb0f020e54d2ba6ad76feb91b3cbfc30dbbf00c06a6df6d7
 * @jenesis.pin pitest:tool/maven/org.antlr/stringtemplate/pom 3.2.1 SHA-256/b45e82655aa5a48f33d13a43e4345426b156335b35e24b5aea12db5823c16a16
 * @jenesis.pin pitest:tool/maven/org.apache.commons/commons-lang3 3.18.0 SHA-256/4eeeae8d20c078abb64b015ec158add383ac581571cddc45c68f0c9ae0230720
 * @jenesis.pin pitest:tool/maven/org.apache.commons/commons-lang3/pom 3.18.0 SHA-256/aa254b373b6f6d46bc9dca86331b072a8ab86eb25ea9921fd439618392e98a16
 * @jenesis.pin pitest:tool/maven/org.apache.commons/commons-parent/pom 85 SHA-256/d189ff2c0027e96bb65d31e6f227ed2af966169b36af1e973dd5ba08926dc7b5
 * @jenesis.pin pitest:tool/maven/org.apache.commons/commons-text 1.14.0 SHA-256/121fce2282910c8f0c3ba793a5436b31beb710423cbe2d574a3fb7a73c508e92
 * @jenesis.pin pitest:tool/maven/org.apache.commons/commons-text/pom 1.14.0 SHA-256/5c80e8144f22bb47dacb642f64460418eb253f0b1ba03eb1413c2f36f0a18805
 * @jenesis.pin pitest:tool/maven/org.apache.groovy/groovy-bom/pom 4.0.11 SHA-256/27553a483d0f46df6aa86a1ce1f4126dca34aa957b2f88aaeb63136fd847fe18
 * @jenesis.pin pitest:tool/maven/org.apache/apache/pom 35 SHA-256/ea297dcd114136e8b8e8b630230d52a76c2fc69f6c5db25d672b1857000728b8
 * @jenesis.pin pitest:tool/maven/org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin pitest:tool/maven/org.apiguardian/apiguardian-api/pom 1.1.2 SHA-256/32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-commons/pom 1.11.3 SHA-256/816ebd3244a770d915d9c1d40d3b4651fe348f42f89b7cb7ebd0dee209c52040
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-engine/pom 1.11.3 SHA-256/fe26dc5da911b94b68c12b2241257a208135bbb9b8c91cc1a13433a80a7a791e
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin pitest:tool/maven/org.junit.platform/junit-platform-launcher/pom 1.11.3 SHA-256/12514367b93ce2850e5dae23b7c3d64a366e54cb8b8237f914d883fd9dba1bfa
 * @jenesis.pin pitest:tool/maven/org.junit/junit-bom/pom 5.11.3 SHA-256/f13df2e4caf1febce567667e7c378101a0331d53d1324d6e2dfe3aecfb1f7774
 * @jenesis.pin pitest:tool/maven/org.junit/junit-bom/pom 5.13.1 SHA-256/fa68451ea830572ed43ffe51d75b6a05f7a5e665a602a51f49d6be02063a65f3
 * @jenesis.pin pitest:tool/maven/org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 * @jenesis.pin pitest:tool/maven/org.opentest4j/opentest4j/pom 1.3.0 SHA-256/9bf7cffc410f3e8372c2522578df9ca56d9d43bd937e30948706c232a943b355
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm 9.9.1 SHA-256/6f3828a215c920059a5efa2fb55c233d6c54ec5cadca99ce1b1bdd10077c7ddd
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-analysis 9.9.1 SHA-256/6260bffc8ec008dd1b713702c7994e2c94d188a3da5bef9e87278a16df6a7522
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-analysis/pom 9.9.1 SHA-256/36217e82d8a60134cf210f80eb76ddae595591c6085cab816b5aa10ce99fcba0
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-commons 9.9.1 SHA-256/c2319e014ce7199f2b7f7d56d6bb991863168c3f4b6cd6c9f542a4937ef7ef88
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-commons/pom 9.9.1 SHA-256/def70fa7d3a4bb71660bc661523329579a4bc31ccc96c42d3a5c3b9f76a6b649
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-tree 9.9.1 SHA-256/0f3555096b720b820bbacab0b515589bee0200bee099bda14c561738ae837ba1
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-tree/pom 9.9.1 SHA-256/67fee446ba512871300738f0f956f61a500a852f6861ff70f0a3c7bf0c0fa405
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-util 9.9.1 SHA-256/c5ebbbeaf68126af094b42fa4800f59bc4413abd02d95b9aefad722cd257e207
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm-util/pom 9.9.1 SHA-256/527969d08a4d75f9bdaf9cbf3837e98696a29890bc017d95fca6ff810f0d704c
 * @jenesis.pin pitest:tool/maven/org.ow2.asm/asm/pom 9.9.1 SHA-256/aca68dee9ba2f6cd90ffde728efdc7e3ebfcf59f3f41fbfe248d2d01d5b866af
 * @jenesis.pin pitest:tool/maven/org.ow2/ow2/pom 1.5.1 SHA-256/321ddbb7ee6fe4f53dea6b4cd6db74154d6bfa42391c1f763b361b9f485acf05
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest 1.25.4 SHA-256/b4be065f597b72da80e9d74aec0af418fce5c47629abff7b5e94557927c039f6
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-command-line 1.25.4 SHA-256/b8a013296eb208ae2d86aa6b2f6c2a2d0e073990f67b154fef01be0f0e000018
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-command-line/pom 1.25.4 SHA-256/05e2b6eee3e0965e644132c01a87cdfe4cc6deaf6bab813264882d9e2d22dc7d
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-entry 1.25.4 SHA-256/eb49ebafdac8338ae43d14b68fbf6ff697cafb633eb70c5e09d1ee98c7d599e1
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-entry/pom 1.25.4 SHA-256/678433a0c2b79db077b40b8e5a64f0d35f00ccf1d3ea7d86580d96b0dc28dbee
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-html-report 1.25.4 SHA-256/da45d9aa9418b12dfa64c352eda94d10afef6d3b108299a356e6a4a6d02edb17
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-html-report/pom 1.25.4 SHA-256/63a9da0f79efc061f169603e1e733b9deca253ceee8d7d99f22f6b657ef5081b
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-junit5-plugin 1.2.3 SHA-256/d3e2fcc8db5fd52c0c4ac2fe0d937e64da3df39b657093a1581b5fd463f9be64
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-junit5-plugin/pom 1.2.3 SHA-256/122245f55d934cfd0df28be82c27d6fa231a17a70d02434af576579d41b63846
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest-parent/pom 1.25.4 SHA-256/59d7966c71ec2f80ef8612bd86b5e985b89f56ec5a9d282d5d8fad17862163c2
 * @jenesis.pin pitest:tool/maven/org.pitest/pitest/pom 1.25.4 SHA-256/535ee0183e495e71257a5ad6e149b826b37ab775aa43c8b1da7c8be1723ece25
 * @jenesis.pin pitest:tool/maven/org.sonatype.oss/oss-parent/pom 7 SHA-256/b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454
 * @jenesis.pin pitest:tool/maven/org.spockframework/spock-bom/pom 2.3-groovy-4.0 SHA-256/df0f4ddf3cab2ae10323e879e27aaec974a403ac628ad8ccb22d13e9bf664419
 */
module demo.mutation.test {
    requires demo.mutation;
    requires org.junit.jupiter;
}
