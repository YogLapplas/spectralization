package io.github.yoglappland.spectralization.block;

import io.github.yoglappland.spectralization.blockentity.RubyBlockEntity;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RubyBlock extends Block implements EntityBlock {
    public static final FrequencyKey RUBY_LINE = FrequencyKey.visible(26);

    public RubyBlock(Properties properties) {
        super(properties);
    }

    public static int lightLevel(BlockState state) {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubyBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        refreshRuby(level, pos);
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
        refreshRuby(level, pos);
    }

    private static void refreshRuby(Level level, BlockPos pos) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RubyBlockEntity ruby) {
            ruby.refreshOutput();
        }
    }
}
