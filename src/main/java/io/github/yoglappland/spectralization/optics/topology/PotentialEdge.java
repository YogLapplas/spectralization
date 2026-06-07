package io.github.yoglappland.spectralization.optics.topology;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record PotentialEdge(
        BlockPos from,
        BlockPos to,
        Direction direction,
        int distance
) {
    public PotentialEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(direction, "direction");

        if (distance <= 0) {
            throw new IllegalArgumentException("Potential edge distance must be positive");
        }

        from = from.immutable();
        to = to.immutable();
    }
}
