package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Project;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectMainTest {

    private static final Map<Class<?>, List<String>> INVOKED = new ConcurrentHashMap<>();

    private final List<String> errors = new ArrayList<>();

    @TempDir
    private Path root;

    @Test
    public void runs_a_package_private_static_main_with_the_selectors_as_arguments() {
        assertThat(run(PackagePrivateStatic.class, "first", "second")).isEqualTo(0);
        assertThat(INVOKED.get(PackagePrivateStatic.class)).containsExactly("first", "second");
    }

    @Test
    public void runs_an_instance_main_without_parameters_on_a_new_instance() {
        assertThat(run(InstanceWithoutParameters.class, "ignored")).isEqualTo(0);
        assertThat(INVOKED.get(InstanceWithoutParameters.class)).isEmpty();
    }

    @Test
    public void prefers_a_main_taking_arguments_over_one_taking_none() {
        assertThat(run(BothForms.class, "argument")).isEqualTo(0);
        assertThat(INVOKED.get(BothForms.class)).containsExactly("argument");
    }

    @Test
    public void runs_an_instance_main_inherited_from_a_superclass() {
        assertThat(run(Inheriting.class, "argument")).isEqualTo(0);
        assertThat(INVOKED.get(Inheriting.class)).containsExactly("argument");
    }

    @Test
    public void runs_a_main_inherited_as_a_default_method_of_an_interface() {
        assertThat(run(Defaulting.class)).isEqualTo(0);
        assertThat(INVOKED.get(Defaulting.class)).isEmpty();
    }

    @Test
    public void runs_a_static_main_inherited_from_a_superclass() {
        assertThat(run(InheritingStatic.class, "argument")).isEqualTo(0);
        assertThat(INVOKED.get(InheritedStatic.class)).containsExactly("argument");
    }

    @Test
    public void runs_the_main_without_parameters_where_the_one_taking_arguments_is_private() {
        assertThat(run(PrivateWithArguments.class, "ignored")).isEqualTo(0);
        assertThat(INVOKED.get(PrivateWithArguments.class)).isEmpty();
    }

    @Test
    public void refuses_a_main_that_returns_a_value_as_java_does() {
        assertThat(run(Returning.class)).isEqualTo(1);
        assertThat(INVOKED).doesNotContainKey(Returning.class);
        assertThat(errors).anyMatch(line -> line.contains("declares or inherits no main method"));
    }

    @Test
    public void reports_what_the_main_method_throws_as_it_was_thrown() {
        assertThat(run(Throwing.class)).isEqualTo(1);
        assertThat(errors.getFirst()).isEqualTo(IllegalStateException.class.getName() + ": thrown by main");
    }

    @Test
    public void refuses_a_class_whose_only_main_is_private() {
        assertThat(run(PrivateOnly.class)).isEqualTo(1);
        assertThat(INVOKED).doesNotContainKey(PrivateOnly.class);
        assertThat(errors).anyMatch(line -> line.contains("declares or inherits no main method"));
    }

    @Test
    public void refuses_an_instance_main_whose_class_has_no_constructor_it_can_call() {
        assertThat(run(PrivateConstructor.class)).isEqualTo(1);
        assertThat(INVOKED).doesNotContainKey(PrivateConstructor.class);
        assertThat(errors).anyMatch(line -> line.contains("no non-private constructor without parameters"));
    }

    @Test
    public void refuses_an_instance_main_whose_class_is_abstract() {
        assertThat(run(Abstract.class)).isEqualTo(1);
        assertThat(INVOKED).doesNotContainKey(Abstract.class);
        assertThat(errors).anyMatch(line -> line.contains("is abstract or an interface"));
    }

    private int run(Class<?> type, String... selectors) {
        return Project.run(new Environment(Map.of()).err(errors::add),
                type.getName(),
                root,
                new LinkedHashSet<>(),
                selectors);
    }

    static final class PackagePrivateStatic {

        static void main(String[] args) {
            INVOKED.put(PackagePrivateStatic.class, List.of(args));
        }
    }

    static class InstanceWithoutParameters {

        void main() {
            INVOKED.put(getClass(), List.of());
        }
    }

    static class BothForms {

        void main() {
            INVOKED.put(getClass(), List.of());
        }

        static void main(String[] args) {
            INVOKED.put(BothForms.class, List.of(args));
        }
    }

    static class Inherited {

        protected void main(String[] args) {
            INVOKED.put(getClass(), List.of(args));
        }
    }

    static class Inheriting extends Inherited {
    }

    static class InheritedStatic {

        static void main(String[] args) {
            INVOKED.put(InheritedStatic.class, List.of(args));
        }
    }

    static class InheritingStatic extends InheritedStatic {
    }

    static class PrivateWithArguments {

        private static void main(String[] args) {
            INVOKED.put(PrivateWithArguments.class, List.of(args));
        }

        void main() {
            INVOKED.put(getClass(), List.of());
        }
    }

    static class Returning {

        static int main(String[] args) {
            INVOKED.put(Returning.class, List.of(args));
            return 0;
        }
    }

    static class Throwing {

        static void main() {
            throw new IllegalStateException("thrown by main");
        }
    }

    interface Launchable {

        default void main() {
            INVOKED.put(getClass(), List.of());
        }
    }

    static class Defaulting implements Launchable {
    }

    static class PrivateOnly {

        private static void main(String[] args) {
            INVOKED.put(PrivateOnly.class, List.of(args));
        }
    }

    abstract static class Abstract {

        void main() {
            INVOKED.put(getClass(), List.of());
        }
    }

    static class PrivateConstructor {

        private PrivateConstructor() {
        }

        void main() {
            INVOKED.put(getClass(), List.of());
        }
    }
}
