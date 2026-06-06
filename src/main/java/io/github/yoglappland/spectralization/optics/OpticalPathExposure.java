package io.github.yoglappland.spectralization.optics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class OpticalPathExposure {
    private static final int HOLD_TICKS = 2;
    private static final int CLEANUP_INTERVAL_TICKS = 20;
    private static final Map<ResourceKey<Level>, Map<BlockPos, Exposure>> EXPOSURES = new HashMap<>();

    public static void mark(Level level, BlockPos pos, BeamPacket beam) {
        if (level.isClientSide || beam.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        Map<BlockPos, Exposure> levelExposures = EXPOSURES.computeIfAbsent(level.dimension(), key -> new HashMap<>());

        if (gameTime % CLEANUP_INTERVAL_TICKS == 0L) {
            cleanup(levelExposures, gameTime);
        }

        levelExposures.put(pos.immutable(), new Exposure(gameTime, frequenciesOf(beam)));
    }

    public static boolean isEntityExposedTo(Level level, Entity entity, FrequencyKey frequency) {
        if (level.isClientSide) {
            return false;
        }

        Map<BlockPos, Exposure> levelExposures = EXPOSURES.get(level.dimension());

        if (levelExposures == null || levelExposures.isEmpty()) {
            return false;
        }

        long gameTime = level.getGameTime();
        AABB bounds = entity.getBoundingBox().inflate(0.05D);
        int minX = Mth.floor(bounds.minX);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ);
        int maxX = Mth.floor(bounds.maxX);
        int maxY = Mth.floor(bounds.maxY);
        int maxZ = Mth.floor(bounds.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            Exposure exposure = levelExposures.get(pos);

            if (exposure != null
                    && gameTime - exposure.gameTime() <= HOLD_TICKS
                    && exposure.frequencies().contains(frequency)) {
                return true;
            }
        }

        return false;
    }

    private static Set<FrequencyKey> frequenciesOf(BeamPacket beam) {
        return beam.components().stream()
                .map(PlaneWaveComponent::frequency)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static void cleanup(Map<BlockPos, Exposure> exposures, long gameTime) {
        exposures.entrySet().removeIf(entry -> gameTime - entry.getValue().gameTime() > HOLD_TICKS);
    }

    private record Exposure(long gameTime, Set<FrequencyKey> frequencies) {
    }

    private OpticalPathExposure() {
    }
}
