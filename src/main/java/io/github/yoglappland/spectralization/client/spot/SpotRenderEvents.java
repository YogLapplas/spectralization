package io.github.yoglappland.spectralization.client.spot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.ProjectionMode;
import io.github.yoglappland.spectralization.optics.projection.SpotSurfaceFrame;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final int MAX_RENDERED_SPOTS = 1024;
    private static final double MAX_RENDER_DISTANCE = 48.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    private static final double SURFACE_OFFSET = 0.003D;
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
                || Minecraft.getInstance().level == null
                || !ClientHudState.visible()) {
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
        List<ClientSpotCache.RenderedSpot> sortedSpots = new ArrayList<>(spots);
        sortedSpots.sort(Comparator.comparingDouble(
                (ClientSpotCache.RenderedSpot spot) -> distanceSquared(cameraPosition, spot)
        ).reversed());

        int renderedSpots = 0;

        for (ClientSpotCache.RenderedSpot spot : sortedSpots) {
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
        bufferSource.endBatch(RenderType.debugQuads());
        bufferSource.endBatch(HALO_RENDER_TYPE);
        bufferSource.endBatch(CORE_RENDER_TYPE);
        bufferSource.endBatch(RING_RENDER_TYPE);
    }

    private static void renderSpot(MultiBufferSource.BufferSource bufferSource, PoseStack.Pose pose, ClientSpotCache.RenderedSpot spot) {
        if (spot.projectionMode() == ProjectionMode.DEBUG_FACE_CENTER) {
            renderDebugFaceCenter(bufferSource.getBuffer(RenderType.debugQuads()), pose, spot);
            return;
        }

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

    private static void renderDebugFaceCenter(VertexConsumer consumer, PoseStack.Pose pose, ClientSpotCache.RenderedSpot spot) {
        double half = 0.045D;
        double cx = spot.centerX();
        double cy = spot.centerY();
        double cz = spot.centerZ();
        double x0 = cx - half;
        double y0 = cy - half;
        double z0 = cz - half;
        double x1 = cx + half;
        double y1 = cy + half;
        double z1 = cz + half;
        int red = 255;
        int green = 0;
        int blue = 0;
        int alpha = 220;

        addDebugQuad(consumer, pose, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, red, green, blue, alpha);
        addDebugQuad(consumer, pose, x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0, red, green, blue, alpha);
        addDebugQuad(consumer, pose, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, red, green, blue, alpha);
        addDebugQuad(consumer, pose, x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, red, green, blue, alpha);
        addDebugQuad(consumer, pose, x0, y1, z1, x0, y1, z0, x1, y1, z0, x1, y1, z1, red, green, blue, alpha);
        addDebugQuad(consumer, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, red, green, blue, alpha);
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

        if (spot.projectionMode() == ProjectionMode.FOOTPRINT_SLICE) {
            renderFootprintSlice(consumer, pose, spot, alpha, red, green, blue);
            return;
        }

        if (spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD) {
            renderFootprintQuad(consumer, pose, spot, alpha, red, green, blue);
            return;
        }

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

    private static void renderFootprintQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        Direction face = spot.face();
        double offsetX = face.getStepX() * SURFACE_OFFSET;
        double offsetY = face.getStepY() * SURFACE_OFFSET;
        double offsetZ = face.getStepZ() * SURFACE_OFFSET;
        double baseX = spot.pos().getX();
        double baseY = spot.pos().getY();
        double baseZ = spot.pos().getZ();

        addQuad(
                consumer,
                pose,
                baseX + normalizedQuadUnit(spot.quadX0()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY0()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ0()) + offsetZ,
                normalizedQuadUnitFloat(spot.quadTextureU0()),
                normalizedQuadUnitFloat(spot.quadTextureV0()),
                baseX + normalizedQuadUnit(spot.quadX1()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY1()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ1()) + offsetZ,
                normalizedQuadUnitFloat(spot.quadTextureU1()),
                normalizedQuadUnitFloat(spot.quadTextureV1()),
                baseX + normalizedQuadUnit(spot.quadX2()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY2()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ2()) + offsetZ,
                normalizedQuadUnitFloat(spot.quadTextureU2()),
                normalizedQuadUnitFloat(spot.quadTextureV2()),
                baseX + normalizedQuadUnit(spot.quadX3()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY3()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ3()) + offsetZ,
                normalizedQuadUnitFloat(spot.quadTextureU3()),
                normalizedQuadUnitFloat(spot.quadTextureV3()),
                red,
                green,
                blue,
                alpha
        );
    }

    private static void renderFootprintSlice(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            int alpha,
            int red,
            int green,
            int blue
    ) {
        Direction face = spot.face();
        double minU = normalizedByte(spot.clipMinU());
        double minV = normalizedByte(spot.clipMinV());
        double maxU = normalizedByte(spot.clipMaxU());
        double maxV = normalizedByte(spot.clipMaxV());
        float textureMinU = normalizedByteFloat(spot.textureMinU());
        float textureMinV = normalizedByteFloat(spot.textureMinV());
        float textureMaxU = normalizedByteFloat(spot.textureMaxU());
        float textureMaxV = normalizedByteFloat(spot.textureMaxV());
        SpotSurfaceFrame.LocalCoordinates p0 = SpotSurfaceFrame.surfaceLocal(face, minU, minV, SURFACE_OFFSET);
        SpotSurfaceFrame.LocalCoordinates p1 = SpotSurfaceFrame.surfaceLocal(face, minU, maxV, SURFACE_OFFSET);
        SpotSurfaceFrame.LocalCoordinates p2 = SpotSurfaceFrame.surfaceLocal(face, maxU, maxV, SURFACE_OFFSET);
        SpotSurfaceFrame.LocalCoordinates p3 = SpotSurfaceFrame.surfaceLocal(face, maxU, minV, SURFACE_OFFSET);
        double baseX = spot.pos().getX();
        double baseY = spot.pos().getY();
        double baseZ = spot.pos().getZ();

        addQuad(
                consumer,
                pose,
                baseX + p0.x(),
                baseY + p0.y(),
                baseZ + p0.z(),
                textureMinU,
                textureMaxV,
                baseX + p1.x(),
                baseY + p1.y(),
                baseZ + p1.z(),
                textureMinU,
                textureMinV,
                baseX + p2.x(),
                baseY + p2.y(),
                baseZ + p2.z(),
                textureMaxU,
                textureMinV,
                baseX + p3.x(),
                baseY + p3.y(),
                baseZ + p3.z(),
                textureMaxU,
                textureMaxV,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void addQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x1,
            double y1,
            double z1,
            float u1,
            float v1,
            double x2,
            double y2,
            double z2,
            float u2,
            float v2,
            double x3,
            double y3,
            double z3,
            float u3,
            float v3,
            double x4,
            double y4,
            double z4,
            float u4,
            float v4,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, u1, v1, red, green, blue, alpha);
        addVertex(consumer, pose, x2, y2, z2, u2, v2, red, green, blue, alpha);
        addVertex(consumer, pose, x3, y3, z3, u3, v3, red, green, blue, alpha);
        addVertex(consumer, pose, x4, y4, z4, u4, v4, red, green, blue, alpha);
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
        addQuad(
                consumer,
                pose,
                x1,
                y1,
                z1,
                x2,
                y2,
                z2,
                x3,
                y3,
                z3,
                x4,
                y4,
                z4,
                0.0F,
                0.0F,
                1.0F,
                1.0F,
                red,
                green,
                blue,
                alpha
        );
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
            float textureMinU,
            float textureMinV,
            float textureMaxU,
            float textureMaxV,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        addVertex(consumer, pose, x1, y1, z1, textureMinU, textureMaxV, red, green, blue, alpha);
        addVertex(consumer, pose, x2, y2, z2, textureMinU, textureMinV, red, green, blue, alpha);
        addVertex(consumer, pose, x3, y3, z3, textureMaxU, textureMinV, red, green, blue, alpha);
        addVertex(consumer, pose, x4, y4, z4, textureMaxU, textureMaxV, red, green, blue, alpha);
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

    private static void addDebugQuad(
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
        addDebugVertex(consumer, pose, x1, y1, z1, red, green, blue, alpha);
        addDebugVertex(consumer, pose, x2, y2, z2, red, green, blue, alpha);
        addDebugVertex(consumer, pose, x3, y3, z3, red, green, blue, alpha);
        addDebugVertex(consumer, pose, x4, y4, z4, red, green, blue, alpha);
    }

    private static void addDebugVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            double x,
            double y,
            double z,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        consumer.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
    }

    private static boolean isNearCamera(Vec3 cameraPosition, ClientSpotCache.RenderedSpot spot) {
        return distanceSquared(cameraPosition, spot) <= MAX_RENDER_DISTANCE_SQUARED;
    }

    private static double distanceSquared(Vec3 cameraPosition, ClientSpotCache.RenderedSpot spot) {
        double dx = spot.centerX() - cameraPosition.x;
        double dy = spot.centerY() - cameraPosition.y;
        double dz = spot.centerZ() - cameraPosition.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double normalizedByte(int value) {
        return value / 255.0D;
    }

    private static float normalizedByteFloat(int value) {
        return value / 255.0F;
    }

    private static double normalizedQuadUnit(int value) {
        return value / (double) SpotRecord.QUAD_QUANTIZATION_LEVEL;
    }

    private static float normalizedQuadUnitFloat(int value) {
        return value / (float) SpotRecord.QUAD_QUANTIZATION_LEVEL;
    }

    private SpotRenderEvents() {
    }
}
