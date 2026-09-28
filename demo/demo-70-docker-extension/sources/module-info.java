/**
 * @jenesis.release 25
 * @jenesis.pin docker/maven/org.slf4j/slf4j-api 2.0.19 SHA-256/e91ff6d720609e7a194ffe758c3ed5c84e798617ae07b0a0f6a4fe229741b4bb
 * @jenesis.pin docker/module/org.slf4j 2.0.19 SHA-256/e91ff6d720609e7a194ffe758c3ed5c84e798617ae07b0a0f6a4fe229741b4bb
 * @jenesis.pin org.slf4j.simple 2.0.19 SHA-256/1d20cbfc8f97a719112a72c6d4c2323259d179a7478d1f694b54409d412bb731
 * @jenesis.pin org.slf4j/slf4j-api 2.0.19 SHA-256/e91ff6d720609e7a194ffe758c3ed5c84e798617ae07b0a0f6a4fe229741b4bb
 * @jenesis.pin org.slf4j/slf4j-simple 2.0.19 SHA-256/1d20cbfc8f97a719112a72c6d4c2323259d179a7478d1f694b54409d412bb731
 */
module demo.docker.extension {
    requires org.slf4j.simple;
}
