/**
 * Attaching Java agents to a module's own executions.
 *
 * This main module declares an entry point with {@code @jenesis.main} and, next
 * to it, attaches the OpenTelemetry Java agent with {@code @jenesis.attach}. The
 * agent is added as a {@code -javaagent} to the java command that runs this
 * module's main class, without ever joining the compile or runtime module path:
 * it is a pure observability layer, resolved in its own {@code agent} scope.
 *
 * @jenesis.release 25
 * @jenesis.main demo.agents.Application
 * @jenesis.attach io.opentelemetry.javaagent/opentelemetry-javaagent
 * @jenesis.pin io.opentelemetry.javaagent/opentelemetry-javaagent 2.30.0 SHA-256/9d6bc2ad8dd8fb7f730984988e57b8ac0a82d81c7b3b8ae795378718733a509d
 * @jenesis.pin maven/io.opentelemetry.javaagent/opentelemetry-javaagent/pom 2.30.0 SHA-256/0dc569cb2910594db7c75555d0a6c857c9149a03f060d77e24e676e4f5045f3e
 */
module demo.agents {
    exports demo.agents;
}
