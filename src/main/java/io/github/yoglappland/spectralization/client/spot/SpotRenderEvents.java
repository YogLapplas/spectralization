package io.github.yoglappland.spectralization.client.spot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    private static final ResourceLocation CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_core.png");
    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_halo.png");
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_ring.png");
    private static final RenderType CORE_RENDER_TYPE = RenderType.entityTranslucent(CORE_TEXTURE);
    private static final RenderType HALO_RENDER_TYPE = RenderType.entityTranslucent(HALO_TEXTURE);
    private static final RenderType RING_RENDER_TYPE = RenderType.entityTranslucent(RING_TEXTURE);

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

        for (ClientSpotCache.RenderedSpot spot : spots) {
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
        bufferSource.endBatch(HALO_RENDER_TYPE);
        bufferSource.endBatch(CORE_RENDER_TYPE);
        bufferSource.endBatch(RING_RENDER_TYPE);
    }

    private static void renderSpot(MultiBufferSource.BufferSource bufferSource, PoseStack.Pose pose, ClientSpotCache.RenderedSpot spot) {
        if (spot.strayAlphaLevel() > 0) {
            renderTexturedSpot(
                    bufferSource.getBuffer(HALO_RENDER_TYPE),
                    pose,
                    spot,
                    spot.strayRadius(),
                    spot.strayAlpha(),
                    spot.strayRed(),
                    spot.strayGreen(),
                    spot.strayBlue()
            );
        }

        if (spot.coherentAlphaLevel() > 0) {
            renderTexturedSpot(
                    bufferSource.getBuffer(CORE_RENDER_TYPE),
                    pose,
                    spot,
                    spot.coherentRadius(),
                    spot.coherentAlpha(),
                    spot.coherentRed(),
                    spot.coherentGreen(),
                    spot.coherentBlue()
            );
        }

        if (spot.ringAlphaLevel() > 0) {
            renderTexturedSpot(
                    bufferSource.getBuffer(RING_RENDER_TYPE),
                    pose,
                    spot,
                    spot.ringRadius(),
                    spot.ringAlpha(),
                    spot.ringRed(),
                    spot.ringGreen(),
                    spot.ringBlue()
            );
        }
    }

    private static void renderTexturedSpot(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            double radius,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        Direction face = spot.face();
        double x = spot.centerX();
        double y = spot.centerY();
        double z = spot.centerZ();

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

    private static boolean isNearCamera(Vec3 cameraPosition, ClientSpotCache.RenderedSpot spot) {
        double dx = spot.centerX() - cameraPosition.x;
        double dy = spot.centerY() - cameraPosition.y;
        double dz = spot.centerZ() - cameraPosition.z;
        return dx * dx + dy * dy + dz * dz <= MAX_RENDER_DISTANCE_SQUARED;
    }

    private SpotRenderEvents() {
    }
}
