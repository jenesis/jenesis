/**
 * A grammar compiled into the module that uses it. The `.g4` grammar lives
 * under the module's `META-INF/build.jenesis` folder and an `antlr.properties`
 * in the configuration folder switches the ANTLR generator on, naming the
 * package the parser is generated into. Only the ANTLR runtime is a dependency
 * of the module; the ANTLR tool itself resolves in its own `antlr` group.
 *
 * @jenesis.release 25
 * @jenesis.main demo.antlr.Calculator
 * @jenesis.pin antlr:tool/maven/com.ibm.icu/icu4j 72.1 SHA-256/3df572b240a68d13b5cd778ad2393e885d26411434cd8f098ac5987ea2e64ce3
 * @jenesis.pin antlr:tool/maven/org.abego.treelayout/org.abego.treelayout.core 1.0.3 SHA-256/fa5e31395c39c2e7d46aca0f81f72060931607b2fa41bd36038eb2cb6fb93326
 * @jenesis.pin antlr:tool/maven/org.antlr/ST4 4.3.4 SHA-256/f927ac384c46d749f8b5ec68972a53aed21e00313509299616edb73bfa15ff33
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr-runtime 3.5.3 SHA-256/68bf9f5a33dfcb34033495c587e6236bef4e37aa6612919f5b1e843b90669fb9
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4 4.13.2 SHA-256/e6f0b10d2ad206f338afe16867fc47148b6729d6e3a260ea28379b91f03a3657
 * @jenesis.pin antlr:tool/maven/org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 * @jenesis.pin org.antlr.antlr4.runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 * @jenesis.pin org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
 * @jenesis.pin antlr/module/build.jenesis 0.16.0 SHA-256/6c7ca48b61d71ca04a929bfc937f49ca5ba899c083b0a011fc29115d44b522c4
 * @jenesis.pin antlr/module/build.jenesis.plugin.antlr 0.16.0 SHA-256/a192647a2c06c39913c7e7ee5641681375f847d939ddcc57897d94ac5b76335d
 */
module demo.antlr {
    requires org.antlr.antlr4.runtime;

    exports demo.antlr;
}
