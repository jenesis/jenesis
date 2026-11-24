package build.jenesis;

public record BuildStepResult(boolean next, boolean cached) {

    public BuildStepResult() {
        this(true, true);
    }

    public BuildStepResult next(boolean next) {
        return new BuildStepResult(next, cached);
    }

    public BuildStepResult cached(boolean cached) {
        return new BuildStepResult(next, cached);
    }
}
