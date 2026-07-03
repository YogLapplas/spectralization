package io.github.yoglappland.spectralization.optics.pump;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.tag.SpectralBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPumpSources {
    public static final int RUBY_PUMP_CAP = 4;

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

    public static int effectiveAdjacentPumpRate(Level level, BlockPos pos, BlockState gainMediumState) {
        int rawPumpRate = adjacentPumpRate(level, pos);

        if (gainMediumState != null && gainMediumState.getBlock() == Spectralization.RUBY_BLOCK.get()) {
            return Math.min(RUBY_PUMP_CAP, rawPumpRate);
        }

        return rawPumpRate;
    }

    public static int pumpRateFor(Level level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return 0;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof OpticalPumpSource pumpSource) {
            return Math.max(0, pumpSource.pumpAmount());
        }

        return 0;
    }

    public static boolean isPumpSource(Level level, BlockPos pos, BlockState state) {
        return state != null && state.is(SpectralBlockTags.PUMP_SOURCE);
    }

    private OpticalPumpSources() {
    }
}
