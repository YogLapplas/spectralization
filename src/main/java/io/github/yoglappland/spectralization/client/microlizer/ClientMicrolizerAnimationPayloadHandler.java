package io.github.yoglappland.spectralization.client.microlizer;

import io.github.yoglappland.spectralization.network.MicrolizerAnimationPayload;

public final class ClientMicrolizerAnimationPayloadHandler {
    public static void handle(MicrolizerAnimationPayload payload) {
        ClientMicrolizerAnimationCache.accept(payload);
    }

    private ClientMicrolizerAnimationPayloadHandler() {
    }
}
