package io.github.yoglappland.spectralization.microlizer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record MicrolizerConnection(
        BlockPos from,
        BlockPos to,
        Direction direction,
        long createdGameTime
) {
    public MicrolizerConnection {
        from = from.immutable();
        to = to.immutable();
    }

    public boolean touches(BlockPos pos) {
        return from.equals(pos) || to.equals(pos);
    }

    public Direction directionFrom(BlockPos pos) {
        if (from.equals(pos)) {
            return direction;
        }

        if (to.equals(pos)) {
            return direction.getOpposite();
        }

        throw new IllegalArgumentException("Position is not a microlizer connection endpoint");
    }
}
