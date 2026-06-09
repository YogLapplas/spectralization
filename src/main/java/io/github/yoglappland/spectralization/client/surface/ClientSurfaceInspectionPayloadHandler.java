package io.github.yoglappland.spectralization.client.surface;

import io.github.yoglappland.spectralization.network.SurfaceCoatingOverlayPayload;
import io.github.yoglappland.spectralization.network.SurfaceInspectionResponsePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSurfaceInspectionPayloadHandler {
    public static void handle(SurfaceInspectionResponsePayload payload) {
        ClientSurfaceInspectionCache.accept(payload);
    }

    public static void handleOverlay(SurfaceCoatingOverlayPayload payload) {
        ClientSurfaceInspectionCache.acceptOverlay(payload.surfaces());
    }

    private ClientSurfaceInspectionPayloadHandler() {
    }
}
