package io.github.yoglappland.spectralization.optics;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class OpticalPathVisualization {
    private static final DustParticleOptions DEBUG_RED_LIGHT =
            new DustParticleOptions(new Vector3f(1.0F, 0.05F, 0.02F), 0.65F);

    public static boolean isEnabled() {
        return SpectralizationConfig.lightPathsVisible();
    }

    public static void setEnabled(boolean enabled) {
        SpectralizationConfig.setLightPathsVisible(enabled);
    }

    public static void spawn(Level level, BlockPos pos, BeamPacket beam) {
        if (!isEnabled() || !(level instanceof ServerLevel serverLevel) || beam.isEmpty()) {
            return;
        }

        if (level.getGameTime() % 2L != 0L) {
            return;
        }

        serverLevel.sendParticles(
                particleFor(beam),
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                1,
                0.02D,
                0.02D,
                0.02D,
                0.0D
        );
    }

    private static DustParticleOptions particleFor(BeamPacket beam) {
        return DEBUG_RED_LIGHT;
    }

    private OpticalPathVisualization() {
    }
}
