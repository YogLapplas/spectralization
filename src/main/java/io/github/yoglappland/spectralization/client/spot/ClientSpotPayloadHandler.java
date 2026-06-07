package io.github.yoglappland.spectralization.client.spot;

import io.github.yoglappland.spectralization.network.SpotUpdatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSpotPayloadHandler {
    public static void handle(SpotUpdatePayload payload) {
        ClientSpotCache.accept(payload.toSpotRecord());
    }

    private ClientSpotPayloadHandler() {
    }
}
