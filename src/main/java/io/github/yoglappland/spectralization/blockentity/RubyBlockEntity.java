package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.RubyBlock;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RubyBlockEntity extends BlockEntity {
    private static final int QUIET_TICKS_AFTER_PUMP_LOSS = 12;

    private int lastPumpRate;
    private double lastSeedRate;
    private int quietTicksRemaining;

    public RubyBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.RUBY_BLOCK.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, RubyBlockEntity ruby) {
        if (level.isClientSide) {
            return;
        }

        int pumpRate = OpticalPumpSources.adjacentPumpRate(level, pos);
        double seedRate = OpticalPumpSources.adjacentSeedRate(level, pos);

        if (pumpRate != ruby.lastPumpRate || !closeEnough(seedRate, ruby.lastSeedRate)) {
            ruby.lastPumpRate = pumpRate;
            ruby.lastSeedRate = seedRate;
            ruby.quietTicksRemaining = seedRate <= 0.0 ? QUIET_TICKS_AFTER_PUMP_LOSS : 0;
            ruby.setChanged();
            OpticalTraceCache.markChanged(level, pos, OpticalDirtyKind.SOURCE);
        }

        if (seedRate > 0.0 && level.getBlockState(pos).getBlock() instanceof RubyBlock rubyBlock) {
            for (OutputBeam outputBeam : rubyBlock.getOutputBeams(level.getBlockState(pos), level, pos)) {
                OpticalTraceCache.requestOrApply(level, pos, outputBeam);
            }
            return;
        }

        if (ruby.quietTicksRemaining > 0) {
            ruby.quietTicksRemaining--;

            for (OutputBeam outputBeam : RubyBlock.quietOutputBeams()) {
                OpticalTraceCache.requestOrApply(level, pos, outputBeam);
            }
        }
    }

    private static boolean closeEnough(double left, double right) {
        return Math.abs(left - right) <= Math.max(1.0E-6, Math.max(Math.abs(left), Math.abs(right)) * 1.0E-4);
    }
}
