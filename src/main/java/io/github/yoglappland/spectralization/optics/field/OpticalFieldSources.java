package io.github.yoglappland.spectralization.optics.field;

import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalFieldSources {
    private static final int MAX_CACHED_QUERIES_PER_LEVEL = 65_536;
    private static final Map<Level, FieldCache> CACHES = new WeakHashMap<>();

    public static OpticalFieldInfluence influenceAt(Level level, BlockPos pos) {
        if (!SpectralizationConfig.scatteringFieldEnabled()) {
            return OpticalFieldInfluence.NONE;
        }

        synchronized (CACHES) {
            FieldCache cache = CACHES.computeIfAbsent(level, ignored -> new FieldCache());
            return cache.influenceAt(level, pos);
        }
    }

    public static boolean hasEffect(Level level, BlockPos pos, OpticalFieldEffectType effectType) {
        return influenceAt(level, pos).has(effectType);
    }

    public static boolean isScatteringFieldSource(BlockState state) {
        return OpticalMaterialProfiles.isScatteringMarker(state);
    }

    public static void invalidate(LevelAccessor level) {
        if (!(level instanceof Level realLevel)) {
            return;
        }

        synchronized (CACHES) {
            CACHES.remove(realLevel);
        }
    }

    private static OpticalFieldInfluence computeInfluence(Level level, BlockPos center) {
        int radius = SpectralizationConfig.scatteringFieldRadius();

        if (radius <= 0) {
            return OpticalFieldInfluence.NONE;
        }

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    mutablePos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);

                    if (!level.isLoaded(mutablePos)) {
                        continue;
                    }

                    if (isScatteringFieldSource(level.getBlockState(mutablePos))) {
                        return OpticalFieldInfluence.scattering(
                                SpectralizationConfig.scatteringFieldPropagationFactor()
                        );
                    }
                }
            }
        }

        return OpticalFieldInfluence.NONE;
    }

    private static final class FieldCache {
        private final Map<BlockPos, OpticalFieldInfluence> influencesByPos = new HashMap<>();

        OpticalFieldInfluence influenceAt(Level level, BlockPos pos) {
            if (influencesByPos.size() > MAX_CACHED_QUERIES_PER_LEVEL) {
                influencesByPos.clear();
            }

            return influencesByPos.computeIfAbsent(
                    pos.immutable(),
                    immutablePos -> computeInfluence(level, immutablePos)
            );
        }
    }

    private OpticalFieldSources() {
    }
}
