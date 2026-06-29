package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.pump.OpticalPumpSources;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RubyBlockEntity extends BlockEntity {
    private int lastEffectivePumpRate = Integer.MIN_VALUE;

    public RubyBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.RUBY_BLOCK.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshOutput();

        if (this.level != null && !this.level.isClientSide) {
            OpticalTraceCache.requestIntrinsicSourcesNear(this.level, this.worldPosition);
        }
    }

    public static boolean refreshNear(LevelAccessor accessor, BlockPos center) {
        if (!(accessor instanceof Level level) || level.isClientSide) {
            return false;
        }

        boolean changed = false;

        if (level.getBlockEntity(center) instanceof RubyBlockEntity ruby) {
            changed |= ruby.refreshOutput();
        }

        for (Direction direction : Direction.values()) {
            BlockPos pos = center.relative(direction);

            if (level.getBlockEntity(pos) instanceof RubyBlockEntity ruby) {
                changed |= ruby.refreshOutput();
            }
        }

        return changed;
    }

    public boolean refreshOutput() {
        if (this.level == null || this.level.isClientSide) {
            return false;
        }

        int pumpRate = OpticalPumpSources.effectiveAdjacentPumpRate(
                this.level,
                this.worldPosition,
                this.getBlockState()
        );

        if (pumpRate != this.lastEffectivePumpRate) {
            this.lastEffectivePumpRate = pumpRate;
            this.setChanged();
            OpticalTraceCache.markChanged(this.level, this.worldPosition, OpticalDirtyKind.PARAMETER);
            return true;
        }

        return false;
    }
}
