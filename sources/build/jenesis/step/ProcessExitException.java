package build.jenesis.step;

import module java.base;

public class ProcessExitException extends IllegalStateException {

    public ProcessExitException(String message) {
        super(message);
    }
}
