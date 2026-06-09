package io.github.yoglappland.spectralization.client.beam;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;

public final class ClientBeamPathPayloadHandler {
    public static void handle(BeamPathOverlayPayload payload) {
        ClientBeamPathCache.accept(payload);
    }

    private ClientBeamPathPayloadHandler() {
    }
}
