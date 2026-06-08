package io.github.yoglappland.spectralization.optics.compiler.gain;

public final class GainSchedulers {
    private static final GainScheduler NOOP = (level, graph) -> GainSchedule.none(graph);
    private static final GainScheduler STABLE_FEEDBACK = new StableFeedbackGainScheduler();

    public static GainScheduler noop() {
        return NOOP;
    }

    public static GainScheduler stableFeedback() {
        return STABLE_FEEDBACK;
    }

    private GainSchedulers() {
    }
}
