package build;

import module java.base;
import build.jenesis.Execution;
import build.jenesis.Project;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.InternalModule;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.Environment;

/**
 * Like the {@code custom-assembler} demo, this wraps the stock
 * {@code InferredMultiProjectAssembler} so the project's Java sources are
 * preprocessed (a {@code ${greeting}} substitution) before the regular compile,
 * jar, and test flow runs. The difference: the preprocessing is not an inline
 * build step but a build module loaded from local source with
 * {@code InternalModule}, and that module uses an external dependency
 * ({@code org.json}) to drive the substitution.
 *
 * The three-argument {@code InternalModule} constructor wires the default
 * Jenesis repository with the local export (~/.jenesis) prepended, so both the
 * plugin's {@code build.jenesis} and {@code org.json} dependencies resolve from
 * there - the demo downloads nothing explicitly.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 *
 * which builds the project (the plugin rewrites {@code ${greeting}} first) and
 * then launches the built module, printing the substituted greeting.
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Project project = Project.ofEnvironment(Environment.SYSTEM, Path.of("."))
                .assembler(new PreprocessingAssembler(InferredMultiProjectAssembler.ofEnvironment(Environment.SYSTEM), Path.of("plugin")));
        // Build the project (running the substitution plugin) and launch the
        // produced module so its main prints the rewritten greeting - the result
        // the plugin set out to produce. Execute reads the build's inventory to
        // find the module and its runtime, so nothing is located by hand.
        System.exit(new Execution(project).execute(args));
    }

    private record PreprocessingAssembler(
            MultiProjectAssembler<? super ProjectModuleDescriptor> delegate,
            Path pluginSource)
            implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            SequencedSet<String> original = descriptor.sources();
            SequencedSet<String> manifests = descriptor.manifests();
            ProjectModuleDescriptor redirected = descriptor.sources("preprocess/substitute");
            return delegate.apply(redirected, repositories, resolvers).mapBuild(inner -> (sub, inherited) -> {
                InternalModule preprocess = new InternalModule(
                        "module",                           // resolution prefix for the plugin's requires
                        "tool",                             // dependency group for the plugin's resolved closure
                        pluginSource);
                sub.addModule("preprocess", preprocess, Stream.concat(original.stream(), manifests.stream()));
                inner.accept(sub, inherited);
            });
        }
    }
}
