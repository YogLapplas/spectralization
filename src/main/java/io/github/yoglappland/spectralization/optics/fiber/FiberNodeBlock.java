package io.github.yoglappland.spectralization.optics.fiber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public interface FiberNodeBlock {
    FiberNodeKind fiberNodeKind(BlockState state, LevelAccessor level, BlockPos pos);

    FiberNodeProfile fiberNodeProfile(BlockState state, LevelAccessor level, BlockPos pos);
}
