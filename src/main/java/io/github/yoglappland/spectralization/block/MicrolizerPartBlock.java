package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.microlizer.MicrolizerNetworkData;
import io.github.yoglappland.spectralization.microlizer.MicrolizerPartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MicrolizerPartBlock extends Block {
    public static final BooleanProperty ERROR = BooleanProperty.create("error");

    private final MicrolizerPartKind kind;

    public MicrolizerPartBlock(BlockBehaviour.Properties properties, MicrolizerPartKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ERROR, true));
    }

    public MicrolizerPartKind kind() {
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
            MicrolizerNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "microlizer part placed");
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            MicrolizerNetworkData.scheduleRefresh(serverLevel, pos.immutable(), "microlizer part removed");
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ERROR);
    }
}
