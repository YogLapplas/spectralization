package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.block.StrayLightEmitterBlock;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.cache.OpticalDirtyKind;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StrayLightEmitterBlockEntity extends BlockEntity {
    private long lastSampleSignature = Long.MIN_VALUE;

    public StrayLightEmitterBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.STRAY_LIGHT_EMITTER.get(), pos, blockState);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (this.level == null || this.level.isClientSide) {
            return;
        }

        updateSource(this.level, this.worldPosition, this.getBlockState());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, StrayLightEmitterBlockEntity emitter) {
        if (level.isClientSide) {
            return;
        }

        emitter.updateSource(level, pos, state);
    }

    private void updateSource(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof StrayLightEmitterBlock emitterBlock)) {
            return;
        }

        long signature = emitterBlock.sampleSignature(state, level, pos);

        if (signature != lastSampleSignature) {
            lastSampleSignature = signature;
            OpticalTraceCache.markChanged(level, pos, OpticalDirtyKind.SOURCE);
            setChanged();
        }

        if (!(state.getBlock() instanceof OpticalSource opticalSource)) {
            return;
        }

        for (OutputBeam outputBeam : opticalSource.getOutputBeams(state, level, pos)) {
            OpticalTraceCache.requestOrApply(level, pos, outputBeam);
        }
    }
}
