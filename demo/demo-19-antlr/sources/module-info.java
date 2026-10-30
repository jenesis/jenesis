/**
 * A grammar compiled into the module that uses it. The `.g4` grammar lives
 * under the module's `META-INF/build.jenesis` folder and an `antlr.properties`
 * in the configuration folder switches the ANTLR generator on, naming the
 * package the parser is generated into. Only the ANTLR runtime is a dependency
 * of the module; the ANTLR tool itself resolves in its own `antlr` group.
 *
 * @jenesis.release 25
 * @jenesis.main demo.antlr.Calculator
 * @jenesis.pin antlr/module/build.jenesis 0.18.0 SHA-256/35a94fc0761110ebcd2fa51b2226a3e7e0a942b14cd69d67cc584bf5f3519f70
 * @jenesis.pin antlr/module/build.jenesis.plugin.antlr 0.2.0 SHA-256/d3fda8ad8bb2a2313b462c287cf0e3bb34cd25cf3dc710045fc816d5ba05e5df
 * @jenesis.pin antlr:tool/maven/com.ibm.icu/icu4j 72.1 SHA-256/3df572b240a68d13b5cd778ad2393e885d26411434cd8f098ac5987ea2e64ce3
 * @jenesis.pin antlr:tool/maven/com.ibm.icu/icu4j/pom 72.1 SHA-256/3def2b29af4a19ad805cb153056925a89a903f92fbee1adee12ed2fc14c44ee8
 * @jenesis.pin antlr:tool/maven/org.abego.treelayout/org.abego.treelayout.core 1.0.3 SHA-256/fa5e31395c39c2e7d46aca0f81f72060931607b2fa41bd36038eb2cb6fb93326
 * @jenesis.pin antlr:tool/maven/org.abego.treelayout/org.abego.treelayout.core/pom 1.0.3 SHA-256/a3b2b223794370355e792433af012fc993667c0331be2bacad84dbc09ace4a0c
 * @jenesis.pin antlr:tool/maven/org.antlr/ST4 4.3.4 SHA-256/f927ac384c46d749f8b5ec68972a53aed21e00313509299616edb73bfa15ff33
 * @jenesis.pin antlr:tool/maven/org.antlr/ST4/pom 4.3.4 SHA-256/9e7c1f3e489919440e8c13089e5963729d5b7f80f70233bfbae309c649abca3e
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr-master/pom 3.5.3 SHA-256/ea9e37250f5c4c2e76b6538be97b57f3349bda585edf53f3ca97e207b385b895
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr-runtime 3.5.3 SHA-256/68bf9f5a33dfcb34033495c587e6236bef4e37aa6612919f5b1e843b90669fb9
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr-runtime/pom 3.5.3 SHA-256/13298e0e0aafaf414ff7d4406427cab6ec4fbfa36427f6d7103c432f32c07d25
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4 4.13.2 SHA-256/e6f0b10d2ad206f338afe16867fc47148b6729d6e3a260ea28379b91f03a3657
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4-master/pom 4.13.2 SHA-256/0adda026685873f651360178bffc446ceee4833081739e3ae9d6e8f07e8d902a
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4-runtime/pom 4.13.2 SHA-256/03ce07a2796c511b0c94dc14fd86ccdd6e3828c57967ad2383de304e0d11ba79
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4/pom 4.13.2 SHA-256/809ee49706dce3674988babfcad36b3c5a0e2fd5cfa0a51209103963e857261b
 * @jenesis.pin antlr:tool/maven/org.sonatype.oss/oss-parent/pom 7 SHA-256/b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454
 * @jenesis.pin antlr:tool/maven/org.sonatype.oss/oss-parent/pom 9 SHA-256/fb40265f982548212ff82e362e59732b2187ec6f0d80182885c14ef1f982827a
 * @jenesis.pin maven/org.antlr/antlr4-master/pom 4.13.2 SHA-256/0adda026685873f651360178bffc446ceee4833081739e3ae9d6e8f07e8d902a
 * @jenesis.pin maven/org.antlr/antlr4-runtime/pom 4.13.2 SHA-256/03ce07a2796c511b0c94dc14fd86ccdd6e3828c57967ad2383de304e0d11ba79
 * @jenesis.pin maven/org.sonatype.oss/oss-parent/pom 9 SHA-256/fb40265f982548212ff82e362e59732b2187ec6f0d80182885c14ef1f982827a
 * @jenesis.pin org.antlr.antlr4.runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 * @jenesis.pin org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 */
module demo.antlr {
    requires org.antlr.antlr4.runtime;

    exports demo.antlr;
}
