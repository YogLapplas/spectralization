package io.github.yoglappland.spectralization.client.spot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.ClientHudState;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
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
    private static final int MAX_RENDERED_SPOTS = 8192;
    private static final double MAX_RENDER_DISTANCE = 48.0D;
    private static final double MAX_RENDER_DISTANCE_SQUARED = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;
    private static final double SURFACE_OFFSET = 0.001D;
    private static final double OVERLAP_LAYER_OFFSET = 0.00001D;
    private static final long PROFILE_INTERVAL_NANOS = 1_000_000_000L;
    private static final ResourceLocation CORE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_square_core.png");
    private static final ResourceLocation HALO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_square_halo.png");
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/effect/spot_square_ring.png");
    private static final RenderType CORE_RENDER_TYPE = RenderType.entityTranslucentEmissive(CORE_TEXTURE);
    private static final RenderType HALO_RENDER_TYPE = RenderType.entityTranslucentEmissive(HALO_TEXTURE);
    private static final RenderType RING_RENDER_TYPE = RenderType.entityTranslucentEmissive(RING_TEXTURE);
    private static final int[] HEX_SEGMENT_MASKS = {
            0x3F, 0x06, 0x5B, 0x4F,
            0x66, 0x6D, 0x7D, 0x07,
            0x7F, 0x6F, 0x77, 0x7C,
            0x39, 0x5E, 0x79, 0x71
    };
    private static final MarkerSegment[] MARKER_SEGMENTS = {
            new MarkerSegment(0x01, 0.20D, 0.86D, 0.80D, 0.98D),
            new MarkerSegment(0x02, 0.82D, 0.52D, 0.96D, 0.84D),
            new MarkerSegment(0x04, 0.82D, 0.16D, 0.96D, 0.48D),
            new MarkerSegment(0x08, 0.20D, 0.02D, 0.80D, 0.14D),
            new MarkerSegment(0x10, 0.04D, 0.16D, 0.18D, 0.48D),
            new MarkerSegment(0x20, 0.04D, 0.52D, 0.18D, 0.84D),
            new MarkerSegment(0x40, 0.20D, 0.44D, 0.80D, 0.56D)
    };
    private static int currentFrameQuadCalls = 0;
    private static long profileWindowStartNanos = 0L;
    private static long profileRenderNanos = 0L;
    private static long profileFrames = 0L;
    private static long profileActiveSpots = 0L;
    private static long profileSortedSpots = 0L;
    private static long profileRenderedSpots = 0L;
    private static long profileCulledSpots = 0L;
    private static long profileQuadCalls = 0L;
    private static long profileMaxFrameNanos = 0L;
    private static int profileMaxFrameQuads = 0;

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

        boolean profile = SpectralizationConfig.opticalCompilerDebugLog();
        long frameStartNanos = profile ? System.nanoTime() : 0L;
        currentFrameQuadCalls = 0;
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
        int culledSpots = 0;

        for (ClientSpotCache.RenderedSpot spot : sortedSpots) {
            if (renderedSpots >= MAX_RENDERED_SPOTS) {
                break;
            }

            if (!isNearCamera(cameraPosition, spot)) {
                culledSpots++;
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

        if (profile) {
            recordRenderProfile(
                    Minecraft.getInstance(),
                    spots.size(),
                    sortedSpots.size(),
                    renderedSpots,
                    culledSpots,
                    currentFrameQuadCalls,
                    System.nanoTime() - frameStartNanos
            );
        } else {
            resetRenderProfile();
        }
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
        if (spot.debugMarker() >= 0) {
            renderDebugFaceMarker(consumer, pose, spot);
            return;
        }

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

    private static void renderDebugFaceMarker(VertexConsumer consumer, PoseStack.Pose pose, ClientSpotCache.RenderedSpot spot) {
        Direction face = spot.face();
        int marker = spot.debugMarker() & 0xFFF;
        int high = (marker >>> 8) & 0xF;
        int middle = (marker >>> 4) & 0xF;
        int low = marker & 0xF;
        double digitWidth = 0.115D;
        double digitHeight = 0.220D;
        double digitGap = 0.018D;
        double totalWidth = digitWidth * 3.0D + digitGap * 2.0D;
        double startU = 0.5D - totalWidth * 0.5D;
        double minV = 0.5D - digitHeight * 0.5D;
        double markerOffset = SURFACE_OFFSET + 0.006D;

        renderDebugFaceRect(consumer, pose, spot, face, startU - 0.018D, minV - 0.018D,
                startU + totalWidth + 0.018D, minV + digitHeight + 0.018D, markerOffset,
                0, 0, 0, 130);
        renderDebugDigit(consumer, pose, spot, face, high, startU, minV, digitWidth, digitHeight, markerOffset);
        renderDebugDigit(consumer, pose, spot, face, middle, startU + digitWidth + digitGap, minV,
                digitWidth, digitHeight, markerOffset);
        renderDebugDigit(consumer, pose, spot, face, low, startU + (digitWidth + digitGap) * 2.0D, minV,
                digitWidth, digitHeight, markerOffset);
    }

    private static void renderDebugDigit(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            Direction face,
            int digit,
            double digitMinU,
            double digitMinV,
            double digitWidth,
            double digitHeight,
            double markerOffset
    ) {
        int mask = HEX_SEGMENT_MASKS[digit & 0xF];

        for (MarkerSegment segment : MARKER_SEGMENTS) {
            if ((mask & segment.bit()) == 0) {
                continue;
            }

            renderDebugFaceRect(
                    consumer,
                    pose,
                    spot,
                    face,
                    digitMinU + segment.minU() * digitWidth,
                    digitMinV + segment.minV() * digitHeight,
                    digitMinU + segment.maxU() * digitWidth,
                    digitMinV + segment.maxV() * digitHeight,
                    markerOffset,
                    255,
                    70,
                    40,
                    245
            );
        }
    }

    private static void renderDebugFaceRect(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            Direction face,
            double minU,
            double minV,
            double maxU,
            double maxV,
            double offset,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        SpotSurfaceFrame.LocalCoordinates p0 = SpotSurfaceFrame.surfaceLocal(face, minU, minV, offset);
        SpotSurfaceFrame.LocalCoordinates p1 = SpotSurfaceFrame.surfaceLocal(face, minU, maxV, offset);
        SpotSurfaceFrame.LocalCoordinates p2 = SpotSurfaceFrame.surfaceLocal(face, maxU, maxV, offset);
        SpotSurfaceFrame.LocalCoordinates p3 = SpotSurfaceFrame.surfaceLocal(face, maxU, minV, offset);
        double baseX = spot.pos().getX();
        double baseY = spot.pos().getY();
        double baseZ = spot.pos().getZ();

        addDebugQuad(
                consumer,
                pose,
                baseX + p0.x(),
                baseY + p0.y(),
                baseZ + p0.z(),
                baseX + p1.x(),
                baseY + p1.y(),
                baseZ + p1.z(),
                baseX + p2.x(),
                baseY + p2.y(),
                baseZ + p2.z(),
                baseX + p3.x(),
                baseY + p3.y(),
                baseZ + p3.z(),
                red,
                green,
                blue,
                alpha
        );
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
        double overlapOffset = overlapLayerOffset(spot);
        double offsetX = face.getStepX() * overlapOffset;
        double offsetY = face.getStepY() * overlapOffset;
        double offsetZ = face.getStepZ() * overlapOffset;

        switch (face.getAxis()) {
            case X -> addQuad(
                    consumer,
                    pose,
                    x + offsetX,
                    y - radius,
                    z - radius,
                    x + offsetX,
                    y + radius,
                    z - radius,
                    x + offsetX,
                    y + radius,
                    z + radius,
                    x + offsetX,
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
                    y + offsetY,
                    z - radius,
                    x - radius,
                    y + offsetY,
                    z + radius,
                    x + radius,
                    y + offsetY,
                    z + radius,
                    x + radius,
                    y + offsetY,
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
                    z + offsetZ,
                    x + radius,
                    y - radius,
                    z + offsetZ,
                    x + radius,
                    y + radius,
                    z + offsetZ,
                    x - radius,
                    y + radius,
                    z + offsetZ,
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
        double surfaceOffset = surfaceOffset(spot);
        double offsetX = face.getStepX() * surfaceOffset;
        double offsetY = face.getStepY() * surfaceOffset;
        double offsetZ = face.getStepZ() * surfaceOffset;
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
        double surfaceOffset = surfaceOffset(spot);
        SpotSurfaceFrame.LocalCoordinates p0 = SpotSurfaceFrame.surfaceLocal(face, minU, minV, surfaceOffset);
        SpotSurfaceFrame.LocalCoordinates p1 = SpotSurfaceFrame.surfaceLocal(face, minU, maxV, surfaceOffset);
        SpotSurfaceFrame.LocalCoordinates p2 = SpotSurfaceFrame.surfaceLocal(face, maxU, maxV, surfaceOffset);
        SpotSurfaceFrame.LocalCoordinates p3 = SpotSurfaceFrame.surfaceLocal(face, maxU, minV, surfaceOffset);
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
        currentFrameQuadCalls++;
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
        currentFrameQuadCalls++;
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

    private static double surfaceOffset(ClientSpotCache.RenderedSpot spot) {
        return SURFACE_OFFSET + overlapLayerOffset(spot);
    }

    private static double overlapLayerOffset(ClientSpotCache.RenderedSpot spot) {
        return (1 + Math.floorMod(stableSpotHash(spot), 7)) * OVERLAP_LAYER_OFFSET;
    }

    private static int stableSpotHash(ClientSpotCache.RenderedSpot spot) {
        int hash = 17;
        hash = 31 * hash + spot.pos().hashCode();
        hash = 31 * hash + spot.face().ordinal();
        hash = 31 * hash + spot.projectionMode().ordinal();
        hash = 31 * hash + spot.clipMinU();
        hash = 31 * hash + spot.clipMinV();
        hash = 31 * hash + spot.clipMaxU();
        hash = 31 * hash + spot.clipMaxV();
        hash = 31 * hash + spot.textureMinU();
        hash = 31 * hash + spot.textureMinV();
        hash = 31 * hash + spot.textureMaxU();
        hash = 31 * hash + spot.textureMaxV();
        hash = 31 * hash + spot.quadX0();
        hash = 31 * hash + spot.quadY0();
        hash = 31 * hash + spot.quadZ0();
        hash = 31 * hash + spot.quadX1();
        hash = 31 * hash + spot.quadY1();
        hash = 31 * hash + spot.quadZ1();
        hash = 31 * hash + spot.quadX2();
        hash = 31 * hash + spot.quadY2();
        hash = 31 * hash + spot.quadZ2();
        hash = 31 * hash + spot.quadX3();
        hash = 31 * hash + spot.quadY3();
        hash = 31 * hash + spot.quadZ3();
        return hash;
    }

    private static void recordRenderProfile(
            Minecraft minecraft,
            int activeSpots,
            int sortedSpots,
            int renderedSpots,
            int culledSpots,
            int quadCalls,
            long renderNanos
    ) {
        long now = System.nanoTime();

        if (profileWindowStartNanos == 0L) {
            profileWindowStartNanos = now;
        }

        profileFrames++;
        profileRenderNanos += Math.max(0L, renderNanos);
        profileActiveSpots += activeSpots;
        profileSortedSpots += sortedSpots;
        profileRenderedSpots += renderedSpots;
        profileCulledSpots += culledSpots;
        profileQuadCalls += quadCalls;
        profileMaxFrameNanos = Math.max(profileMaxFrameNanos, renderNanos);
        profileMaxFrameQuads = Math.max(profileMaxFrameQuads, quadCalls);

        if (now - profileWindowStartNanos < PROFILE_INTERVAL_NANOS) {
            return;
        }

        double frames = Math.max(1.0D, profileFrames);
        SpectralDiagnostics.event(minecraft.level, "client_spot_render", "profile")
                .field("frames", profileFrames)
                .field("active_avg", profileActiveSpots / frames)
                .field("sorted_avg", profileSortedSpots / frames)
                .field("rendered_avg", profileRenderedSpots / frames)
                .field("culled_avg", profileCulledSpots / frames)
                .field("quad_avg", profileQuadCalls / frames)
                .field("quad_max", profileMaxFrameQuads)
                .field("elapsed_ms_avg", profileRenderNanos / frames / 1_000_000.0D)
                .field("elapsed_ms_max", profileMaxFrameNanos / 1_000_000.0D)
                .write();
        resetRenderProfile();
    }

    private static void resetRenderProfile() {
        profileWindowStartNanos = 0L;
        profileRenderNanos = 0L;
        profileFrames = 0L;
        profileActiveSpots = 0L;
        profileSortedSpots = 0L;
        profileRenderedSpots = 0L;
        profileCulledSpots = 0L;
        profileQuadCalls = 0L;
        profileMaxFrameNanos = 0L;
        profileMaxFrameQuads = 0;
    }

    private record MarkerSegment(int bit, double minU, double minV, double maxU, double maxV) {
    }

    private SpotRenderEvents() {
    }
}
