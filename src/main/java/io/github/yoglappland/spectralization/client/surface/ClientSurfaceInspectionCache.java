package io.github.yoglappland.spectralization.client.surface;

import io.github.yoglappland.spectralization.network.SurfaceInspectionResponsePayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSurfaceInspectionCache {
    private static final long TTL_MILLIS = 650L;

    private static SurfaceInspectionResponsePayload lastResponse;
    private static long expiresAtMillis;
    private static List<SurfaceInspectionResponsePayload> overlaySurfaces = List.of();
    private static long overlayExpiresAtMillis;

    public static void accept(SurfaceInspectionResponsePayload payload) {
        lastResponse = payload;
        expiresAtMillis = System.currentTimeMillis() + TTL_MILLIS;
    }

    public static void acceptOverlay(List<SurfaceInspectionResponsePayload> surfaces) {
        overlaySurfaces = List.copyOf(surfaces);
        overlayExpiresAtMillis = System.currentTimeMillis() + TTL_MILLIS;
    }

    public static SurfaceInspectionResponsePayload active(BlockPos pos, Direction side) {
        if (Minecraft.getInstance().level == null || lastResponse == null || expiresAtMillis < System.currentTimeMillis()) {
            return null;
        }

        if (!lastResponse.pos().equals(pos) || lastResponse.side() != side) {
            return null;
        }

        return lastResponse;
    }

    public static SurfaceInspectionResponsePayload active() {
        if (Minecraft.getInstance().level == null || lastResponse == null || expiresAtMillis < System.currentTimeMillis()) {
            return null;
        }

        return lastResponse;
    }

    public static List<SurfaceInspectionResponsePayload> activeSurfaces() {
        if (Minecraft.getInstance().level == null || overlayExpiresAtMillis < System.currentTimeMillis()) {
            overlaySurfaces = List.of();
            return List.of();
        }

        return overlaySurfaces;
    }

    public static void clear() {
        lastResponse = null;
        expiresAtMillis = 0L;
        overlaySurfaces = List.of();
        overlayExpiresAtMillis = 0L;
    }

    private ClientSurfaceInspectionCache() {
    }
}
