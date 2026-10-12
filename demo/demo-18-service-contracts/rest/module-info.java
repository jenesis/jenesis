/**
 * @jenesis.pin com.fasterxml.jackson.core/jackson-annotations 2.22 SHA-256/21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.22.3 SHA-256/8a501126a385b25841915d839508f8a66e2a0dbc8a6709d055ef3b3e852b094c
 * @jenesis.pin com.fasterxml.jackson.core/jackson-databind 2.22.3 SHA-256/556db5439e206114346043f68d200497dc96a0bca62a360a81784092ebd0e0a9
 * @jenesis.pin com.fasterxml.jackson.databind 2.22.3 SHA-256/556db5439e206114346043f68d200497dc96a0bca62a360a81784092ebd0e0a9
 * @jenesis.pin com.fasterxml.jackson.datatype.jsr310 2.22.3 SHA-256/ec7052ee48c9d873bb8148338a643f02cf89c01d035dd219411473030d6d2b22
 * @jenesis.pin com.fasterxml.jackson.datatype/jackson-datatype-jsr310 2.22.3 SHA-256/ec7052ee48c9d873bb8148338a643f02cf89c01d035dd219411473030d6d2b22
 * @jenesis.pin jakarta.annotation 3.0.0 SHA-256/b01f55552284cfb149411e64eabca75e942d26d2e1786b32914250e4330afaa2
 * @jenesis.pin jakarta.annotation/jakarta.annotation-api 3.0.0 SHA-256/b01f55552284cfb149411e64eabca75e942d26d2e1786b32914250e4330afaa2
 * @jenesis.pin openapi/maven/org.openapitools/openapi-generator-cli 7.26.0 SHA-256/1760050094997b9cc790cc1350be095f15da8c08a996b184d3eadc4ccda5e35e
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
