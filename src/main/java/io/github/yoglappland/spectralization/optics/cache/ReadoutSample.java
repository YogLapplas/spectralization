package io.github.yoglappland.spectralization.optics.cache;

public record ReadoutSample(double power, boolean reliable, long step) {
    public ReadoutSample {
        if (!Double.isFinite(power) || power < 0.0D) {
            throw new IllegalArgumentException("Readout sample power must be finite and non-negative");
        }
    }
}
