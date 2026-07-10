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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final double PARTIAL_QUAD_AREA_THRESHOLD = 0.985D;
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
    private static long colorDebugWindowStartNanos = 0L;
    private static long colorDebugFrames = 0L;
    private static long colorDebugFootprintQuads = 0L;
    private static long colorDebugPartialQuads = 0L;
    private static long colorDebugSmallPartialQuads = 0L;
    private static long colorDebugFootprintSlices = 0L;
    private static long colorDebugPartialSlices = 0L;
    private static long colorDebugMergedContributionSpots = 0L;
    private static long colorDebugMultiColorMergedSpots = 0L;
    private static long colorDebugPartialMultiColorFaces = 0L;
    private static long colorDebugPartialGeometryGroups = 0L;
    private static long colorDebugPartialGeometryMergedGroups = 0L;
    private static long colorDebugPartialGeometryMergedInputSpots = 0L;
    private static long colorDebugPartialGeometryMulticolorGroups = 0L;
    private static long colorDebugPartialQuadFlatRendered = 0L;
    private static double colorDebugMinPartialArea = 1.0D;
    private static int colorDebugWorstFacePartialQuads = 0;
    private static int colorDebugWorstFaceColorBuckets = 0;
    private static String colorDebugWorstFace = "none";
    private static int colorDebugWorstGeometrySpots = 0;
    private static int colorDebugWorstGeometryColorBuckets = 0;
    private static String colorDebugWorstGeometry = "none";

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
        boolean colorDebug = SpectralizationConfig.spotColorDebug();
        long frameStartNanos = profile ? System.nanoTime() : 0L;
        currentFrameQuadCalls = 0;
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPosition = event.getCamera().getPosition();
        ColorDebugFrameStats colorDebugStats = colorDebug ? new ColorDebugFrameStats() : null;

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

            if (colorDebugStats != null) {
                colorDebugStats.record(spot);
            }

            renderSpot(bufferSource, pose, spot);

            if (colorDebug) {
                renderColorDebugOverlay(bufferSource.getBuffer(RenderType.debugQuads()), pose, spot);
            }
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

        if (colorDebugStats != null) {
            recordColorDebugProfile(Minecraft.getInstance(), colorDebugStats);
        } else {
            resetColorDebugProfile();
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

    private static void renderColorDebugOverlay(VertexConsumer consumer, PoseStack.Pose pose, ClientSpotCache.RenderedSpot spot) {
        if (spot.projectionMode() != ProjectionMode.FOOTPRINT_QUAD
                && spot.projectionMode() != ProjectionMode.FOOTPRINT_SLICE) {
            return;
        }

        double area = textureArea(spot);
        if (area >= 0.985D) {
            return;
        }

        int[] color = colorDebugOverlayColor(spot);
        int alpha = ClientSpotCache.colorBucketCount(spot) > 1 ? 150 : 92;

        if (spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD) {
            renderColorDebugFootprintQuad(consumer, pose, spot, color[0], color[1], color[2], alpha);
        } else {
            renderColorDebugFootprintSlice(consumer, pose, spot, color[0], color[1], color[2], alpha);
        }
    }

    private static void renderColorDebugFootprintQuad(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        Direction face = spot.face();
        double surfaceOffset = surfaceOffset(spot) + 0.004D;
        double offsetX = face.getStepX() * surfaceOffset;
        double offsetY = face.getStepY() * surfaceOffset;
        double offsetZ = face.getStepZ() * surfaceOffset;
        double baseX = spot.pos().getX();
        double baseY = spot.pos().getY();
        double baseZ = spot.pos().getZ();

        addDebugQuad(
                consumer,
                pose,
                baseX + normalizedQuadUnit(spot.quadX0()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY0()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ0()) + offsetZ,
                baseX + normalizedQuadUnit(spot.quadX1()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY1()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ1()) + offsetZ,
                baseX + normalizedQuadUnit(spot.quadX2()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY2()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ2()) + offsetZ,
                baseX + normalizedQuadUnit(spot.quadX3()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY3()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ3()) + offsetZ,
                red,
                green,
                blue,
                alpha
        );
    }

    private static void renderColorDebugFootprintSlice(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            ClientSpotCache.RenderedSpot spot,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        renderDebugFaceRect(
                consumer,
                pose,
                spot,
                spot.face(),
                normalizedByte(spot.clipMinU()),
                normalizedByte(spot.clipMinV()),
                normalizedByte(spot.clipMaxU()),
                normalizedByte(spot.clipMaxV()),
                surfaceOffset(spot) + 0.004D,
                red,
                green,
                blue,
                alpha
        );
    }

    private static int[] colorDebugOverlayColor(ClientSpotCache.RenderedSpot spot) {
        if (ClientSpotCache.colorBucketCount(spot) > 1) {
            return new int[]{255, 255, 255};
        }

        int bucket = Integer.numberOfTrailingZeros(Math.max(1, spot.colorBucketMask()));
        return switch (bucket) {
            case 1 -> new int[]{255, 48, 48};
            case 2 -> new int[]{48, 255, 48};
            case 3 -> new int[]{255, 230, 48};
            case 4 -> new int[]{64, 96, 255};
            case 5 -> new int[]{255, 64, 255};
            case 6 -> new int[]{64, 255, 255};
            case 7 -> new int[]{255, 255, 255};
            default -> new int[]{150, 150, 150};
        };
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
        boolean flatPartialQuad = isPartialFootprintQuad(spot);
        float textureU0 = flatPartialQuad ? flatQuadTextureU(spot) : normalizedQuadUnitFloat(spot.quadTextureU0());
        float textureV0 = flatPartialQuad ? flatQuadTextureV(spot) : normalizedQuadUnitFloat(spot.quadTextureV0());
        float textureU1 = flatPartialQuad ? textureU0 : normalizedQuadUnitFloat(spot.quadTextureU1());
        float textureV1 = flatPartialQuad ? textureV0 : normalizedQuadUnitFloat(spot.quadTextureV1());
        float textureU2 = flatPartialQuad ? textureU0 : normalizedQuadUnitFloat(spot.quadTextureU2());
        float textureV2 = flatPartialQuad ? textureV0 : normalizedQuadUnitFloat(spot.quadTextureV2());
        float textureU3 = flatPartialQuad ? textureU0 : normalizedQuadUnitFloat(spot.quadTextureU3());
        float textureV3 = flatPartialQuad ? textureV0 : normalizedQuadUnitFloat(spot.quadTextureV3());

        addQuad(
                consumer,
                pose,
                baseX + normalizedQuadUnit(spot.quadX0()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY0()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ0()) + offsetZ,
                textureU0,
                textureV0,
                baseX + normalizedQuadUnit(spot.quadX1()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY1()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ1()) + offsetZ,
                textureU1,
                textureV1,
                baseX + normalizedQuadUnit(spot.quadX2()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY2()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ2()) + offsetZ,
                textureU2,
                textureV2,
                baseX + normalizedQuadUnit(spot.quadX3()) + offsetX,
                baseY + normalizedQuadUnit(spot.quadY3()) + offsetY,
                baseZ + normalizedQuadUnit(spot.quadZ3()) + offsetZ,
                textureU3,
                textureV3,
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

    private static double textureArea(ClientSpotCache.RenderedSpot spot) {
        return switch (spot.projectionMode()) {
            case FOOTPRINT_QUAD -> quadTextureArea(spot);
            case FOOTPRINT_SLICE -> sliceTextureArea(spot);
            default -> 1.0D;
        };
    }

    private static boolean isPartialFootprintQuad(ClientSpotCache.RenderedSpot spot) {
        return spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD
                && quadTextureArea(spot) < PARTIAL_QUAD_AREA_THRESHOLD;
    }

    private static float flatQuadTextureU(ClientSpotCache.RenderedSpot spot) {
        return (normalizedQuadUnitFloat(spot.quadTextureU0())
                + normalizedQuadUnitFloat(spot.quadTextureU1())
                + normalizedQuadUnitFloat(spot.quadTextureU2())
                + normalizedQuadUnitFloat(spot.quadTextureU3())) * 0.25F;
    }

    private static float flatQuadTextureV(ClientSpotCache.RenderedSpot spot) {
        return (normalizedQuadUnitFloat(spot.quadTextureV0())
                + normalizedQuadUnitFloat(spot.quadTextureV1())
                + normalizedQuadUnitFloat(spot.quadTextureV2())
                + normalizedQuadUnitFloat(spot.quadTextureV3())) * 0.25F;
    }

    private static double sliceTextureArea(ClientSpotCache.RenderedSpot spot) {
        double width = Math.max(0.0D, normalizedByte(spot.textureMaxU()) - normalizedByte(spot.textureMinU()));
        double height = Math.max(0.0D, normalizedByte(spot.textureMaxV()) - normalizedByte(spot.textureMinV()));
        return Math.min(1.0D, width * height);
    }

    private static double quadTextureArea(ClientSpotCache.RenderedSpot spot) {
        double u0 = normalizedQuadUnit(spot.quadTextureU0());
        double v0 = normalizedQuadUnit(spot.quadTextureV0());
        double u1 = normalizedQuadUnit(spot.quadTextureU1());
        double v1 = normalizedQuadUnit(spot.quadTextureV1());
        double u2 = normalizedQuadUnit(spot.quadTextureU2());
        double v2 = normalizedQuadUnit(spot.quadTextureV2());
        double u3 = normalizedQuadUnit(spot.quadTextureU3());
        double v3 = normalizedQuadUnit(spot.quadTextureV3());
        double twiceArea = u0 * v1 - v0 * u1
                + u1 * v2 - v1 * u2
                + u2 * v3 - v2 * u3
                + u3 * v0 - v3 * u0;
        return Math.min(1.0D, Math.abs(twiceArea) * 0.5D);
    }

    private static void recordColorDebugProfile(Minecraft minecraft, ColorDebugFrameStats stats) {
        long now = System.nanoTime();

        if (colorDebugWindowStartNanos == 0L) {
            colorDebugWindowStartNanos = now;
        }

        colorDebugFrames++;
        colorDebugFootprintQuads += stats.footprintQuads;
        colorDebugPartialQuads += stats.partialQuads;
        colorDebugSmallPartialQuads += stats.smallPartialQuads;
        colorDebugFootprintSlices += stats.footprintSlices;
        colorDebugPartialSlices += stats.partialSlices;
        colorDebugMergedContributionSpots += stats.mergedContributionSpots;
        colorDebugMultiColorMergedSpots += stats.multiColorMergedSpots;
        colorDebugPartialMultiColorFaces += stats.partialMultiColorFaces();
        ClientSpotCache.GeometryMergeStats geometryStats = ClientSpotCache.geometryMergeStats();
        colorDebugPartialGeometryGroups += geometryStats.partialGeometryGroups();
        colorDebugPartialGeometryMergedGroups += geometryStats.partialGeometryMergedGroups();
        colorDebugPartialGeometryMergedInputSpots += geometryStats.partialGeometryMergedInputSpots();
        colorDebugPartialGeometryMulticolorGroups += geometryStats.partialGeometryMulticolorGroups();
        colorDebugPartialQuadFlatRendered += stats.partialQuadFlatRendered;

        if (stats.minPartialArea < colorDebugMinPartialArea) {
            colorDebugMinPartialArea = stats.minPartialArea;
        }

        FaceColorStats worstFace = stats.worstFace();
        if (worstFace != null
                && (worstFace.partialQuads > colorDebugWorstFacePartialQuads
                || (worstFace.partialQuads == colorDebugWorstFacePartialQuads
                && worstFace.colorBucketCount() > colorDebugWorstFaceColorBuckets))) {
            colorDebugWorstFacePartialQuads = worstFace.partialQuads;
            colorDebugWorstFaceColorBuckets = worstFace.colorBucketCount();
            colorDebugWorstFace = worstFace.key.format();
        }

        if (geometryStats.worstGeometrySpots() > colorDebugWorstGeometrySpots
                || (geometryStats.worstGeometrySpots() == colorDebugWorstGeometrySpots
                && geometryStats.worstGeometryColorBuckets() > colorDebugWorstGeometryColorBuckets)) {
            colorDebugWorstGeometrySpots = geometryStats.worstGeometrySpots();
            colorDebugWorstGeometryColorBuckets = geometryStats.worstGeometryColorBuckets();
            colorDebugWorstGeometry = geometryStats.worstGeometryKey();
        }

        if (now - colorDebugWindowStartNanos < PROFILE_INTERVAL_NANOS) {
            return;
        }

        double frames = Math.max(1.0D, colorDebugFrames);
        SpectralDiagnostics.event(minecraft.level, "client_spot_color_debug", "profile")
                .field("frames", colorDebugFrames)
                .field("footprint_quad_avg", colorDebugFootprintQuads / frames)
                .field("partial_quad_avg", colorDebugPartialQuads / frames)
                .field("small_partial_quad_avg", colorDebugSmallPartialQuads / frames)
                .field("footprint_slice_avg", colorDebugFootprintSlices / frames)
                .field("partial_slice_avg", colorDebugPartialSlices / frames)
                .field("merged_contribution_spots_avg", colorDebugMergedContributionSpots / frames)
                .field("multicolor_merged_spots_avg", colorDebugMultiColorMergedSpots / frames)
                .field("partial_multicolor_faces_avg", colorDebugPartialMultiColorFaces / frames)
                .field("partial_geometry_groups_avg", colorDebugPartialGeometryGroups / frames)
                .field("partial_geometry_merged_groups_avg", colorDebugPartialGeometryMergedGroups / frames)
                .field("partial_geometry_merged_input_spots_avg", colorDebugPartialGeometryMergedInputSpots / frames)
                .field("partial_geometry_multicolor_groups_avg", colorDebugPartialGeometryMulticolorGroups / frames)
                .field("partial_quad_flat_rendered_avg", colorDebugPartialQuadFlatRendered / frames)
                .field("min_partial_area", colorDebugMinPartialArea == 1.0D ? 0.0D : colorDebugMinPartialArea)
                .field("worst_face", colorDebugWorstFace)
                .field("worst_face_partial_quads", colorDebugWorstFacePartialQuads)
                .field("worst_face_color_buckets", colorDebugWorstFaceColorBuckets)
                .field("worst_geometry", colorDebugWorstGeometry)
                .field("worst_geometry_spots", colorDebugWorstGeometrySpots)
                .field("worst_geometry_color_buckets", colorDebugWorstGeometryColorBuckets)
                .write();
        resetColorDebugProfile();
    }

    private static void resetColorDebugProfile() {
        colorDebugWindowStartNanos = 0L;
        colorDebugFrames = 0L;
        colorDebugFootprintQuads = 0L;
        colorDebugPartialQuads = 0L;
        colorDebugSmallPartialQuads = 0L;
        colorDebugFootprintSlices = 0L;
        colorDebugPartialSlices = 0L;
        colorDebugMergedContributionSpots = 0L;
        colorDebugMultiColorMergedSpots = 0L;
        colorDebugPartialMultiColorFaces = 0L;
        colorDebugPartialGeometryGroups = 0L;
        colorDebugPartialGeometryMergedGroups = 0L;
        colorDebugPartialGeometryMergedInputSpots = 0L;
        colorDebugPartialGeometryMulticolorGroups = 0L;
        colorDebugPartialQuadFlatRendered = 0L;
        colorDebugMinPartialArea = 1.0D;
        colorDebugWorstFacePartialQuads = 0;
        colorDebugWorstFaceColorBuckets = 0;
        colorDebugWorstFace = "none";
        colorDebugWorstGeometrySpots = 0;
        colorDebugWorstGeometryColorBuckets = 0;
        colorDebugWorstGeometry = "none";
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

    private static final class ColorDebugFrameStats {
        private final Map<FaceKey, FaceColorStats> partialQuadFaces = new HashMap<>();
        private int footprintQuads;
        private int partialQuads;
        private int smallPartialQuads;
        private int footprintSlices;
        private int partialSlices;
        private int mergedContributionSpots;
        private int multiColorMergedSpots;
        private int partialQuadFlatRendered;
        private double minPartialArea = 1.0D;

        private void record(ClientSpotCache.RenderedSpot spot) {
            if (spot.mergedContributions() > 1) {
                mergedContributionSpots++;
            }

            if (ClientSpotCache.colorBucketCount(spot) > 1) {
                multiColorMergedSpots++;
            }

            if (spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD) {
                footprintQuads++;
                double area = textureArea(spot);

                if (area < PARTIAL_QUAD_AREA_THRESHOLD) {
                    partialQuads++;
                    partialQuadFlatRendered++;
                    minPartialArea = Math.min(minPartialArea, area);

                    if (area < 0.025D) {
                        smallPartialQuads++;
                    }

                    FaceKey key = FaceKey.from(spot);
                    partialQuadFaces.computeIfAbsent(key, FaceColorStats::new)
                            .record(spot.colorBucketMask(), area);
                }
            } else if (spot.projectionMode() == ProjectionMode.FOOTPRINT_SLICE) {
                footprintSlices++;
                double area = textureArea(spot);

                if (area < 0.985D) {
                    partialSlices++;
                    minPartialArea = Math.min(minPartialArea, area);
                }
            }
        }

        private int partialMultiColorFaces() {
            int count = 0;

            for (FaceColorStats stats : partialQuadFaces.values()) {
                if (stats.colorBucketCount() > 1) {
                    count++;
                }
            }

            return count;
        }

        private FaceColorStats worstFace() {
            FaceColorStats worst = null;

            for (FaceColorStats stats : partialQuadFaces.values()) {
                if (worst == null
                        || stats.partialQuads > worst.partialQuads
                        || (stats.partialQuads == worst.partialQuads
                        && stats.colorBucketCount() > worst.colorBucketCount())) {
                    worst = stats;
                }
            }

            return worst;
        }
    }

    private static final class FaceColorStats {
        private final FaceKey key;
        private int partialQuads;
        private int colorBucketMask;
        private double minArea = 1.0D;

        private FaceColorStats(FaceKey key) {
            this.key = key;
        }

        private void record(int bucketMask, double area) {
            partialQuads++;
            colorBucketMask |= bucketMask;
            minArea = Math.min(minArea, area);
        }

        private int colorBucketCount() {
            return Integer.bitCount(colorBucketMask);
        }
    }

    private record FaceKey(int x, int y, int z, Direction face) {
        private static FaceKey from(ClientSpotCache.RenderedSpot spot) {
            return new FaceKey(spot.pos().getX(), spot.pos().getY(), spot.pos().getZ(), spot.face());
        }

        private String format() {
            return x + "," + y + "," + z + "/" + face;
        }
    }

    private record MarkerSegment(int bit, double minU, double minV, double maxU, double maxV) {
    }

    private SpotRenderEvents() {
    }
}
