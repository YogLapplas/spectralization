package io.github.yoglappland.spectralization.optics.compiler;

public record SaturatingEdgeGain(double linearGain, double extraOutput) {
    public SaturatingEdgeGain {
        if (!Double.isFinite(linearGain) || linearGain < 0.0D) {
            throw new IllegalArgumentException("Saturating edge linear gain must be finite and non-negative");
        }

        if (!Double.isFinite(extraOutput) || extraOutput < 0.0D) {
            throw new IllegalArgumentException("Saturating edge extra output must be finite and non-negative");
        }
    }
}
