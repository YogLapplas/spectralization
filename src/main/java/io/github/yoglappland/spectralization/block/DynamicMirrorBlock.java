package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class DynamicMirrorBlock extends MirrorBlock implements OpticalTopologyProvider {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final Set<Direction> HORIZONTAL_DIRECTIONS = EnumSet.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );

    public DynamicMirrorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston
    ) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        updatePulseRotation(state, level, pos);
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }

    public static void updatePulseRotation(BlockState state, Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        boolean powered = level.hasNeighborSignal(pos);
        boolean wasPowered = state.getValue(POWERED);

        if (powered == wasPowered) {
            return;
        }

        BlockState nextState = state.setValue(POWERED, powered);
        boolean rotated = powered && !wasPowered;

        if (rotated) {
            nextState = rotateOnce(nextState);
        }

        level.setBlock(pos, nextState, 3);

        if (rotated) {
            OpticalTraceCache.markChanged(level, pos, OpticalDirtyKind.TOPOLOGY);
        }
    }
}
