package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.ExecuteTool;
import build.jenesis.JpxTool;
import build.jenesis.Json;
import build.jenesis.MakeTool;

import static org.assertj.core.api.Assertions.assertThat;

public class JbangCatalogTest {

    private static final Pattern RELEASE = Pattern.compile("build\\.jenesis:build\\.jenesis:(\\d+\\.\\d+\\.\\d+)");

    @Test
    public void every_tool_has_an_alias_named_after_its_main_class_that_runs_it_on_java_25() throws Exception {
        Map<?, ?> aliases = aliases();
        Map<String, String> tools = Stream.<ToolProvider>of(new MakeTool(), new ExecuteTool(), new JpxTool())
                .map(tool -> tool.getClass().getName().replaceFirst("Tool$", ""))
                .collect(Collectors.toMap(
                        main -> main.substring(main.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT),
                        Function.identity()));
        assertThat(aliases.keySet())
                .as("an alias is the name of the entry point it runs")
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
    public void every_alias_and_the_demo_script_resolve_one_release_of_the_published_artifact() throws IOException {
        Set<Object> references = aliases().values().stream()
                .map(alias -> ((Map<?, ?>) alias).get("script-ref"))
                .collect(Collectors.toSet());
        assertThat(references).as("a release moves every alias at once").hasSize(1);
        Matcher release = RELEASE.matcher(references.iterator().next().toString());
        assertThat(release.matches()).as("an alias names a release, which JBang resolves from Maven Central").isTrue();
        assertThat(Files.readAllLines(Path.of("demo", "demo-83-jbang", "build", "Demo.java")))
                .as("the demo's script builds with the release the catalog runs")
                .contains("//DEPS " + release.group());
    }

    private static Map<?, ?> aliases() throws IOException {
        Path catalog = Path.of("jbang-catalog.json");
        assertThat(catalog).as("the tests run from the root of the repository, beside its catalog").isRegularFile();
        return (Map<?, ?>) ((Map<?, ?>) Json.parse(Files.readString(catalog))).get("aliases");
    }
}
