package io.github.yoglappland.spectralization.optics.compiler;

import java.util.Objects;

public record OpticalLocalScattering(
        OpticalComponentPort inputPort,
        OpticalComponentPort outputPort,
        double sampleInputPower,
        double sampleOutputPower
) {
    public OpticalLocalScattering {
        Objects.requireNonNull(inputPort, "inputPort");
        Objects.requireNonNull(outputPort, "outputPort");

        if (!Double.isFinite(sampleInputPower) || sampleInputPower < 0.0) {
            throw new IllegalArgumentException("Local scattering input power must be finite and non-negative");
        }

        if (!Double.isFinite(sampleOutputPower) || sampleOutputPower < 0.0) {
            throw new IllegalArgumentException("Local scattering output power must be finite and non-negative");
        }
    }
}
