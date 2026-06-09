package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Objects;
import net.minecraft.core.Direction;

public record SpatialTransformContext(
        FrequencyKey frequency,
        CoherenceKind coherence,
        Direction incomingDirection,
        Direction outgoingDirection,
        double distance,
        boolean feedbackPath
) {
    public SpatialTransformContext {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(coherence, "coherence");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(outgoingDirection, "outgoingDirection");

        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Spatial transform distance must be finite and non-negative");
        }
    }
}
