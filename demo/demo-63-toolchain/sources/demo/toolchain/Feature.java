package demo.toolchain;

public class Feature {

    public static void main(String... arguments) {
        System.out.println(Runtime.version().feature());
    }
}
