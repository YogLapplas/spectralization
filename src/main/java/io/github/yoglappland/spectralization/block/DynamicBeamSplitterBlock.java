package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.optics.topology.OpticalTopologyProvider;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public class DynamicBeamSplitterBlock extends BeamSplitterBlock implements OpticalTopologyProvider {
    private static final Set<Direction> HORIZONTAL_DIRECTIONS = EnumSet.of(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    );

    public DynamicBeamSplitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(DynamicMirrorBlock.POWERED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context)
                .setValue(DynamicMirrorBlock.POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
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
        DynamicMirrorBlock.updatePulseRotation(state, level, pos);
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
        builder.add(DynamicMirrorBlock.POWERED);
    }
}
