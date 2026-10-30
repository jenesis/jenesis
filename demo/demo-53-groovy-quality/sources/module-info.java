/**
 * The Groovy counterpart to the Java code-quality demo. A {@code codenarc.xml}
 * activates CodeNarc, which lints the Groovy sources. There is no inferred
 * Groovy formatter, so this demo is lint-only. The Groovy compiler is pinned in
 * its own {@code groovy} group while CodeNarc floats its own {@code RELEASE}.
 *
 * @jenesis.release 25
 * @jenesis.pin codenarc/module/build.jenesis 0.18.0 SHA-256/35a94fc0761110ebcd2fa51b2226a3e7e0a942b14cd69d67cc584bf5f3519f70
 * @jenesis.pin codenarc/module/build.jenesis.plugin.codenarc 0.2.0 SHA-256/f9f230d3ebb2f37b2c43318b381ad23eec618b3508083e4254f5efcee4d99d5c
 * @jenesis.pin codenarc:tool/maven/com.github.javaparser/javaparser-core 3.26.2 SHA-256/3e3e0c65d57d12797dbead3df1ebb28e7583737d0cd1f2a898dba6febd50ab88
 * @jenesis.pin codenarc:tool/maven/com.github.javaparser/javaparser-core/pom 3.26.2 SHA-256/f312ba81707d56db4486c1f9ad1a6e03c36a99acf1e089a186ea7478a71344b8
 * @jenesis.pin codenarc:tool/maven/com.github.javaparser/javaparser-parent/pom 3.26.2 SHA-256/9ec9c2e358d3a69fdca53d52eb58aaac29e90be0f500d10c99ed2b95b9105034
 * @jenesis.pin codenarc:tool/maven/com.thoughtworks.qdox/qdox 1.12.1 SHA-256/21fba22f830e9268f07cf4ab2d99e8181abbdcb0cb91ee0228eb3cb918dcdd1d
 * @jenesis.pin codenarc:tool/maven/com.thoughtworks.qdox/qdox/pom 1.12.1 SHA-256/c52a6616d04efb30c9edeab9ee82b0591e4d04d38e030bf81e250776552d215d
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant 1.10.15 SHA-256/763acda4a69588c9ea8817a952851ff0c2fc4bffa1d081c2565dc407f29d5794
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-antlr 1.10.15 SHA-256/619e361609d446fe6ab67d1d45b8656e68644cbd7c45bbed5c2c8816408d8dd5
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-antlr/pom 1.10.15 SHA-256/f706ec5c533200f6ea1db97e4c7fa476d9f2f25d06162d1ca97106a1ab84c73d
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-junit 1.10.15 SHA-256/9a455eeb4bedc1d8bcd4b8099d4b1f18f1bf1bc977cd6e389a106fe0cf366711
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-junit/pom 1.10.15 SHA-256/5e1cd65c9bc33f43c10b500beba447867d6a737032bc9365583d31810f8ed234
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-launcher 1.10.15 SHA-256/5c8551990307a032336d98ddaed549a39a689f07d4d4c6b950601bf22b3d6a1b
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-launcher/pom 1.10.15 SHA-256/79af842a2979dc5fe0022bc073c498810eead83bc6283eedf34dc4dfe30dac95
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant-parent/pom 1.10.15 SHA-256/49884f1873c5107cc237f4285c4477479bb0804bf0737394801b08d75e2bbeb0
 * @jenesis.pin codenarc:tool/maven/org.apache.ant/ant/pom 1.10.15 SHA-256/4780e61e87816eee1f21d184ec997b65f93db5f4b9042c178adb29e23e7425d6
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-ant 4.0.24 SHA-256/c2c1cffa463e396fa7c928cd115fc0cf602bf7f10d322d7fbbeb193759967e72
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-ant/pom 4.0.24 SHA-256/ddd5cce3d667c360f1a8eeb63febe289cac35f627c1147e9e55aba8d1e865df9
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-bom/pom 4.0.24 SHA-256/684fd2a23ac1bc1fd3481be0276d4fc048314c8496137fdd50b3e2c599bb55f8
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-bom/pom 6.0.0-alpha-1 SHA-256/d8fad415eeaf4377a6098506d29b345134f117a7d12187e2d6b1278ea423b04d
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-docgenerator 4.0.24 SHA-256/86ef6a7e6c50bcee25b85ffd9fe59708a10978737223dae50e1bbcf0cff0bef1
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-docgenerator/pom 4.0.24 SHA-256/72e0649d716305de81ef10c418238287ff7593c72ee4cfe35503163a150cbeca
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-groovydoc 4.0.24 SHA-256/9345bb7b4cda6219ad5355a99213797cf3969cf0aa948067c30563a0dbb5ccc0
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-groovydoc/pom 4.0.24 SHA-256/467f4df7fdadce601797f3cb093016b23ef2f6c7e7a1b783871dc0857d745eb9
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-json 4.0.24 SHA-256/e813df50d6544b90bbdc3552105be9a69758d9134e3b6990d882f35fca3c858f
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-json/pom 4.0.24 SHA-256/f8da6bc36bc1a8d933bf66db4c4b14585c1aeb87068bad14c8d383301a887328
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-templates 4.0.24 SHA-256/b3b11cc4a84badc6d030d2160fc2d05ac142017c122355cef04597c185be7ec0
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-templates/pom 4.0.24 SHA-256/17da3a777fb08ad8aca3f31159fdfdf5c6f6963df52f037a8c9de6e32037b321
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-xml 4.0.24 SHA-256/0bb344d6005a03091a6dc639e235c3d36c7af978e656ae7f2a1d95002e8f3f0c
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy-xml/pom 4.0.24 SHA-256/9a02ed8a6053b2e672bbc27992d450954718bfe4c73a66f7cc70d464687e37ed
 * @jenesis.pin codenarc:tool/maven/org.apache.groovy/groovy/pom 6.0.0-alpha-1 SHA-256/bc2f56284d37d6d5e634522dcc6d1431a5e1a73377fb70109a093533c5b20986
 * @jenesis.pin codenarc:tool/maven/org.codehaus/codehaus-parent/pom 4 SHA-256/6b87237de8c2e1740cf80627c7f3ce3e15de1930bb250c55a1eca94fa3e014df
 * @jenesis.pin codenarc:tool/maven/org.codenarc/CodeNarc 3.7.0-groovy-4.0 SHA-256/059246e61ad0a8f2234b5da7fd224a1443b3672bc9448108e0f89a93bb18493e
 * @jenesis.pin codenarc:tool/maven/org.codenarc/CodeNarc/pom 3.7.0-groovy-4.0 SHA-256/33a4adae8cead456ccc5f91bad30838dd0a8c1420b16c9bbcf388cf36370f305
 * @jenesis.pin codenarc:tool/maven/org.gmetrics/GMetrics-Groovy4 2.1.0 SHA-256/485c98e78e76200e58dff9c374979e81f73fb5796645ba99bdc37c28fee33d90
 * @jenesis.pin codenarc:tool/maven/org.gmetrics/GMetrics-Groovy4/pom 2.1.0 SHA-256/d5c227eac8fbfc8a1adc09a3298abcbd6f9af5135c6bbcb5ed03ab165efe74e0
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-api 1.7.35 SHA-256/84cbd60deaf9e18db8cb181e43db4e63f7de353cfcaf654a76d85b22da4d2762
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-api/pom 1.7.35 SHA-256/2fb6434ed1c82e4ac78cd80043800b2bdb80a5c73aa65fc6b7de5fd5e42fd409
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-bom/pom 2.1.0-alpha1 SHA-256/a8e81389978f440709241b983d31efa787113be11b818c2c6bcd9ed3096fd485
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-parent/pom 1.7.35 SHA-256/0912e868d5183a2de49dd5f53942531d294b2c05c24e838ca5ecda8bf0182389
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-parent/pom 2.1.0-alpha1 SHA-256/ce4735b1f9c821de0b92b8dbe40b071c6ea320732e593559c6ea6df965f8738f
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-simple 2.1.0-alpha1 SHA-256/014fedac7a32288ed6f8f72a1007e7fb32aec5bfedb271467e496e0953482f75
 * @jenesis.pin codenarc:tool/maven/org.slf4j/slf4j-simple/pom 2.1.0-alpha1 SHA-256/46045e1be100f7824b5084e1f37782c6804970478a4573de8c49a07d03a94863
 * @jenesis.pin groovy/module/build.jenesis 0.18.0 SHA-256/35a94fc0761110ebcd2fa51b2226a3e7e0a942b14cd69d67cc584bf5f3519f70
 * @jenesis.pin groovy/module/build.jenesis.plugin.groovy 0.2.0 SHA-256/25dd8f40a87d19e274e223d93b3c46b4752c7b009ff570fa63a02cb0dea2b00d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy-bom/pom 6.0.0-alpha-1 SHA-256/d8fad415eeaf4377a6098506d29b345134f117a7d12187e2d6b1278ea423b04d
 * @jenesis.pin groovy:tool/maven/org.apache.groovy/groovy/pom 6.0.0-alpha-1 SHA-256/bc2f56284d37d6d5e634522dcc6d1431a5e1a73377fb70109a093533c5b20986
 * @jenesis.pin maven/org.apache.groovy/groovy-bom/pom 5.0.6 SHA-256/ad5d4b53e161e6537afe57dc96a6608cb36896cfb733caa997e9be28d59ab54e
 * @jenesis.pin maven/org.apache.groovy/groovy/pom 5.0.6 SHA-256/17eed34dc0dc57b902b38e19136394e72431c515d0aaed0e3fabd95df77b36a8
 * @jenesis.pin org.apache.groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 * @jenesis.pin org.apache.groovy/groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 */
module sample {
    requires org.apache.groovy;
    exports sample;
}
