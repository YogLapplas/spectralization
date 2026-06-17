package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record BeamVisualSegment(
        BlockPos from,
        BlockPos to,
        Direction direction,
        CoherenceKind coherence,
        BeamVisibilityKind visibilityKind,
        BeamGeometrySample geometry,
        double startRadius,
        double endRadius,
        double power,
        int colorRgb
) {
    public BeamVisualSegment {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(coherence, "coherence");
        Objects.requireNonNull(visibilityKind, "visibilityKind");
        Objects.requireNonNull(geometry, "geometry");

        from = from.immutable();
        to = to.immutable();

        if (!Double.isFinite(power) || power < 0.0) {
            throw new IllegalArgumentException("Visual segment power must be finite and non-negative");
        }

        if (!Double.isFinite(startRadius) || startRadius < 0.0) {
            throw new IllegalArgumentException("Visual segment start radius must be finite and non-negative");
        }

        if (!Double.isFinite(endRadius) || endRadius < 0.0) {
            throw new IllegalArgumentException("Visual segment end radius must be finite and non-negative");
        }

        if (colorRgb < 0 || colorRgb > 0xFFFFFF) {
            throw new IllegalArgumentException("Visual segment color must be a 24-bit RGB value");
        }
    }

    public boolean naturallyVisible() {
        return visibilityKind == BeamVisibilityKind.NATURAL_SCATTER
                || visibilityKind == BeamVisibilityKind.NATURAL_PLASMA
                || visibilityKind == BeamVisibilityKind.DEBUG;
    }
}
