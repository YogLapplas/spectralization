package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import java.util.Objects;

public record SpatialModeCoupling(
        BeamEnvelope orderedEnvelope,
        double orderedFraction,
        double strayFraction,
        SpatialDegradationReason reason
) {
    public static final SpatialModeCoupling IDEAL =
            new SpatialModeCoupling(BeamEnvelope.DEFAULT_COLLIMATED, 1.0, 0.0, SpatialDegradationReason.NONE);

    public SpatialModeCoupling {
        Objects.requireNonNull(orderedEnvelope, "orderedEnvelope");
        Objects.requireNonNull(reason, "reason");

        if (!Double.isFinite(orderedFraction) || orderedFraction < 0.0 || orderedFraction > 1.0) {
            throw new IllegalArgumentException("Ordered fraction must be finite and between 0 and 1");
        }

        if (!Double.isFinite(strayFraction) || strayFraction < 0.0 || strayFraction > 1.0) {
            throw new IllegalArgumentException("Stray fraction must be finite and between 0 and 1");
        }

        if (orderedFraction + strayFraction > 1.0 + 1.0E-9) {
            throw new IllegalArgumentException("Mode fractions must not exceed 1");
        }
    }

    public static SpatialModeCoupling ordered(BeamEnvelope envelope) {
        return new SpatialModeCoupling(envelope, 1.0, 0.0, SpatialDegradationReason.NONE);
    }

    public static SpatialModeCoupling degraded(
            BeamEnvelope envelope,
            double orderedFraction,
            SpatialDegradationReason reason
    ) {
        double clampedOrderedFraction = BeamGeometryOps.clamp01(orderedFraction);
        return new SpatialModeCoupling(
                envelope,
                clampedOrderedFraction,
                1.0 - clampedOrderedFraction,
                reason
        );
    }
}
