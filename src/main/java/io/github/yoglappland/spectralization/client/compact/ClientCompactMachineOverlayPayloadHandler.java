package io.github.yoglappland.spectralization.client.compact;

import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;

public final class ClientCompactMachineOverlayPayloadHandler {
    public static void handle(CompactMachineOverlayPayload payload) {
        ClientCompactMachineOverlayCache.accept(payload);
    }

    private ClientCompactMachineOverlayPayloadHandler() {
    }
}
