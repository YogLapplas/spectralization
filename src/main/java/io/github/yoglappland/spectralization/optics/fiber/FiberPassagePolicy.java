package io.github.yoglappland.spectralization.optics.fiber;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface FiberPassagePolicy {
    FiberPassagePolicy AIR_ONLY = (level, pos, state) -> state.isAir();

    boolean canPassThrough(LevelAccessor level, BlockPos pos, BlockState state);
}
