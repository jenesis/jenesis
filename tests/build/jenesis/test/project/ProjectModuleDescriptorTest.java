package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;
import build.jenesis.PathPlacement;
import build.jenesis.maven.MavenProject.MavenModuleDescriptor;
import build.jenesis.project.ProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectModuleDescriptorTest {

    @Test
    public void defaults_to_running_tests_without_configuration_or_attached_jars() {
        ProjectModule base = new MavenModuleDescriptor("module-foo", Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Path.of("."));
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base);
        assertThat(descriptor.configuration()).isEmpty();
        assertThat(descriptor.test()).isTrue();
        assertThat(descriptor.source()).isFalse();
        assertThat(descriptor.documentation()).isFalse();
        assertThat(descriptor.pinning()).isNull();
        assertThat(descriptor.pathPlacement()).isEqualTo(PathPlacement.INFERRED);
    }

    @Test
    public void replaces_one_value_per_wither() {
        ProjectModule base = new MavenModuleDescriptor("module-foo", Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Path.of("."));
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base)
                .configuration(Path.of("config"))
                .pinning(Pinning.STRICT)
                .pathPlacement(PathPlacement.MODULE_PATH);
        assertThat(descriptor.configuration()).containsExactly(Path.of("config"));
        assertThat(descriptor.pinning()).isEqualTo(Pinning.STRICT);
        assertThat(descriptor.pathPlacement()).isEqualTo(PathPlacement.MODULE_PATH);
        assertThat(descriptor.test()).isTrue();
    }

    @Test
    public void carries_the_flags_unchanged() {
        ProjectModule base = new MavenModuleDescriptor("module-foo", Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Path.of("."));
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base).test(true).source(false).documentation(true);
        assertThat(descriptor.test()).isTrue();
        assertThat(descriptor.source()).isFalse();
        assertThat(descriptor.documentation()).isTrue();
    }

    @Test
    public void delegates_module_descriptor_accessors_to_base() {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>(List.of("module-bar"));
        ProjectModule base = new MavenModuleDescriptor("module-foo", dependencies, Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Path.of("."));
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base);
        assertThat(descriptor.name()).isEqualTo(base.name());
        assertThat(descriptor.dependencies()).isEqualTo(base.dependencies());
        assertThat(descriptor.sources()).isEqualTo(base.sources());
        assertThat(descriptor.resources()).isEqualTo(base.resources());
        assertThat(descriptor.manifests()).isEqualTo(base.manifests());
        assertThat(descriptor.artifacts()).isEqualTo(base.artifacts());
        assertThat(descriptor.spdx()).isEqualTo(base.spdx());
    }

    @Test
    public void to_inherited_prepends_one_parent_segment_per_call() {
        ProjectModule base = new MavenModuleDescriptor("module-foo", Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Collections.emptyNavigableSet(), Path.of("."));
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base).test(true).source(true).documentation(true);
        ProjectModuleDescriptor inherited = descriptor.toInherited();
        assertThat(inherited.name()).isEqualTo(base.name());
        assertThat(inherited.dependencies()).isEqualTo(base.dependencies());
        assertThat(inherited.test()).isTrue();
        assertThat(inherited.source()).isTrue();
        assertThat(inherited.documentation()).isTrue();
        assertThat(inherited.sources()).isEqualTo(prefixed(base.sources(), "../"));
        assertThat(inherited.resources()).isEqualTo(prefixed(base.resources(), "../"));
        assertThat(inherited.manifests()).isEqualTo(prefixed(base.manifests(), "../"));
        assertThat(inherited.artifacts()).isEqualTo(prefixed(base.artifacts(), "../"));
        ProjectModuleDescriptor twice = inherited.toInherited();
        assertThat(twice.sources()).isEqualTo(prefixed(base.sources(), "../../"));
        assertThat(twice.manifests()).isEqualTo(prefixed(base.manifests(), "../../"));
        assertThat(twice.artifacts()).isEqualTo(prefixed(base.artifacts(), "../../"));
    }

    private static SequencedSet<String> prefixed(SequencedSet<String> values, String prefix) {
        LinkedHashSet<String> prefixed = new LinkedHashSet<>();
        for (String value : values) {
            prefixed.add(prefix + value);
        }
        return prefixed;
    }
}
