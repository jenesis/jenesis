package build.jenesis.test.module;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.module.ModuleVersionNegotiator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModuleVersionNegotiatorTest {

    private static final ModuleVersionNegotiator.CompiledVersion RECORDED =
            new ModuleVersionNegotiator.CompiledVersion("1.0", "root");
    private static final ModuleVersionNegotiator.CompiledVersion DECLARED =
            new ModuleVersionNegotiator.CompiledVersion("2.0", "middle");

    @Test
    public void first_keeps_what_an_earlier_descriptor_recorded() {
        ModuleVersionNegotiator negotiator = ModuleVersionNegotiator.first().get();
        assertThat(negotiator.negotiate("shared", null, DECLARED)).isEqualTo(DECLARED);
        assertThat(negotiator.negotiate("shared", RECORDED, DECLARED)).isEqualTo(RECORDED);
    }

    @Test
    public void ignore_never_records_a_version() {
        ModuleVersionNegotiator negotiator = ModuleVersionNegotiator.ignore().get();
        assertThat(negotiator.negotiate("shared", null, DECLARED)).isNull();
        assertThat(negotiator.negotiate("shared", RECORDED, DECLARED)).isNull();
    }

    @Test
    public void fail_accepts_an_agreeing_version_and_rejects_a_disagreeing_one() {
        ModuleVersionNegotiator negotiator = ModuleVersionNegotiator.fail().get();
        assertThat(negotiator.negotiate("shared", null, DECLARED)).isEqualTo(DECLARED);
        assertThat(negotiator.negotiate("shared",
                RECORDED,
                new ModuleVersionNegotiator.CompiledVersion("1.0", "middle"))).isEqualTo(RECORDED);
        assertThatThrownBy(() -> negotiator.negotiate("shared", RECORDED, DECLARED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Conflicting compiled versions for module shared:"
                        + " root requires 1.0, middle requires 2.0");
    }

    @Test
    public void no_two_factories_supply_the_same_negotiator() throws IOException {
        Set<String> distinct = new HashSet<>();
        for (Supplier<ModuleVersionNegotiator> supplier : factories()) {
            distinct.add(HexFormat.of().formatHex(serialize(supplier)));
        }
        assertThat(distinct).hasSize(3);
    }

    @Test
    public void every_factory_survives_a_serialization_round_trip() throws Exception {
        for (Supplier<ModuleVersionNegotiator> supplier : factories()) {
            Object restored;
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(serialize(supplier)))) {
                restored = in.readObject();
            }
            assertThat(restored).isInstanceOf(Supplier.class);
            assertThat(((Supplier<?>) restored).get()).isInstanceOf(ModuleVersionNegotiator.class);
        }
    }

    private static List<Supplier<ModuleVersionNegotiator>> factories() {
        return List.of(ModuleVersionNegotiator.first(),
                ModuleVersionNegotiator.ignore(),
                ModuleVersionNegotiator.fail());
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }
}
