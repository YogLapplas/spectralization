package io.github.yoglappland.spectralization.optics;

import java.util.List;
import java.util.Objects;

public record OpticalResult(List<OutputBeam> outputs, double absorbedPower, double generatedHeat) {
    public OpticalResult {
        Objects.requireNonNull(outputs, "outputs");

        outputs = List.copyOf(outputs);

        if (!Double.isFinite(absorbedPower) || absorbedPower < 0.0) {
            throw new IllegalArgumentException("Absorbed power must be finite and non-negative");
        }

        if (!Double.isFinite(generatedHeat) || generatedHeat < 0.0) {
            throw new IllegalArgumentException("Generated heat must be finite and non-negative");
        }
    }

    public static OpticalResult empty() {
        return new OpticalResult(List.of(), 0.0, 0.0);
    }

    public static OpticalResult absorbed(double absorbedPower) {
        return new OpticalResult(List.of(), absorbedPower, absorbedPower);
    }

    public static OpticalResult single(OutputBeam output, double absorbedPower, double generatedHeat) {
        return new OpticalResult(List.of(output), absorbedPower, generatedHeat);
    }
}
