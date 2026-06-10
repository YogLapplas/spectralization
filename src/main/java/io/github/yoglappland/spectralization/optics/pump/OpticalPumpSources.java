package io.github.yoglappland.spectralization.optics.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPumpSources {
    public static final int CHARGED_GLOWSTONE_PUMP_RATE = 1;
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

        if (state.is(Blocks.MAGMA_BLOCK)) {
            return MAGMA_BLOCK_PUMP_RATE;
        }

        return isChargedGlowstone(level, pos, state) ? CHARGED_GLOWSTONE_PUMP_RATE : 0;
    }

    public static boolean isPumpSource(Level level, BlockPos pos, BlockState state) {
        return pumpRateFor(level, pos, state) > 0;
    }

    public static boolean isChargedGlowstone(Level level, BlockPos pos, BlockState state) {
        return level != null
                && pos != null
                && state.is(Blocks.GLOWSTONE)
                && level.hasNeighborSignal(pos);
    }

    private OpticalPumpSources() {
    }
}
