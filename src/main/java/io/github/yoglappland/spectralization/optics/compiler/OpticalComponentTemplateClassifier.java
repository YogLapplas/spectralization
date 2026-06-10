package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.block.BeamProfilerBlock;
import io.github.yoglappland.spectralization.block.BeamSplitterBlock;
import io.github.yoglappland.spectralization.block.CmosSensorBlock;
import io.github.yoglappland.spectralization.block.CreativeLightSourceBlock;
import io.github.yoglappland.spectralization.block.DynamicMirrorBlock;
import io.github.yoglappland.spectralization.block.LensHolderBlock;
import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.block.PassThroughSensorBlock;
import io.github.yoglappland.spectralization.block.SpectrometerBlock;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalComponentTemplateClassifier {
    public static OpticalComponentTemplate classify(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return OpticalComponentTemplate.OPAQUE;
        }

        return classify(level, pos, level.getBlockState(pos));
    }

    public static OpticalComponentTemplate classify(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (OpticalMaterialProfiles.isAirLike(state)) {
            return OpticalComponentTemplate.AIR_LIKE;
        }

        if (block instanceof CreativeLightSourceBlock) {
            return OpticalComponentTemplate.SOURCE;
        }

        if (block instanceof BeamSplitterBlock) {
            return OpticalComponentTemplate.BEAM_SPLITTER;
        }

        if (block instanceof DynamicMirrorBlock) {
            return OpticalComponentTemplate.DYNAMIC_MIRROR;
        }

        if (block instanceof MirrorBlock) {
            return OpticalComponentTemplate.MIRROR;
        }

        if (block instanceof LensHolderBlock) {
            return level.getBlockEntity(pos) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens()
                    ? OpticalComponentTemplate.LENS_HOLDER_LENSED
                    : OpticalComponentTemplate.LENS_HOLDER_EMPTY;
        }

        if (block instanceof CmosSensorBlock) {
            return OpticalComponentTemplate.CMOS_SENSOR;
        }

        if (block instanceof PassThroughSensorBlock) {
            return OpticalComponentTemplate.PASS_THROUGH_SENSOR;
        }

        if (block instanceof BeamProfilerBlock) {
            return OpticalComponentTemplate.BEAM_PROFILER;
        }

        if (block instanceof SpectrometerBlock) {
            return OpticalComponentTemplate.SPECTROMETER;
        }

        if (OpticalMaterialProfiles.isScatteringMarker(state)) {
            return OpticalComponentTemplate.SCATTERING_FIELD;
        }

        if (OpticalMaterialProfiles.isExplicitOpticalMaterial(state)) {
            return OpticalComponentTemplate.OPTICAL_MATERIAL;
        }

        if (block instanceof OpticalElement) {
            return OpticalComponentTemplate.OTHER_OPTICAL_ELEMENT;
        }

        return OpticalComponentTemplate.OPAQUE;
    }

    private OpticalComponentTemplateClassifier() {
    }
}
