package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class DynamicMirrorBlock extends MirrorBlock implements OpticalTopologyProvider {
    private static final Set<Direction> HORIZONTAL_DIRECTIONS = EnumSet.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );

    public DynamicMirrorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Set<Direction> potentialOutgoingDirections(
            BlockState state,
            Level level,
            BlockPos pos,
            Direction incomingDirection
    ) {
        return incomingDirection.getAxis().isHorizontal() ? HORIZONTAL_DIRECTIONS : Set.of();
    }
}
