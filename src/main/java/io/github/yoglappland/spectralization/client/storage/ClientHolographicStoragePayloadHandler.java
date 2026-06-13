package io.github.yoglappland.spectralization.client.storage;

import io.github.yoglappland.spectralization.menu.HolographicStorageCoreMenu;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
import io.github.yoglappland.spectralization.network.HolographicStorageSnapshotPayload;
import net.minecraft.client.Minecraft;

public final class ClientHolographicStoragePayloadHandler {
    public static void handle(HolographicStorageSnapshotPayload payload) {
        if (Minecraft.getInstance().player == null) {
            return;
        }

        if (Minecraft.getInstance().player.containerMenu instanceof HolographicStorageMenu menu) {
            menu.applySnapshot(payload);
        } else if (Minecraft.getInstance().player.containerMenu instanceof HolographicStorageCoreMenu menu) {
            menu.applySnapshot(payload);
        }
    }

    private ClientHolographicStoragePayloadHandler() {
    }
}
