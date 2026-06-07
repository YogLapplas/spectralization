package io.github.yoglappland.spectralization.client.networkoverlay;

import io.github.yoglappland.spectralization.network.NetworkOverlayPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkOverlayPayloadHandler {
    public static void handle(NetworkOverlayPayload payload) {
        ClientNetworkOverlayCache.accept(payload.visible(), payload.positions());
    }

    private ClientNetworkOverlayPayloadHandler() {
    }
}
