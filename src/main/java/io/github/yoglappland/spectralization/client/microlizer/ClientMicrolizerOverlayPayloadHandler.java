package io.github.yoglappland.spectralization.client.microlizer;

import io.github.yoglappland.spectralization.network.MicrolizerOverlayPayload;

public final class ClientMicrolizerOverlayPayloadHandler {
    public static void handle(MicrolizerOverlayPayload payload) {
        ClientMicrolizerOverlayCache.accept(payload);
    }

    private ClientMicrolizerOverlayPayloadHandler() {
    }
}
