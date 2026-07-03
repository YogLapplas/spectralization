package io.github.yoglappland.spectralization.network;

import io.github.yoglappland.spectralization.client.beam.ClientBeamPathPayloadHandler;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerAnimationPayloadHandler;
import io.github.yoglappland.spectralization.client.microlizer.ClientMicrolizerOverlayPayloadHandler;
import io.github.yoglappland.spectralization.client.networkoverlay.ClientNetworkOverlayPayloadHandler;
import io.github.yoglappland.spectralization.client.storage.ClientHolographicStoragePayloadHandler;
import io.github.yoglappland.spectralization.client.surface.ClientSurfaceInspectionPayloadHandler;
import io.github.yoglappland.spectralization.client.spot.ClientSpotPayloadHandler;
import io.github.yoglappland.spectralization.menu.CreativeLightSourceMenu;
import io.github.yoglappland.spectralization.menu.HolographicStorageMenu;
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

        registrar.playToClient(
                HolographicStorageSnapshotPayload.TYPE,
                HolographicStorageSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleHolographicStorageSnapshot(payload))
        );

        registrar.playToClient(
                MicrolizerOverlayPayload.TYPE,
                MicrolizerOverlayPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMicrolizerOverlay(payload))
        );

        registrar.playToClient(
                MicrolizerAnimationPayload.TYPE,
                MicrolizerAnimationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleMicrolizerAnimation(payload))
        );

        registrar.playToServer(
                SurfaceInspectionRequestPayload.TYPE,
                SurfaceInspectionRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleSurfaceInspectionRequest(payload, context.player()))
        );

        registrar.playToServer(
                CreativeLightSpectrumPayload.TYPE,
                CreativeLightSpectrumPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCreativeLightSpectrum(payload, context.player()))
        );

        registrar.playToServer(
                CreativeLightPowerPayload.TYPE,
                CreativeLightPowerPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleCreativeLightPower(payload, context.player()))
        );

        registrar.playToServer(
                HolographicStorageActionPayload.TYPE,
                HolographicStorageActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleHolographicStorageAction(payload, context.player()))
        );

        registrar.playToServer(
                StrawberryMovementInputPayload.TYPE,
                StrawberryMovementInputPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleStrawberryMovementInput(payload, context.player()))
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

    private static void handleHolographicStorageSnapshot(HolographicStorageSnapshotPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientHolographicStoragePayloadHandler.handle(payload);
        }
    }

    private static void handleMicrolizerOverlay(MicrolizerOverlayPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientMicrolizerOverlayPayloadHandler.handle(payload);
        }
    }

    private static void handleMicrolizerAnimation(MicrolizerAnimationPayload payload) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientMicrolizerAnimationPayloadHandler.handle(payload);
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

    private static void handleCreativeLightSpectrum(
            CreativeLightSpectrumPayload payload,
            net.minecraft.world.entity.player.Player player
    ) {
        if (!(player.containerMenu instanceof CreativeLightSourceMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }

        menu.setSpectrumWeight(payload.bin(), payload.weight(), payload.exclusive());
    }

    private static void handleCreativeLightPower(
            CreativeLightPowerPayload payload,
            net.minecraft.world.entity.player.Player player
    ) {
        if (!(player.containerMenu instanceof CreativeLightSourceMenu menu) || menu.containerId != payload.containerId()) {
            return;
        }

        menu.setPowerCenti(payload.powerCenti());
    }

    private static void handleHolographicStorageAction(
            HolographicStorageActionPayload payload,
            net.minecraft.world.entity.player.Player player
    ) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || !(player.containerMenu instanceof HolographicStorageMenu menu)
                || menu.containerId != payload.containerId()) {
            return;
        }

        menu.handleAction(payload, serverPlayer);
    }

    private static void handleStrawberryMovementInput(
            StrawberryMovementInputPayload payload,
            net.minecraft.world.entity.player.Player player
    ) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return;
        }

        if (payload.dashPressed()) {
            io.github.yoglappland.spectralization.movement.StrawberryMovementController.requestDash(serverPlayer);
            return;
        }

        io.github.yoglappland.spectralization.movement.StrawberryMovementController.updateInput(
                serverPlayer,
                payload.holdingRod(),
                payload.jumpDown(),
                payload.jumpPressed(),
                payload.leftImpulse(),
                payload.forwardImpulse()
        );
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
