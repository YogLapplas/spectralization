package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.client.beam.ClientBeamPathPayloadHandler;
import io.github.yoglappland.spectralization.client.networkoverlay.ClientNetworkOverlayPayloadHandler;
import io.github.yoglappland.spectralization.client.surface.ClientSurfaceInspectionPayloadHandler;
import io.github.yoglappland.spectralization.client.spot.ClientSpotPayloadHandler;
import io.github.yoglappland.spectralization.optics.surface.SurfaceCoatingData;
import io.github.yoglappland.spectralization.optics.surface.SurfaceKey;
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
                SpotOverlayPayload.TYPE,
                SpotOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSpotOverlay(payload))
        );

        registrar.playToClient(
                NetworkOverlayPayload.TYPE,
                NetworkOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleNetworkOverlay(payload))
        );

        registrar.playToClient(
                BeamPathOverlayPayload.TYPE,
                BeamPathOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleBeamPathOverlay(payload))
        );

        registrar.playToClient(
                SurfaceInspectionResponsePayload.TYPE,
                SurfaceInspectionResponsePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSurfaceInspectionResponse(payload))
        );

        registrar.playToClient(
                SurfaceCoatingOverlayPayload.TYPE,
                SurfaceCoatingOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSurfaceCoatingOverlay(payload))
        );

        registrar.playToServer(
                SurfaceInspectionRequestPayload.TYPE,
                SurfaceInspectionRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSurfaceInspectionRequest(payload, context.player()))
        );
    }

    private static void handleSpotUpdate(SpotUpdatePayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSpotPayloadHandler.handle(payload);
        }
    }

    private static void handleSpotOverlay(SpotOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSpotPayloadHandler.handle(payload);
        }
    }

    private static void handleNetworkOverlay(NetworkOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientNetworkOverlayPayloadHandler.handle(payload);
        }
    }

    private static void handleBeamPathOverlay(BeamPathOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBeamPathPayloadHandler.handle(payload);
        }
    }

    private static void handleSurfaceInspectionResponse(SurfaceInspectionResponsePayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSurfaceInspectionPayloadHandler.handle(payload);
        }
    }

    private static void handleSurfaceCoatingOverlay(SurfaceCoatingOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSurfaceInspectionPayloadHandler.handleOverlay(payload);
        }
    }

    private static void handleSurfaceInspectionRequest(
            SurfaceInspectionRequestPayload payload,
            net.minecraft.world.entity.player.Player player
    ) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }

        java.util.List<SurfaceInspectionResponsePayload> surfaces = SurfaceCoatingData.entriesNear(
                        serverPlayer.level(),
                        serverPlayer.blockPosition(),
                        8,
                        512
                )
                .stream()
                .map(entry -> SurfaceInspectionResponsePayload.of(entry.key().pos(), entry.key().side(), entry.treatmentKind()))
                .toList();
        contextReply(serverPlayer, new SurfaceCoatingOverlayPayload(surfaces));

        if (!payload.hasTarget()
                || serverPlayer.distanceToSqr(payload.pos().getX() + 0.5D, payload.pos().getY() + 0.5D, payload.pos().getZ() + 0.5D) > 64.0D) {
            return;
        }

        SurfaceKey key = new SurfaceKey(payload.pos(), payload.side());
        SurfaceInspectionResponsePayload response = SurfaceCoatingData.treatmentFor(serverPlayer.level(), key)
                .map(kind -> SurfaceInspectionResponsePayload.of(payload.pos(), payload.side(), kind))
                .orElseGet(() -> SurfaceInspectionResponsePayload.empty(payload.pos(), payload.side()));
        contextReply(serverPlayer, response);
    }

    private static void contextReply(
            net.minecraft.server.level.ServerPlayer player,
            net.minecraft.network.protocol.common.custom.CustomPacketPayload response
    ) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, response);
    }

    private SpectralNetwork() {
    }
}
