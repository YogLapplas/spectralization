package io.github.yoglappland.spectralization.optics.fiber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public interface FiberLikeFaceBlock {
    boolean isFiberLikeFace(BlockState state, LevelAccessor level, BlockPos pos, Direction face);
}
