package io.github.yoglappland.spectralization.client.spot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Spectralization.MODID, value = Dist.CLIENT)
public final class SpotRenderEvents {
    private static final int MAX_RENDERED_SPOTS = 384;
    private static final double MAX_RENDER_DISTANCE = 48.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    private static final double SURFACE_OFFSET = 0.003D;
    private static final ResourceLocation CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_core.png");
    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_halo.png");
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_ring.png");

    @SubscribeEvent
    public static void renderSpots(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || Minecraft.getInstance().level == null) {
            return;
        }

        var spots = ClientSpotCache.activeSpots();

        if (spots.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPosition = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        PoseStack.Pose pose = poseStack.last();

        int renderedSpots = 0;

        for (SpotRecord spot : spots) {
            if (renderedSpots >= MAX_RENDERED_SPOTS) {
                break;
            }

            if (!isNearCamera(cameraPosition, spot)) {
                continue;
            }

            renderSpot(bufferSource, pose, spot);
            renderedSpots++;
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.entityTranslucent(HALO_TEXTURE));
        bufferSource.endBatch(RenderType.entityTranslucent(CORE_TEXTURE));
        bufferSource.endBatch(RenderType.entityTranslucent(RING_TEXTURE));
    }

    private static void renderSpot(MultiBufferSource.BufferSource bufferSource, PoseStack.Pose pose, SpotRecord spot) {
        Vec3 center = spotCenter(spot);

        if (spot.strayAlphaLevel() > 0) {
            renderTexturedSpot(
                    bufferSource.getBuffer(RenderType.entityTranslucent(HALO_TEXTURE)),
                    pose,
                    spot,
                    center,
                    renderRadius(spot.strayRadiusLevel(), 1.25D),
                    alphaFor(spot.strayAlphaLevel(), 0.72D),
                    spot.strayRed(),
                    spot.strayGreen(),
                    spot.strayBlue()
            );
        }

        if (spot.coherentAlphaLevel() > 0) {
            renderTexturedSpot(
                    bufferSource.getBuffer(RenderType.entityTranslucent(CORE_TEXTURE)),
                    pose,
                    spot,
                    center,
                    renderRadius(spot.coherentRadiusLevel(), 0.82D),
                    alphaFor(spot.coherentAlphaLevel(), 1.0D),
                    spot.coherentRed(),
                    spot.coherentGreen(),
                    spot.coherentBlue()
            );
        }

        if (spot.ringAlphaLevel() > 0) {
            int red = spot.coherentAlphaLevel() > 0 ? spot.coherentRed() : spot.strayRed();
            int green = spot.coherentAlphaLevel() > 0 ? spot.coherentGreen() : spot.strayGreen();
            int blue = spot.coherentAlphaLevel() > 0 ? spot.coherentBlue() : spot.strayBlue();
            int radiusLevel = Math.max(spot.coherentRadiusLevel(), spot.strayRadiusLevel());
            renderTexturedSpot(
                    bufferSource.getBuffer(RenderType.entityTranslucent(RING_TEXTURE)),
                    pose,
                    spot,
                    center,
                    renderRadius(radiusLevel, 1.35D),
                    alphaFor(spot.ringAlphaLevel(), 0.72D),
                    red,
                    green,
                    blue
            );
        }
    }

    private static void renderTexturedSpot(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            SpotRecord spot,
            Vec3 center,
            double radius,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        Direction face = spot.face();
        double x = center.x;
        double y = center.y;
        double z = center.z;

        switch (face.getAxis()) {
            case X -> addQuad(
                    consumer,
                    pose,
                    x,
                    y - radius,
                    z - radius,
                    x,
                    y + radius,
                    z - radius,
                    x,
                    y + radius,
                    z + radius,
                    x,
                    y - radius,
                    z + radius,
                    red,
                    green,
                    blue,
                    alpha
            );
            case Y -> addQuad(
                    consumer,
                    pose,
                    x - radius,
                    y,
                    z - radius,
                    x - radius,
                    y,
                    z + radius,
                    x + radius,
                    y,
                    z + radius,
                    x + radius,
                    y,
                    z - radius,
                    red,
                    green,
                    blue,
                    alpha
            );
            case Z -> addQuad(
                    consumer,
                    pose,
                    x - radius,
                    y - radius,
                    z,
                    x + radius,
                    y - radius,
                    z,
                    x + radius,
                    y + radius,
                    z,
                    x - radius,
                    y + radius,
                    z,
                    red,
                    green,
                    blue,
                    alpha
            );
        }
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            double x3,
            double y3,
            double z3,
            double x4,
            double y4,
            double z4,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, 0.0F, 1.0F, red, green, blue, alpha);
        addVertex(consumer, pose, x2, y2, z2, 0.0F, 0.0F, red, green, blue, alpha);
        addVertex(consumer, pose, x3, y3, z3, 1.0F, 0.0F, red, green, blue, alpha);
        addVertex(consumer, pose, x4, y4, z4, 1.0F, 1.0F, red, green, blue, alpha);
    }

    private static void addVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private static double renderRadius(int radiusLevel, double multiplier) {
        return (0.075D + Math.max(1, radiusLevel) * 0.048D) * multiplier;
    }

    private static int alphaFor(int alphaLevel, double multiplier) {
        return Math.max(0, Math.min(240, (int) Math.round((88 + alphaLevel * 10) * multiplier)));
    }

    private static Vec3 spotCenter(SpotRecord spot) {
        BlockPos pos = spot.pos();
        Direction face = spot.face();
        return new Vec3(
                pos.getX() + 0.5D + face.getStepX() * (0.5D + SURFACE_OFFSET),
                pos.getY() + 0.5D + face.getStepY() * (0.5D + SURFACE_OFFSET),
                pos.getZ() + 0.5D + face.getStepZ() * (0.5D + SURFACE_OFFSET)
        );
    }

    private static boolean isNearCamera(Vec3 cameraPosition, SpotRecord spot) {
        BlockPos pos = spot.pos();
        double dx = pos.getX() + 0.5D - cameraPosition.x;
        double dy = pos.getY() + 0.5D - cameraPosition.y;
        double dz = pos.getZ() + 0.5D - cameraPosition.z;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQUARED;
    }

    private SpotRenderEvents() {
    }
}
