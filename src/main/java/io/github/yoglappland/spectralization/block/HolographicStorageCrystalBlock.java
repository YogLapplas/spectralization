package io.github.yoglappland.spectralization.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class HolographicStorageCrystalBlock extends Block {
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");
    public static final BooleanProperty ERROR = BooleanProperty.create("error");

    public HolographicStorageCrystalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(LINKED, false)
                .setValue(ERROR, false));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction direction) {
        return adjacentBlockState.is(this) || super.skipRendering(state, adjacentBlockState, direction);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshVisualState(level, pos);
        refreshNearbyStorageVisuals(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())) {
            refreshNearbyStorageVisuals(level, pos);
        }
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
        refreshVisualState(level, pos);
        refreshNearbyStorageVisuals(level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED, ERROR);
    }

    public static void refreshNearbyStorageVisuals(Level level, BlockPos origin) {
        HolographicStorageMultiblock.scheduleRefresh(level, origin, "storage visual refresh requested");
    }

    public static void refreshVisualState(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HolographicStorageCrystalBlock)) {
            return;
        }

        boolean linked = !(state.getBlock() instanceof HolographicStorageMainCoreBlock)
                && HolographicStorageMultiblock.isRecognizedCrystal(level, pos);
        BlockState updatedState = state
                .setValue(LINKED, linked);

        if (!state.equals(updatedState)) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
        }
    }
}
