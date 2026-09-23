package demo.natives.text;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public final class NativeText {

    private NativeText() {
    }

    public static boolean granted() {
        return NativeText.class.getModule().isNativeAccessEnabled();
    }

    public static long length(String text) {
        Linker linker = Linker.nativeLinker();
        MethodHandle strlen = linker.downcallHandle(
                linker.defaultLookup().find("strlen").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
        try (Arena arena = Arena.ofConfined()) {
            return (long) strlen.invokeExact(arena.allocateFrom(text));
        } catch (Throwable throwable) {
            throw new IllegalStateException(throwable);
        }
    }
}
