package io.github.yoglappland.spectralization.client.compact;

import io.github.yoglappland.spectralization.network.CompactMachineAnimationPayload;

public final class ClientCompactMachineAnimationPayloadHandler {
    public static void handle(CompactMachineAnimationPayload payload) {
        ClientCompactMachineAnimationCache.accept(payload);
    }

    private ClientCompactMachineAnimationPayloadHandler() {
    }
}
