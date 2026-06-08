package io.github.yoglappland.spectralization.optics.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPumpSources {
    public static final int CHARGED_GLOWSTONE_PUMP_RATE = 1;
    public static final double GLOWSTONE_SEED_RATE = 1.0;

    public static int adjacentPumpRate(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0;
        }

        int pumpRate = 0;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);

            if (isChargedGlowstone(level, neighborPos, level.getBlockState(neighborPos))) {
                pumpRate += CHARGED_GLOWSTONE_PUMP_RATE;
            }
        }

        return pumpRate;
    }

    public static double adjacentSeedRate(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0.0;
        }

        double seedRate = 0.0;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);

            if (level.getBlockState(neighborPos).is(Blocks.GLOWSTONE)) {
                seedRate += GLOWSTONE_SEED_RATE;
            }
        }

        return seedRate;
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
