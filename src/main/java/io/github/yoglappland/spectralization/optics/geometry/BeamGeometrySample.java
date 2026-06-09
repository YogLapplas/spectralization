package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import java.util.Objects;

public record BeamGeometrySample(
        BeamEnvelope envelope,
        double spotArea,
        double irradiance,
        int visualLevel
) {
    public BeamGeometrySample {
        Objects.requireNonNull(envelope, "envelope");

        if (!Double.isFinite(spotArea) || spotArea < 0.0) {
            throw new IllegalArgumentException("Spot area must be finite and non-negative");
        }

        if (!Double.isFinite(irradiance) || irradiance < 0.0) {
            throw new IllegalArgumentException("Irradiance must be finite and non-negative");
        }

        if (visualLevel < 0) {
            throw new IllegalArgumentException("Visual level must not be negative");
        }
    }
}
