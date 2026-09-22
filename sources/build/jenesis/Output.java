package build.jenesis;

import module java.base;

public record Output(Consumer<String> out, Consumer<String> err) {

    public Output() {
        this(System.out::println, System.err::println);
    }

    public Output(PrintWriter out, PrintWriter err) {
        this(flushed(out), flushed(err));
    }

    private static Consumer<String> flushed(PrintWriter writer) {
        return line -> {
            writer.println(line);
            writer.flush();
        };
    }

    public Output out(Consumer<String> out) {
        return new Output(out, err);
    }

    public Output err(Consumer<String> err) {
        return new Output(out, err);
    }
}
