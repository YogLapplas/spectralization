package io.github.yoglappland.spectralization.blockentity;

import io.github.yoglappland.spectralization.optics.OpticalPathTracer;
import io.github.yoglappland.spectralization.optics.OpticalSource;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.registry.SpectralBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CreativeLightSourceBlockEntity extends BlockEntity {
    public CreativeLightSourceBlockEntity(BlockPos pos, BlockState blockState) {
        super(SpectralBlockEntities.CREATIVE_LIGHT_SOURCE.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, CreativeLightSourceBlockEntity source) {
        if (level.isClientSide || !(state.getBlock() instanceof OpticalSource opticalSource)) {
            return;
        }

        for (OutputBeam outputBeam : opticalSource.getOutputBeams(state, level, pos)) {
            OpticalPathTracer.trace(level, pos, outputBeam);
        }
    }
}
