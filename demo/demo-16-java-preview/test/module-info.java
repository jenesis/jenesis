/**
 * The tests of demo.preview. A module compiled against classes that use preview
 * features must enable them itself, so its release is 25-preview as well.
 *
 * @jenesis.release 25-preview
 * @jenesis.test demo.preview
 * @jenesis.pin maven/org.apiguardian/apiguardian-api/pom 1.1.2 SHA-256/32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0
 * @jenesis.pin maven/org.jspecify/jspecify/pom 1.0.0 SHA-256/cdab929a3b95211f43d2090c5e2d0dfe8465960e378bc32b35841dab324433a6
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-api/pom 6.1.3 SHA-256/a9d6347da0329bf118e9878617e50b5537eb9ec3c5fcd08852650713832d1ac1
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-engine/pom 6.1.3 SHA-256/051fc65d97c15e7cd646831368af8afec590b008c804d293aff114c74a1e2530
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-params/pom 6.1.3 SHA-256/10f7e85b5ce53014c946b14c750b09097bed1892ecaf2cea05d12aab6e8677d2
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter/pom 6.1.3 SHA-256/ebfffb41861ab1ccc989de2c9c2d11924e5ddded2728b053f5f2fddc8409c0e1
 * @jenesis.pin maven/org.junit.platform/junit-platform-commons/pom 6.1.3 SHA-256/a5a3a1aec96113b1e953545c3d8c5117ff0220c56f7293c6fff7d35b1cd37c14
 * @jenesis.pin maven/org.junit.platform/junit-platform-console/pom 6.1.3 SHA-256/da2739e6f56f89df300165e41facc375c0b03ca8ae68dcd2531afed409f0731e
 * @jenesis.pin maven/org.junit.platform/junit-platform-engine/pom 6.1.3 SHA-256/5194dad25d878985646a49df71bd985e8d2dd4c6b6765717093ac8a31bdb32e4
 * @jenesis.pin maven/org.junit.platform/junit-platform-launcher/pom 6.1.3 SHA-256/2daf3b4360ba9e07344210ccf29b6be9703d0abc66de4121f65d90119fe996ca
 * @jenesis.pin maven/org.junit.platform/junit-platform-reporting/pom 6.1.3 SHA-256/bb287edb5fc60d6e648615cfa55865d31fa5dda22eb23eff14f144611c909eea
 * @jenesis.pin maven/org.junit/junit-bom/pom 6.1.3 SHA-256/a9000043610a3e90c852e779a6b1a8eb724a9f312cf5f7ddd57862b010c65dc8
 * @jenesis.pin maven/org.opentest4j.reporting/open-test-reporting-tooling-spi/pom 0.2.5 SHA-256/09a28ae6b0607712afefa63688dc8465a45e4d751407dc514fd858ad80a68b3b
 * @jenesis.pin maven/org.opentest4j/opentest4j/pom 1.3.0 SHA-256/9bf7cffc410f3e8372c2522578df9ca56d9d43bd937e30948706c232a943b355
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
 * @jenesis.pin org.junit.jupiter 6.1.3 SHA-256/6b0a14075c74221de53047e363860b7d19fce802bebc152df2c35c4089c6c379
 * @jenesis.pin org.junit.jupiter/junit-jupiter 6.1.3 SHA-256/6b0a14075c74221de53047e363860b7d19fce802bebc152df2c35c4089c6c379
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 6.1.3 SHA-256/555d6cf20fa1710884dd01b86cc5785397ba73e21ada2d4b784f5f1a14dcafc4
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 6.1.3 SHA-256/414559e78bbfa3bdb2eab009ac3ab1e22369ac31117c270a0bd666da115c5c6c
 * @jenesis.pin org.junit.jupiter/junit-jupiter-params 6.1.3 SHA-256/05c51cedba2c06a9770707391e006cee825876937dea580b94a265ce145d4939
 * @jenesis.pin org.junit.platform.console 6.1.3 SHA-256/913554ad65b9420936822889a7268a6793a942e5f77ad8b653b8ec05068fa3be
 * @jenesis.pin org.junit.platform/junit-platform-commons 6.1.3 SHA-256/a4774ae923c109544034aeae7c762cf017fbcdd1342403ba90e0921984b608c3
 * @jenesis.pin org.junit.platform/junit-platform-console 6.1.3 SHA-256/913554ad65b9420936822889a7268a6793a942e5f77ad8b653b8ec05068fa3be
 * @jenesis.pin org.junit.platform/junit-platform-engine 6.1.3 SHA-256/21ad7ad3a35beda16387979317be1833a72a71d812f9a66ee4b9a7bafb56334c
 * @jenesis.pin org.junit.platform/junit-platform-launcher 6.1.3 SHA-256/1dec64b1fc0b7c47a11302aa5b4d37d8160c041b396e27067709ce775601a7b6
 * @jenesis.pin org.junit.platform/junit-platform-reporting 6.1.3 SHA-256/d584a16cd87da5609af5e35428ed4f40b192681a2487dc59d02ecb62f6c417c2
 * @jenesis.pin org.opentest4j.reporting/open-test-reporting-tooling-spi 0.2.5 SHA-256/df237b68847637747f0bfdb88fa9cdd9c72cc85550fad0c41ddb33869a5ca516
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 */
module demo.preview.test {
    requires demo.preview;
    requires org.junit.jupiter;
}
