/**
 * @jenesis.pin com.fasterxml.jackson.core/jackson-annotations 2.22 SHA-256/21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.22.3 SHA-256/8a501126a385b25841915d839508f8a66e2a0dbc8a6709d055ef3b3e852b094c
 * @jenesis.pin com.fasterxml.jackson.core/jackson-databind 2.22.3 SHA-256/556db5439e206114346043f68d200497dc96a0bca62a360a81784092ebd0e0a9
 * @jenesis.pin com.fasterxml.jackson.databind 2.22.3 SHA-256/556db5439e206114346043f68d200497dc96a0bca62a360a81784092ebd0e0a9
 * @jenesis.pin com.fasterxml.jackson.datatype.jsr310 2.22.3 SHA-256/ec7052ee48c9d873bb8148338a643f02cf89c01d035dd219411473030d6d2b22
 * @jenesis.pin com.fasterxml.jackson.datatype/jackson-datatype-jsr310 2.22.3 SHA-256/ec7052ee48c9d873bb8148338a643f02cf89c01d035dd219411473030d6d2b22
 * @jenesis.pin jakarta.annotation 3.0.0 SHA-256/b01f55552284cfb149411e64eabca75e942d26d2e1786b32914250e4330afaa2
 * @jenesis.pin jakarta.annotation/jakarta.annotation-api 3.0.0 SHA-256/b01f55552284cfb149411e64eabca75e942d26d2e1786b32914250e4330afaa2
 * @jenesis.pin maven/com.fasterxml.jackson.core/jackson-annotations/pom 2.22 SHA-256/38c3ba52a0eade3e3eacc5b13499d56e1038ccbad8ec4bb79426dc21432b46d0
 * @jenesis.pin maven/com.fasterxml.jackson.core/jackson-core/pom 2.22.3 SHA-256/bc74c18b24f3ebbfd42741f2b29b37eca9b25e512e6fa7add77f6973cee50bf1
 * @jenesis.pin maven/com.fasterxml.jackson.core/jackson-databind/pom 2.22.3 SHA-256/27a5c6657b955d088daf586f2be9769224e33618ab4f44682a1b36ed98f7b904
 * @jenesis.pin maven/com.fasterxml.jackson.datatype/jackson-datatype-jsr310/pom 2.22.3 SHA-256/a3ad30d2892bb829d2d3c535f52379eeac6924f25c180feebd1f2e34c03b8682
 * @jenesis.pin maven/com.fasterxml.jackson.module/jackson-modules-java8/pom 2.22.3 SHA-256/19aa2e1fa4a541bd20815f110f80e9232bc3dee87d2492bb04e56cdfc4f28bb6
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-base/pom 2.22.3 SHA-256/1991ff35885b389eb29c23ae318c8eca66562915205bdeea53c722a5bd1b3bcb
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-bom/pom 2.22.3 SHA-256/8f499c6a8c017bb987a1a73b91f705c22fe24ec2777e7055c7a2a913f14b5440
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-parent/pom 2.22 SHA-256/7d45a7efb69df207bed7cc54b87a0bb012f10952c095570a273b49485da0d129
 * @jenesis.pin maven/com.fasterxml/oss-parent/pom 79 SHA-256/9206b7dd67fb138036576c3dfca947a2a8f3e2c4d2d9c1f2d1dea519b3a4dae1
 * @jenesis.pin maven/jakarta.annotation/jakarta.annotation-api/pom 3.0.0 SHA-256/9fc66a87375df8443aba6bdcc1d4ff07f126542583785a482a9262a19bfe8eae
 * @jenesis.pin maven/org.eclipse.ee4j/project/pom 1.0.9 SHA-256/825379934a1cf29249f349f2d18adaf79a7b2cb2dbe231518974e1ee70941c17
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.14.3 SHA-256/08a02856e487c9357f9b29e38745f8ae805848111e72d15aad0352338f1632e1
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.14.4 SHA-256/5706e8f29a0a07f56efbbea4a0670793414194bb8d24d8143ba1e787a2f32856
 * @jenesis.pin maven/org.openapitools/jackson-databind-nullable/pom 0.2.12 SHA-256/cb13594fd356c30aa8b76da2e2cdbdbe95e2f3dd52a9b698eec2510f152a8f4b
 * @jenesis.pin maven/tools.jackson/jackson-bom/pom 3.2.3 SHA-256/ad181b150b3f22bcf8ead7227d51ccc09e610c457c8bcc97d8958d2439ef16ec
 * @jenesis.pin openapi/module/build.jenesis 0.17.0 SHA-256/d3f83ea608953c2cb1745b75d34b66a0f0a89dc78944f11430f2cfc9bcbe6437
 * @jenesis.pin openapi/module/build.jenesis.plugin.openapi 0.1.0 SHA-256/1086b1157fc1beec07822dd97777f9af9f75847ee8db3ed8da09fe8e0f9b4c32
 * @jenesis.pin openapi:tool/maven/org.openapitools/openapi-generator-cli 7.26.0 SHA-256/1760050094997b9cc790cc1350be095f15da8c08a996b184d3eadc4ccda5e35e
 * @jenesis.pin org.openapitools.jackson.nullable 0.2.12 SHA-256/cdcf0d46f9d7f076be4298145616035123b76b2ae390ae61752c06b1c5068210
 * @jenesis.pin org.openapitools/jackson-databind-nullable 0.2.12 SHA-256/cdcf0d46f9d7f076be4298145616035123b76b2ae390ae61752c06b1c5068210
 */
module demo.contract.rest {
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires org.openapitools.jackson.nullable;
    requires jakarta.annotation;
    exports demo.contract.rest;
    exports demo.greeting;
    exports demo.greeting.model;
}
