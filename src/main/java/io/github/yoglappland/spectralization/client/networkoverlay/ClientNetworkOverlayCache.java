package io.github.yoglappland.spectralization.client.networkoverlay;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkOverlayCache {
    private static boolean visible;
    private static Set<BlockPos> positions = Set.of();

    public static void accept(boolean newVisible, Collection<BlockPos> newPositions) {
        visible = newVisible;

        if (!newVisible) {
            positions = Set.of();
            return;
        }

        positions = Set.copyOf(newPositions);
    }

    public static boolean isVisible() {
        return visible;
    }

    public static Collection<BlockPos> positions() {
        return visible ? positions : List.of();
    }

    private ClientNetworkOverlayCache() {
    }
}
