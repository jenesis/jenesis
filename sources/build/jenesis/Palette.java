package build.jenesis;

import module java.base;

public enum Palette {

    ANSI, PLAIN;

    public static Palette ofEnvironment(Environment environment) {
        return environment.flag("print.color", true) ? ANSI : PLAIN;
    }

    public String reset() {
        return escape("0");
    }

    public String red() {
        return escape("31");
    }

    public String green() {
        return escape("32");
    }

    public String yellow() {
        return escape("33");
    }

    public String blue() {
        return escape("34");
    }

    public String cyan() {
        return escape("36");
    }

    public String color(int code) {
        return escape("38;5;" + code);
    }

    public String bold(int code) {
        return escape("1;38;5;" + code);
    }

    private String escape(String code) {
        return this == ANSI ? "\033[" + code + "m" : "";
    }
}
