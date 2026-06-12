package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public record SpatialTransformContext(
        Level level,
        BlockPos pos,
        BlockState state,
        FrequencyKey frequency,
        CoherenceKind coherence,
        Direction incomingDirection,
        Direction outgoingDirection,
        double distance,
        boolean feedbackPath
) {
    public SpatialTransformContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(coherence, "coherence");
        Objects.requireNonNull(incomingDirection, "incomingDirection");
        Objects.requireNonNull(outgoingDirection, "outgoingDirection");
        pos = pos.immutable();

        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("Spatial transform distance must be finite and non-negative");
        }
    }
}
