package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record BeamEnvelope(
        BeamModel model,
        double radius,
        double divergence,
        double focusDistance,
        int modeM,
        int modeN
) {
    public static final BeamEnvelope PLANE_WAVE = new BeamEnvelope(BeamModel.PLANE_WAVE, 0.0, 0.0, 0.0, 0, 0);
    public static final BeamEnvelope DEFAULT_COLLIMATED = collimated(0.25);

    public BeamEnvelope {
        Objects.requireNonNull(model, "model");

        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("Beam radius must be finite and non-negative");
        }

        if (!Double.isFinite(divergence) || divergence < 0.0) {
            throw new IllegalArgumentException("Beam divergence must be finite and non-negative");
        }

        if (!Double.isFinite(focusDistance) || focusDistance < 0.0) {
            throw new IllegalArgumentException("Beam focus distance must be finite and non-negative");
        }

        if (modeM < 0 || modeN < 0) {
            throw new IllegalArgumentException("Beam mode indices must not be negative");
        }
    }

    public static BeamEnvelope collimated(double radius) {
        return new BeamEnvelope(BeamModel.COLLIMATED, radius, 0.0, 0.0, 0, 0);
    }
}
