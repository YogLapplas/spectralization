package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.client.networkoverlay.ClientNetworkOverlayPayloadHandler;
import io.github.yoglappland.spectralization.client.spot.ClientSpotPayloadHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class SpectralNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SpotUpdatePayload.TYPE,
                SpotUpdatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSpotUpdate(payload))
        );

        registrar.playToClient(
                NetworkOverlayPayload.TYPE,
                NetworkOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleNetworkOverlay(payload))
        );
    }

    private static void handleSpotUpdate(SpotUpdatePayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSpotPayloadHandler.handle(payload);
        }
    }

    private static void handleNetworkOverlay(NetworkOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientNetworkOverlayPayloadHandler.handle(payload);
        }
    }

    private SpectralNetwork() {
    }
}
