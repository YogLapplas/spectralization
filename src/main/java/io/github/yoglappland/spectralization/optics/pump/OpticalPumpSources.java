package io.github.yoglappland.spectralization.optics.pump;

import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPumpSources {
    public static final int MAGMA_BLOCK_PUMP_RATE = 1;

    public static int adjacentPumpRate(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }

        int pumpRate = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            pumpRate += pumpRateFor(level, neighborPos, level.getBlockState(neighborPos));
        }

        return pumpRate;
    }

    public static int pumpRateFor(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null) {
            return 0;
        }

        return state.is(SpectralBlockTags.PUMP_SOURCE) ? MAGMA_BLOCK_PUMP_RATE : 0;
    }

    public static boolean isPumpSource(Level level, BlockPos pos, BlockState state) {
        return pumpRateFor(level, pos, state) > 0;
    }

    private OpticalPumpSources() {
    }
}
