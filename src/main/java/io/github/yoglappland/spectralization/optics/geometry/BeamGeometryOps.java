package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;

public final class BeamGeometryOps {
    private static final double MIN_RADIUS = 0.03125;
    private static final double MAX_REASONABLE_RADIUS = 16.0;
    private static final double DIFFRACTION_DIVERGENCE_SCALE = 0.0025;

    public static BeamGeometrySample sample(BeamEnvelope envelope, double power) {
        double area = spotArea(envelope);
        double irradiance = area <= 0.0 ? 0.0 : Math.max(0.0, power) / area;

        return new BeamGeometrySample(envelope, area, irradiance, visualLevel(irradiance));
    }

    public static BeamEnvelope propagate(BeamEnvelope envelope, double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Propagation distance must be finite and non-negative");
        }

        if (distance == 0.0 || envelope.model() == BeamModel.PLANE_WAVE) {
            return envelope;
        }

        double radius = envelope.radius();
        double divergence = effectiveDivergence(envelope);

        if (envelope.focusDistance() > 0.0 && distance < envelope.focusDistance()) {
            radius = Math.max(MIN_RADIUS, radius - divergence * distance);
        } else {
            double postFocusDistance = envelope.focusDistance() > 0.0
                    ? distance - envelope.focusDistance()
                    : distance;
            radius = radius + divergence * Math.max(0.0, postFocusDistance);
        }

        double scatter = clamp01(envelope.scatter() + divergence * distance * 0.01);

        return new BeamEnvelope(
                envelope.model(),
                Math.min(MAX_REASONABLE_RADIUS, Math.max(MIN_RADIUS, radius)),
                divergence,
                Math.max(0.0, envelope.focusDistance() - distance),
                envelope.beamQuality(),
                envelope.apertureFill(),
                scatter,
                envelope.modeM(),
                envelope.modeN()
        );
    }

    private static double effectiveDivergence(BeamEnvelope envelope) {
        if (envelope.model() == BeamModel.PLANE_WAVE) {
            return envelope.divergence();
        }

        double radius = Math.max(MIN_RADIUS, envelope.radius());
        double diffractionFloor = DIFFRACTION_DIVERGENCE_SCALE / radius;
        return Math.max(envelope.divergence(), diffractionFloor);
    }

    public static SpatialModeCoupling passivePropagationCoupling(BeamEnvelope envelope, double distance) {
        BeamEnvelope propagated = propagate(envelope, distance);
        double orderedFraction = clamp01(1.0 - propagated.scatter());
        return SpatialModeCoupling.degraded(
                propagated,
                orderedFraction,
                orderedFraction >= 1.0 ? SpatialDegradationReason.NONE : SpatialDegradationReason.PASSIVE_PROPAGATION
        );
    }

    public static BeamEnvelope degradeQuality(
            BeamEnvelope envelope,
            double beamQualityMultiplier,
            double extraScatter
    ) {
        if (!Double.isFinite(beamQualityMultiplier) || beamQualityMultiplier < 1.0) {
            throw new IllegalArgumentException("Beam quality multiplier must be finite and at least 1");
        }

        return new BeamEnvelope(
                envelope.model(),
                envelope.radius(),
                envelope.divergence(),
                envelope.focusDistance(),
                envelope.beamQuality() * beamQualityMultiplier,
                envelope.apertureFill(),
                clamp01(envelope.scatter() + extraScatter),
                envelope.modeM(),
                envelope.modeN()
        );
    }

    public static double spotArea(BeamEnvelope envelope) {
        double radius = Math.max(MIN_RADIUS, envelope.radius());
        return Math.PI * radius * radius;
    }

    public static int visualLevel(double irradiance) {
        if (!Double.isFinite(irradiance) || irradiance <= 0.0) {
            return 0;
        }

        return Math.min(8, 1 + (int) Math.floor(Math.log10(1.0 + irradiance) * 2.0));
    }

    public static int widthLevel(BeamEnvelope envelope) {
        double radius = Math.max(MIN_RADIUS, envelope.radius());
        return Math.min(8, Math.max(1, (int) Math.ceil(radius * 8.0)));
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }

    private BeamGeometryOps() {
    }
}
