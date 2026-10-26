package build.jenesis.test.project;

import module java.base;
import java.lang.classfile.Annotation;
import java.lang.classfile.Signature;
import module org.junit.jupiter.api;
import build.jenesis.project.TestSelection;

import static org.assertj.core.api.Assertions.assertThat;

public class TestSelectionTest {

    @Test
    public void selects_direct_and_transitive_dependents() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.Leaf", reference("a.Leaf"));
        classes.put("a.Middle", reference("a.Middle", "a.Leaf"));
        classes.put("a.Top", reference("a.Top", "a.Middle"));
        classes.put("a.Unrelated", reference("a.Unrelated"));

        assertThat(TestSelection.of(classes).impacted(List.of("a.Leaf")))
                .containsExactlyInAnyOrder("a.Leaf", "a.Middle", "a.Top");
    }

    @Test
    public void selects_subclasses_through_the_superclass_reference() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.Base", reference("a.Base"));
        classes.put("a.Sub", extending("a.Sub", "a.Base"));

        assertThat(TestSelection.of(classes).impacted(List.of("a.Base")))
                .containsExactlyInAnyOrder("a.Base", "a.Sub");
    }

    @Test
    public void leaves_a_change_without_dependents_to_itself() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.Independent", reference("a.Independent"));
        classes.put("a.User", reference("a.User", "a.Independent"));

        assertThat(TestSelection.of(classes).impacted(List.of("a.User")))
                .containsExactly("a.User");
    }

    @Test
    public void ignores_references_outside_the_project() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.Only", reference("a.Only", "java.lang.String"));

        assertThat(TestSelection.of(classes).impacted(List.of("java.lang.String"))).isEmpty();
        assertThat(TestSelection.of(classes).impacted(List.of("a.Only"))).containsExactly("a.Only");
    }

    @Test
    public void selects_a_class_that_names_a_change_only_as_an_annotation_value() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.HStoreType", reference("a.HStoreType"));
        classes.put("a.Type", reference("a.Type"));
        classes.put("a.Entity", TestSelection.references(ClassFile.of().build(ClassDesc.of("a.Entity"),
                builder -> builder.withField("field",
                        ClassDesc.of("java.util.Map"),
                        field -> field.withFlags(ClassFile.ACC_PRIVATE).with(RuntimeVisibleAnnotationsAttribute.of(
                                Annotation.of(ClassDesc.of("a.Type"),
                                        AnnotationElement.of("value", AnnotationValue.ofClass(ClassDesc.of("a.HStoreType"))))))))));

        assertThat(TestSelection.of(classes).impacted(List.of("a.HStoreType")))
                .as("@Type(HStoreType.class) stores the class as a descriptor, not as a class of the constant pool")
                .containsExactlyInAnyOrder("a.HStoreType", "a.Entity");
        assertThat(TestSelection.of(classes).impacted(List.of("a.Type")))
                .as("the annotation's own type is a descriptor too")
                .containsExactlyInAnyOrder("a.Type", "a.Entity");
    }

    @Test
    public void selects_a_class_that_names_a_change_only_as_a_type_argument() {
        Map<String, Set<String>> classes = new LinkedHashMap<>();
        classes.put("a.Item", reference("a.Item"));
        classes.put("a.Holder", TestSelection.references(ClassFile.of().build(ClassDesc.of("a.Holder"),
                builder -> builder.withField("field",
                        ClassDesc.of("java.util.List"),
                        field -> field.withFlags(ClassFile.ACC_PRIVATE)
                                .with(SignatureAttribute.of(Signature.parseFrom("Ljava/util/List<La/Item;>;")))))));

        assertThat(TestSelection.of(classes).impacted(List.of("a.Item")))
                .containsExactlyInAnyOrder("a.Item", "a.Holder");
    }

    private static Set<String> reference(String name, String... fieldTypes) {
        return TestSelection.references(ClassFile.of().build(ClassDesc.of(name), builder -> {
            int index = 0;
            for (String fieldType : fieldTypes) {
                builder.withField("field" + index++, ClassDesc.of(fieldType), ClassFile.ACC_PRIVATE);
            }
        }));
    }

    private static Set<String> extending(String name, String superName) {
        return TestSelection.references(
                ClassFile.of().build(ClassDesc.of(name), builder -> builder.withSuperclass(ClassDesc.of(superName))));
    }
}
