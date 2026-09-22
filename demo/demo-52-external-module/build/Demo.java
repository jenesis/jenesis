package build;

import module java.base;
import build.jenesis.Execution;
import build.jenesis.Project;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.ExternalModule;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModuleDescriptor;
import static build.jenesis.SequencedProperties.SYSTEM;

/**
 * The {@code ExternalModule} counterpart of the {@code ../internal-module} demo.
 * It does exactly the same thing - wraps the stock {@code InferredMultiProjectAssembler}
 * so a build module preprocesses the project's sources (a {@code ${greeting}}
 * substitution driven by the {@code org.json} dependency) before the regular
 * flow - but the build module is consumed as a published artifact rather than
 * compiled from local source.
 *
 * To stand in for that published artifact, {@code main} first stages the build
 * module: it builds {@code plugin/} as its own modular project into a nested
 * {@code target/} folder and reads the produced jar straight from the build's
 * structured result (a fixed coordinate path under {@code stage/modular}), then
 * serves it under a custom coordinate. The custom {@code Project} wires that
 * coordinate as an {@code ExternalModule}.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 *
 * which builds the project (the resolved plugin rewrites {@code ${greeting}}
 * first) and then launches the built module, printing the substituted greeting.
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("target"));
        Path modular = Project.ofKeys(SYSTEM, Path.of("."))
                .root(Path.of("plugin"))
                .target(Path.of("target", "plugin"))
                .version("1")
                .build("stage")
                .get("stage/modular");
        Path pluginJar = modular.resolve("demo.plugin").resolve("1").resolve("demo.plugin.jar");

        Repository local = (executor, coordinate, extension) -> {
            int slash = coordinate.indexOf('/');
            String module = slash < 0 ? coordinate : coordinate.substring(0, slash);
            return extension == null && module.equals("demo.plugin")
                    ? Optional.of(RepositoryItem.ofFile(pluginJar))
                    : Optional.empty();
        };
        Repository repository = JenesisModuleRepository.ofKeys(SYSTEM, JenesisRepository.Scope.MODULE).prepend(local);

        // Build the project (the resolved plugin rewrites ${greeting} first) and
        // launch the produced module so its main prints the substituted greeting.
        Project project = Project.ofKeys(SYSTEM, Path.of("."))
                .assembler(new PreprocessingAssembler(
                        InferredMultiProjectAssembler.ofKeys(SYSTEM),
                        Map.of("module", repository),
                        Map.of("module", ModularJarResolver.ofKeys(SYSTEM, true))));
        System.exit(new Execution(project).execute(args));
    }

    private record PreprocessingAssembler(
            MultiProjectAssembler<? super ProjectModuleDescriptor> delegate,
            Map<String, Repository> pluginRepositories,
            Map<String, Resolver> pluginResolvers)
            implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            SequencedSet<String> original = descriptor.sources();
            SequencedSet<String> manifests = descriptor.manifests();
            ProjectModuleDescriptor redirected = descriptor.sources("preprocess/substitute");
            return delegate.apply(redirected, repositories, resolvers).mapBuild(inner -> (sub, inherited) -> {
                ExternalModule preprocess = new ExternalModule(
                        "module/demo.plugin",               // the coordinate to resolve
                        "tool",                             // dependency group for the plugin's resolved closure
                        pluginRepositories,
                        pluginResolvers);
                sub.addModule("preprocess", preprocess, Stream.concat(original.stream(), manifests.stream()));
                inner.accept(sub, inherited);
            });
        }
    }
}
