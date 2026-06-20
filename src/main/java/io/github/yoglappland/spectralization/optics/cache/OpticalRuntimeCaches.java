package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import io.github.yoglappland.spectralization.optics.fiber.FiberNetworkIndex;
import io.github.yoglappland.spectralization.optics.topology.OpticalNetworkIndex;
import io.github.yoglappland.spectralization.optics.world.OpticalWorldIndex;
import net.minecraft.world.level.LevelAccessor;

public final class OpticalRuntimeCaches {
    private OpticalRuntimeCaches() {
    }

    public static void clearAll() {
        OpticalTraceCache.clearAll();
        OpticalWorldIndex.clearAll();
        OpticalNetworkIndex.clearAll();
        FiberNetworkIndex.clearAll();
        OpticalFieldSources.clearAll();
        OpticalSpotTracker.clearAll();
        HolographicStorageMultiblock.clearAll();
    }

    public static void clear(LevelAccessor level) {
        OpticalTraceCache.clear(level);
        OpticalWorldIndex.clear(level);
        OpticalNetworkIndex.clear(level);
        FiberNetworkIndex.clear(level);
        OpticalFieldSources.invalidate(level);
        OpticalSpotTracker.clear(level);
        HolographicStorageMultiblock.clear(level);
    }
}
