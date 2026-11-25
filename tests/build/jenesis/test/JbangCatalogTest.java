package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.ExecuteTool;
import build.jenesis.JpxTool;
import build.jenesis.Json;
import build.jenesis.MakeTool;

import static org.assertj.core.api.Assertions.assertThat;

public class JbangCatalogTest {

    @Test
    public void every_tool_has_an_alias_of_its_name_that_runs_its_main_class_on_java_25() throws Exception {
        Map<?, ?> aliases = aliases();
        Map<String, String> tools = Stream.<ToolProvider>of(new MakeTool(), new ExecuteTool(), new JpxTool())
                .collect(Collectors.toMap(ToolProvider::name, tool -> tool.getClass().getName().replaceFirst("Tool$", "")));
        assertThat(aliases.keySet())
                .as("an alias answers to the name its command has on a command line")
                .isEqualTo(tools.keySet());
        for (Map.Entry<String, String> tool : tools.entrySet()) {
            Map<?, ?> alias = (Map<?, ?>) aliases.get(tool.getKey());
            assertThat(alias.get("main")).as("the main class of %s", tool.getKey()).isEqualTo(tool.getValue());
            assertThat(Modifier.isStatic(Class.forName(tool.getValue()).getMethod("main", String[].class).getModifiers()))
                    .as("%s is a class a JVM launches", tool.getValue())
                    .isTrue();
            assertThat(alias.get("java")).as("the JDK %s asks JBang for", tool.getKey()).isEqualTo("25+");
            assertThat(alias.get("description")).as("what jbang alias list says of %s", tool.getKey())
                    .asString()
                    .isNotBlank();
        }
    }

    @Test
    public void every_alias_resolves_one_release_of_the_published_artifact() throws IOException {
        Set<Object> references = aliases().values().stream()
                .map(alias -> ((Map<?, ?>) alias).get("script-ref"))
                .collect(Collectors.toSet());
        assertThat(references).as("a release moves every alias at once").hasSize(1);
        assertThat(references.iterator().next().toString())
                .as("an alias names a release, which JBang resolves from Maven Central")
                .matches("build\\.jenesis:build\\.jenesis:\\d+\\.\\d+\\.\\d+");
    }

    private static Map<?, ?> aliases() throws IOException {
        Path catalog = Path.of("jbang-catalog.json");
        assertThat(catalog).as("the tests run from the root of the repository, beside its catalog").isRegularFile();
        return (Map<?, ?>) ((Map<?, ?>) Json.parse(Files.readString(catalog))).get("aliases");
    }
}
