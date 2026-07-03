package io.github.yoglappland.spectralization.optics.compiler.gain;

public final class GainSchedulers {
    private static final GainScheduler NOOP = (level, graph) -> GainSchedule.none(graph);
    private static final GainScheduler MATERIAL_GAIN = new MaterialGainScheduler();

    public static GainScheduler noop() {
        return NOOP;
    }

    public static GainScheduler materialGain() {
        return MATERIAL_GAIN;
    }

    private GainSchedulers() {
    }
}
