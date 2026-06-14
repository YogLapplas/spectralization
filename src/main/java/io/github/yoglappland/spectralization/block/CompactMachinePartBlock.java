package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.compact.CompactMachinePartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class CompactMachinePartBlock extends Block {
    public static final BooleanProperty ERROR = BooleanProperty.create("error");

    private final CompactMachinePartKind kind;

    public CompactMachinePartBlock(BlockBehaviour.Properties properties, CompactMachinePartKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ERROR, true));
    }

    public CompactMachinePartKind kind() {
        return kind;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CompactMachineNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "compact part placed");
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CompactMachineNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "compact part removed");
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ERROR);
    }
}
