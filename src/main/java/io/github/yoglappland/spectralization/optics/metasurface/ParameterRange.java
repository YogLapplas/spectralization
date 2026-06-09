package io.github.yoglappland.spectralization.optics.metasurface;

public record ParameterRange(double minInclusive, double maxInclusive) {
    public ParameterRange {
        if (!Double.isFinite(minInclusive) || !Double.isFinite(maxInclusive)) {
            throw new IllegalArgumentException("Parameter range bounds must be finite");
        }

        if (minInclusive > maxInclusive) {
            throw new IllegalArgumentException("Parameter range min must not exceed max");
        }
    }

    public boolean contains(double value) {
        return Double.isFinite(value) && value >= minInclusive && value <= maxInclusive;
    }
}
