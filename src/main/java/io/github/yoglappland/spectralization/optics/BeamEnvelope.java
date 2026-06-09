package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record BeamEnvelope(
        BeamModel model,
        double radius,
        double divergence,
        double focusDistance,
        double beamQuality,
        double apertureFill,
        double scatter,
        int modeM,
        int modeN
) {
    public static final double IDEAL_BEAM_QUALITY = 1.0;
    public static final double DEFAULT_APERTURE_FILL = 1.0;
    public static final double DEFAULT_SCATTER = 0.0;
    public static final BeamEnvelope PLANE_WAVE = new BeamEnvelope(BeamModel.PLANE_WAVE, 0.0, 0.0, 0.0, 0, 0);
    public static final BeamEnvelope DEFAULT_COLLIMATED = collimated(0.25);

    public BeamEnvelope(
            BeamModel model,
            double radius,
            double divergence,
            double focusDistance,
            int modeM,
            int modeN
    ) {
        this(
                model,
                radius,
                divergence,
                focusDistance,
                IDEAL_BEAM_QUALITY,
                DEFAULT_APERTURE_FILL,
                DEFAULT_SCATTER,
                modeM,
                modeN
        );
    }

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

        if (!Double.isFinite(beamQuality) || beamQuality < 1.0) {
            throw new IllegalArgumentException("Beam quality must be finite and at least 1");
        }

        if (!Double.isFinite(apertureFill) || apertureFill < 0.0 || apertureFill > 1.0) {
            throw new IllegalArgumentException("Beam aperture fill must be finite and between 0 and 1");
        }

        if (!Double.isFinite(scatter) || scatter < 0.0 || scatter > 1.0) {
            throw new IllegalArgumentException("Beam scatter must be finite and between 0 and 1");
        }

        if (modeM < 0 || modeN < 0) {
            throw new IllegalArgumentException("Beam mode indices must not be negative");
        }
    }

    public static BeamEnvelope collimated(double radius) {
        return new BeamEnvelope(BeamModel.COLLIMATED, radius, 0.0, 0.0, 0, 0);
    }

    public BeamEnvelope withBeamQuality(double beamQuality) {
        return new BeamEnvelope(model, radius, divergence, focusDistance, beamQuality, apertureFill, scatter, modeM, modeN);
    }

    public BeamEnvelope withApertureFill(double apertureFill) {
        return new BeamEnvelope(model, radius, divergence, focusDistance, beamQuality, apertureFill, scatter, modeM, modeN);
    }

    public BeamEnvelope withScatter(double scatter) {
        return new BeamEnvelope(model, radius, divergence, focusDistance, beamQuality, apertureFill, scatter, modeM, modeN);
    }
}
