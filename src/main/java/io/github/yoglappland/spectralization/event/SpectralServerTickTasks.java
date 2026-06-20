package io.github.yoglappland.spectralization.event;

import io.github.yoglappland.spectralization.block.HolographicStorageMultiblock;
import io.github.yoglappland.spectralization.compact.CompactMachineNetworkData;
import io.github.yoglappland.spectralization.compact.CompactMachineOverlayPublisher;
import io.github.yoglappland.spectralization.optics.cache.OpticalTraceCache;
import io.github.yoglappland.spectralization.optics.fiber.FiberOverlayPublisher;
import net.minecraft.server.MinecraftServer;

public final class SpectralServerTickTasks {
    private SpectralServerTickTasks() {
    }

    public static void runPostTick(MinecraftServer server) {
        OpticalTraceCache.processQueues(server);
        HolographicStorageMultiblock.processPendingRefreshes(server);
        CompactMachineNetworkData.processPendingRefreshes(server);
        FiberOverlayPublisher.publishToInterestedPlayers(server);
        CompactMachineOverlayPublisher.publishToPlayers(server);
    }
}
