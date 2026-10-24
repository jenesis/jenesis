/**
 * @jenesis.release 25
 * @jenesis.test demo.greeter
 * @jenesis.pin junit/junit 4.13.2 SHA-256/8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3
 * @jenesis.pin maven/junit/junit/pom 4.13.2 SHA-256/569b6977ee4603c965c1c46c3058fa6e969291b0160eb6964dd092cd89eadd94
 * @jenesis.pin maven/org.apiguardian/apiguardian-api/pom 1.1.2 SHA-256/32355081d109095c3d5d374d5a43b4f4c1b75d549e983ef50723e2772e5302a0
 * @jenesis.pin maven/org.hamcrest/hamcrest-core/pom 1.3 SHA-256/fde386a7905173a1b103de6ab820727584b50d0e32282e2797787c20a64ffa93
 * @jenesis.pin maven/org.hamcrest/hamcrest-parent/pom 1.3 SHA-256/6d535f94efb663bdb682c9f27a50335394688009642ba7a9677504bc1be4129b
 * @jenesis.pin maven/org.jspecify/jspecify/pom 1.0.0 SHA-256/cdab929a3b95211f43d2090c5e2d0dfe8465960e378bc32b35841dab324433a6
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-api/pom 6.1.3 SHA-256/a9d6347da0329bf118e9878617e50b5537eb9ec3c5fcd08852650713832d1ac1
 * @jenesis.pin maven/org.junit.jupiter/junit-jupiter-migrationsupport/pom 6.1.3 SHA-256/44cd3fdee9283d7b2e6a0990e518fe1e55df911879b04a3fc6bba1e86f14fc19
 * @jenesis.pin maven/org.junit.platform/junit-platform-commons/pom 6.1.3 SHA-256/a5a3a1aec96113b1e953545c3d8c5117ff0220c56f7293c6fff7d35b1cd37c14
 * @jenesis.pin maven/org.junit/junit-bom/pom 6.1.3 SHA-256/a9000043610a3e90c852e779a6b1a8eb724a9f312cf5f7ddd57862b010c65dc8
 * @jenesis.pin maven/org.opentest4j/opentest4j/pom 1.3.0 SHA-256/9bf7cffc410f3e8372c2522578df9ca56d9d43bd937e30948706c232a943b355
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.hamcrest/hamcrest-core 1.3 SHA-256/66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9
 * @jenesis.pin org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
 * @jenesis.pin org.junit.jupiter.migrationsupport 6.1.3 SHA-256/4a14527853ed8449d17ee6602693ae666437e0614708b910341552bd78d06075
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 6.1.3 SHA-256/555d6cf20fa1710884dd01b86cc5785397ba73e21ada2d4b784f5f1a14dcafc4
 * @jenesis.pin org.junit.jupiter/junit-jupiter-migrationsupport 6.1.3 SHA-256/4a14527853ed8449d17ee6602693ae666437e0614708b910341552bd78d06075
 * @jenesis.pin org.junit.platform/junit-platform-commons 6.1.3 SHA-256/a4774ae923c109544034aeae7c762cf017fbcdd1342403ba90e0921984b608c3
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 */
module demo.greeter.test {

    requires demo.greeter;
    requires org.junit.jupiter.migrationsupport;
}
