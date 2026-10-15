package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;

public record Language(SequencedSet<String> extensions,
                       Order order,
                       BuildExecutorModule compiler,
                       BuildExecutorModule documentation) {

    public enum Order {

        BEFORE_JAVAC, AFTER_JAVAC
    }
}
