package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.optics.field.OpticalFieldEffectType;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldInfluence;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalPropagationLoss {
    private static final double AIR_PROPAGATION_FACTOR = 0.995;
    private static final double INCOHERENT_FREE_SPACE_FACTOR = 0.60;
    private static final double INCOHERENT_BLOCK_FACTOR = 0.96;

    public static double factor(Level level, BlockPos pos, BeamPacket beam) {
        return mediumFactor(level, pos) * coherenceFactor(level, pos, beam);
    }

    private static double mediumFactor(Level level, BlockPos pos) {
        OpticalFieldInfluence fieldInfluence = OpticalFieldSources.influenceAt(level, pos);

        return fieldInfluence.has(OpticalFieldEffectType.SCATTERING)
                ? fieldInfluence.propagationFactor()
                : AIR_PROPAGATION_FACTOR;
    }

    private static double coherenceFactor(Level level, BlockPos pos, BeamPacket beam) {
        double totalPower = 0.0;
        double incoherentPower = 0.0;

        for (PlaneWaveComponent component : beam.components()) {
            totalPower += component.power();

            if (component.coherence() == CoherenceKind.INCOHERENT) {
                incoherentPower += component.power();
            }
        }

        if (totalPower <= 0.0) {
            return 1.0;
        }

        double incoherentFraction = incoherentPower / totalPower;
        double incoherentFactor = isFreeSpaceStep(level, pos)
                ? INCOHERENT_FREE_SPACE_FACTOR
                : INCOHERENT_BLOCK_FACTOR;

        return 1.0 - incoherentFraction * (1.0 - incoherentFactor);
    }

    private static boolean isFreeSpaceStep(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return true;
        }

        BlockState state = level.getBlockState(pos);

        return OpticalMaterialProfiles.isAirLike(state);
    }

    private OpticalPropagationLoss() {
    }
}
