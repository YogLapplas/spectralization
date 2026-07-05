package io.github.yoglappland.spectralization.optics.pump;

import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPumpSources {
    public static final int RUBY_PUMP_CAP = 4;

    public static double adjacentPumpRate(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return 0.0D;
        }

        double pumpRate = 0.0D;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            pumpRate += pumpRateFor(level, neighborPos, level.getBlockState(neighborPos));
        }

        return pumpRate;
    }

    public static double effectiveAdjacentPumpRate(Level level, BlockPos pos, BlockState gainMediumState) {
        return adjacentPumpRate(level, pos);
    }

    public static double pumpRateFor(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return 0.0D;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof OpticalPumpSource pumpSource) {
            return Math.max(0.0D, pumpSource.pumpAmount());
        }

        return 0.0D;
    }

    public static boolean isPumpSource(Level level, BlockPos pos, BlockState state) {
        return state != null && state.is(SpectralBlockTags.PUMP_SOURCE);
    }

    private OpticalPumpSources() {
    }
}
