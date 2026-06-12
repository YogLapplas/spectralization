package io.github.yoglappland.spectralization.heat;

import java.util.Objects;

public record PhotothermalCouplingResult(
        double inputPower,
        double absorbedOpticalPower,
        double heatPower,
        double spectralEfficiency,
        double radiusEfficiency,
        double uniformityEfficiency,
        double totalEfficiency,
        double beamRadius,
        double irradiance,
        PhotothermalCouplingState state
) {
    public static PhotothermalCouplingResult zero() {
        return new PhotothermalCouplingResult(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                PhotothermalCouplingState.NO_INPUT
        );
    }

    public PhotothermalCouplingResult {
        Objects.requireNonNull(state, "state");
        requireNonNegative(inputPower, "input power");
        requireNonNegative(absorbedOpticalPower, "absorbed optical power");
        requireNonNegative(heatPower, "heat power");
        requireFraction(spectralEfficiency, "spectral efficiency");
        requireFraction(radiusEfficiency, "radius efficiency");
        requireFraction(uniformityEfficiency, "uniformity efficiency");
        requireFraction(totalEfficiency, "total efficiency");
        requireNonNegative(beamRadius, "beam radius");
        requireNonNegative(irradiance, "irradiance");
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Photothermal " + label + " must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Photothermal " + label + " must be between 0 and 1");
        }
    }
}
