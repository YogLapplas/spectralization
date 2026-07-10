package io.github.yoglappland.spectralization.client.spot;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.ProjectionMode;
import io.github.yoglappland.spectralization.optics.projection.SpotSurfaceFrame;
import io.github.yoglappland.spectralization.network.SpotOverlayPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientSpotCache {
    private static final int LEGACY_OWNER_ID = Integer.MIN_VALUE;
    private static final double SURFACE_OFFSET = 0.001D;
    private static final double PARTIAL_QUAD_AREA_THRESHOLD = 0.985D;
    private static final Map<Integer, Map<SpotKey, RenderedSpot>> SPOTS_BY_OWNER = new HashMap<>();
    private static final Comparator<SpotKey> SPOT_KEY_ORDER = Comparator
            .comparingInt((SpotKey key) -> key.pos().getX())
            .thenComparingInt(key -> key.pos().getY())
            .thenComparingInt(key -> key.pos().getZ())
            .thenComparingInt(key -> key.projectionMode().ordinal())
            .thenComparingInt(key -> key.face().ordinal())
            .thenComparingInt(SpotKey::clipMinU)
            .thenComparingInt(SpotKey::clipMinV)
            .thenComparingInt(SpotKey::clipMaxU)
            .thenComparingInt(SpotKey::clipMaxV)
            .thenComparingInt(SpotKey::textureMinU)
            .thenComparingInt(SpotKey::textureMinV)
            .thenComparingInt(SpotKey::textureMaxU)
            .thenComparingInt(SpotKey::textureMaxV)
            .thenComparingInt(SpotKey::quadX0)
            .thenComparingInt(SpotKey::quadY0)
            .thenComparingInt(SpotKey::quadZ0)
            .thenComparingInt(SpotKey::quadTextureU0)
            .thenComparingInt(SpotKey::quadTextureV0)
            .thenComparingInt(SpotKey::quadX1)
            .thenComparingInt(SpotKey::quadY1)
            .thenComparingInt(SpotKey::quadZ1)
            .thenComparingInt(SpotKey::quadTextureU1)
            .thenComparingInt(SpotKey::quadTextureV1)
            .thenComparingInt(SpotKey::quadX2)
            .thenComparingInt(SpotKey::quadY2)
            .thenComparingInt(SpotKey::quadZ2)
            .thenComparingInt(SpotKey::quadTextureU2)
            .thenComparingInt(SpotKey::quadTextureV2)
            .thenComparingInt(SpotKey::quadX3)
            .thenComparingInt(SpotKey::quadY3)
            .thenComparingInt(SpotKey::quadZ3)
            .thenComparingInt(SpotKey::quadTextureU3)
            .thenComparingInt(SpotKey::quadTextureV3);
    private static final Comparator<RenderedSpot> RENDERED_SPOT_ORDER = Comparator
            .comparingInt((RenderedSpot spot) -> projectionRenderPriority(spot.projectionMode()))
            .thenComparingInt(spot -> spot.pos().getX())
            .thenComparingInt(spot -> spot.pos().getY())
            .thenComparingInt(spot -> spot.pos().getZ())
            .thenComparingInt(spot -> spot.face().ordinal())
            .thenComparingInt(RenderedSpot::clipMinU)
            .thenComparingInt(RenderedSpot::clipMinV)
            .thenComparingInt(RenderedSpot::clipMaxU)
            .thenComparingInt(RenderedSpot::clipMaxV)
            .thenComparingInt(RenderedSpot::textureMinU)
            .thenComparingInt(RenderedSpot::textureMinV)
            .thenComparingInt(RenderedSpot::textureMaxU)
            .thenComparingInt(RenderedSpot::textureMaxV)
            .thenComparingInt(RenderedSpot::quadX0)
            .thenComparingInt(RenderedSpot::quadY0)
            .thenComparingInt(RenderedSpot::quadZ0)
            .thenComparingInt(RenderedSpot::quadTextureU0)
            .thenComparingInt(RenderedSpot::quadTextureV0)
            .thenComparingInt(RenderedSpot::quadX1)
            .thenComparingInt(RenderedSpot::quadY1)
            .thenComparingInt(RenderedSpot::quadZ1)
            .thenComparingInt(RenderedSpot::quadTextureU1)
            .thenComparingInt(RenderedSpot::quadTextureV1)
            .thenComparingInt(RenderedSpot::quadX2)
            .thenComparingInt(RenderedSpot::quadY2)
            .thenComparingInt(RenderedSpot::quadZ2)
            .thenComparingInt(RenderedSpot::quadTextureU2)
            .thenComparingInt(RenderedSpot::quadTextureV2)
            .thenComparingInt(RenderedSpot::quadX3)
            .thenComparingInt(RenderedSpot::quadY3)
            .thenComparingInt(RenderedSpot::quadZ3)
            .thenComparingInt(RenderedSpot::quadTextureU3)
            .thenComparingInt(RenderedSpot::quadTextureV3);
    private static List<RenderedSpot> activeSpots = List.of();
    private static Object lastLevel;
    private static GeometryMergeStats lastGeometryMergeStats = GeometryMergeStats.empty();

    public static void accept(SpotRecord spot) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(Minecraft.getInstance().level);
        SpotKey key = spotKey(spot);
        Map<SpotKey, RenderedSpot> legacySpots = SPOTS_BY_OWNER.computeIfAbsent(LEGACY_OWNER_ID, ignored -> new HashMap<>());

        if (!spot.visible()) {
            legacySpots.remove(key);
            if (legacySpots.isEmpty()) {
                SPOTS_BY_OWNER.remove(LEGACY_OWNER_ID);
            }
            rebuildActiveSpots();
            return;
        }

        legacySpots.put(key, renderedSpot(spot));
        rebuildActiveSpots();
    }

    public static void accept(SpotOverlayPayload payload) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(Minecraft.getInstance().level);

        if (payload.spots().isEmpty()) {
            SPOTS_BY_OWNER.remove(payload.ownerId());
            rebuildActiveSpots();
            return;
        }

        Map<SpotKey, RenderedSpot> ownerSpots = new HashMap<>();
        for (SpotRecord spot : payload.spots()) {
            if (spot.visible()) {
                ownerSpots.put(spotKey(spot), renderedSpot(spot));
            }
        }

        if (ownerSpots.isEmpty()) {
            SPOTS_BY_OWNER.remove(payload.ownerId());
        } else {
            SPOTS_BY_OWNER.put(payload.ownerId(), ownerSpots);
        }

        rebuildActiveSpots();
    }

    public static Collection<RenderedSpot> activeSpots() {
        if (Minecraft.getInstance().level == null) {
            clear();
            return List.of();
        }

        clearIfLevelChanged(Minecraft.getInstance().level);
        return activeSpots;
    }

    private static void rebuildActiveSpots() {
        if (SPOTS_BY_OWNER.isEmpty()) {
            activeSpots = List.of();
            return;
        }

        Map<SpotKey, RenderedSpot> mergedSpots = new HashMap<>();
        List<Integer> ownerIds = new ArrayList<>(SPOTS_BY_OWNER.keySet());
        ownerIds.sort(Integer::compare);

        for (int ownerId : ownerIds) {
            Map<SpotKey, RenderedSpot> ownerSpots = SPOTS_BY_OWNER.get(ownerId);

            if (ownerSpots == null) {
                continue;
            }

            List<Map.Entry<SpotKey, RenderedSpot>> entries = new ArrayList<>(ownerSpots.entrySet());
            entries.sort(Map.Entry.comparingByKey(SPOT_KEY_ORDER));

            for (Map.Entry<SpotKey, RenderedSpot> entry : entries) {
                mergedSpots.merge(entry.getKey(), entry.getValue(), ClientSpotCache::mergeSpot);
            }
        }

        List<RenderedSpot> sortedSpots = mergePartialGeometrySpots(mergedSpots.values());
        sortedSpots.sort(RENDERED_SPOT_ORDER);
        activeSpots = List.copyOf(sortedSpots);
    }

    private static List<RenderedSpot> mergePartialGeometrySpots(Collection<RenderedSpot> spots) {
        Map<PartialQuadGeometryKey, List<RenderedSpot>> partialGroups = new HashMap<>();
        List<RenderedSpot> result = new ArrayList<>(spots.size());

        for (RenderedSpot spot : spots) {
            if (isPartialFootprintQuad(spot)) {
                partialGroups.computeIfAbsent(PartialQuadGeometryKey.from(spot), ignored -> new ArrayList<>())
                        .add(spot);
            } else {
                result.add(spot);
            }
        }

        GeometryMergeStatsBuilder stats = new GeometryMergeStatsBuilder();
        stats.partialGeometryGroups = partialGroups.size();

        for (Map.Entry<PartialQuadGeometryKey, List<RenderedSpot>> entry : partialGroups.entrySet()) {
            List<RenderedSpot> group = entry.getValue();
            group.sort(RENDERED_SPOT_ORDER);

            if (group.size() == 1) {
                result.add(group.getFirst());
                continue;
            }

            RenderedSpot merged = group.getFirst();
            int colorBucketMask = merged.colorBucketMask();

            for (int index = 1; index < group.size(); index++) {
                RenderedSpot next = group.get(index);
                colorBucketMask |= next.colorBucketMask();
                merged = mergeSpot(merged, next);
            }

            result.add(merged);
            stats.partialGeometryMergedGroups++;
            stats.partialGeometryMergedInputSpots += group.size();

            int colorBuckets = Integer.bitCount(colorBucketMask);
            if (colorBuckets > 1) {
                stats.partialGeometryMulticolorGroups++;
            }

            if (group.size() > stats.worstGeometrySpots
                    || (group.size() == stats.worstGeometrySpots
                    && colorBuckets > stats.worstGeometryColorBuckets)) {
                stats.worstGeometryKey = entry.getKey().format();
                stats.worstGeometrySpots = group.size();
                stats.worstGeometryColorBuckets = colorBuckets;
            }
        }

        lastGeometryMergeStats = stats.build();
        return result;
    }

    public static void clear() {
        SPOTS_BY_OWNER.clear();
        activeSpots = List.of();
        lastLevel = null;
        lastGeometryMergeStats = GeometryMergeStats.empty();
    }

    private static void clearIfLevelChanged(Object level) {
        if (lastLevel == level) {
            return;
        }

        clear();
        lastLevel = level;
    }

    private static RenderedSpot renderedSpot(SpotRecord spot) {
        BlockPos pos = spot.pos();
        Direction face = spot.face();
        double localCenterU = (normalizedByte(spot.clipMinU()) + normalizedByte(spot.clipMaxU())) * 0.5D;
        double localCenterV = (normalizedByte(spot.clipMinV()) + normalizedByte(spot.clipMaxV())) * 0.5D;
        boolean sliced = spot.projectionMode() == ProjectionMode.FOOTPRINT_SLICE;
        boolean quad = spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD;
        double centerX = quad
                ? quadCenterX(pos, face, spot)
                : sliced
                ? faceCenterX(pos, face, localCenterU, localCenterV)
                : pos.getX() + 0.5D + face.getStepX() * (0.5D + SURFACE_OFFSET);
        double centerY = quad
                ? quadCenterY(pos, face, spot)
                : sliced
                ? faceCenterY(pos, face, localCenterU, localCenterV)
                : pos.getY() + 0.5D + face.getStepY() * (0.5D + SURFACE_OFFSET);
        double centerZ = quad
                ? quadCenterZ(pos, face, spot)
                : sliced
                ? faceCenterZ(pos, face, localCenterU, localCenterV)
                : pos.getZ() + 0.5D + face.getStepZ() * (0.5D + SURFACE_OFFSET);
        int ringRed = spot.coherentAlphaLevel() > 0 ? spot.coherentRed() : spot.strayRed();
        int ringGreen = spot.coherentAlphaLevel() > 0 ? spot.coherentGreen() : spot.strayGreen();
        int ringBlue = spot.coherentAlphaLevel() > 0 ? spot.coherentBlue() : spot.strayBlue();
        int ringRadiusLevel = Math.max(spot.coherentRadiusLevel(), spot.strayRadiusLevel());

        return new RenderedSpot(
                pos.immutable(),
                face,
                centerX,
                centerY,
                centerZ,
                spot.coherentAlphaLevel(),
                renderRadius(spot.coherentRadiusLevel(), 0.82D),
                alphaFor(spot.coherentAlphaLevel(), 1.0D),
                spot.coherentRed(),
                spot.coherentGreen(),
                spot.coherentBlue(),
                spot.strayAlphaLevel(),
                renderRadius(spot.strayRadiusLevel(), 1.25D),
                alphaFor(spot.strayAlphaLevel(), 0.72D),
                spot.strayRed(),
                spot.strayGreen(),
                spot.strayBlue(),
                spot.ringAlphaLevel(),
                renderRadius(ringRadiusLevel, 1.35D),
                alphaFor(spot.ringAlphaLevel(), 0.72D),
                ringRed,
                ringGreen,
                ringBlue,
                spot.projectionMode(),
                spot.clipMinU(),
                spot.clipMinV(),
                spot.clipMaxU(),
                spot.clipMaxV(),
                spot.textureMinU(),
                spot.textureMinV(),
                spot.textureMaxU(),
                spot.textureMaxV(),
                spot.quadX0(),
                spot.quadY0(),
                spot.quadZ0(),
                spot.quadTextureU0(),
                spot.quadTextureV0(),
                spot.quadX1(),
                spot.quadY1(),
                spot.quadZ1(),
                spot.quadTextureU1(),
                spot.quadTextureV1(),
                spot.quadX2(),
                spot.quadY2(),
                spot.quadZ2(),
                spot.quadTextureU2(),
                spot.quadTextureV2(),
                spot.quadX3(),
                spot.quadY3(),
                spot.quadZ3(),
                spot.quadTextureU3(),
                spot.quadTextureV3(),
                spot.debugMarker(),
                1,
                colorBucketMask(spot.coherentAlphaLevel(), spot.coherentRed(), spot.coherentGreen(), spot.coherentBlue(),
                        spot.strayAlphaLevel(), spot.strayRed(), spot.strayGreen(), spot.strayBlue())
        );
    }

    private static double renderRadius(int radiusLevel, double multiplier) {
        return (0.075D + Math.max(1, radiusLevel) * 0.048D) * multiplier;
    }

    private static int projectionRenderPriority(ProjectionMode projectionMode) {
        return switch (projectionMode) {
            case CENTERED_QUAD, FOOTPRINT_SLICE -> 0;
            case FOOTPRINT_QUAD -> 1;
            case DEBUG_FACE_CENTER -> 2;
        };
    }

    private static int alphaFor(int alphaLevel, double multiplier) {
        if (alphaLevel <= 0) {
            return 0;
        }

        return Math.max(0, Math.min(240, (int) Math.round((88 + alphaLevel * 10) * multiplier)));
    }

    private static RenderedSpot mergeSpot(RenderedSpot first, RenderedSpot second) {
        int coherentAlpha = composeAlpha(first.coherentAlpha(), second.coherentAlpha());
        int strayAlpha = composeAlpha(first.strayAlpha(), second.strayAlpha());
        int ringAlpha = composeAlpha(first.ringAlpha(), second.ringAlpha());

        return new RenderedSpot(
                first.pos(),
                first.face(),
                first.centerX(),
                first.centerY(),
                first.centerZ(),
                Math.max(first.coherentAlphaLevel(), second.coherentAlphaLevel()),
                Math.max(first.coherentRadius(), second.coherentRadius()),
                coherentAlpha,
                mixLightChannel(first.coherentRed(), first.coherentAlpha(), second.coherentRed(), second.coherentAlpha(), coherentAlpha),
                mixLightChannel(first.coherentGreen(), first.coherentAlpha(), second.coherentGreen(), second.coherentAlpha(), coherentAlpha),
                mixLightChannel(first.coherentBlue(), first.coherentAlpha(), second.coherentBlue(), second.coherentAlpha(), coherentAlpha),
                Math.max(first.strayAlphaLevel(), second.strayAlphaLevel()),
                Math.max(first.strayRadius(), second.strayRadius()),
                strayAlpha,
                mixLightChannel(first.strayRed(), first.strayAlpha(), second.strayRed(), second.strayAlpha(), strayAlpha),
                mixLightChannel(first.strayGreen(), first.strayAlpha(), second.strayGreen(), second.strayAlpha(), strayAlpha),
                mixLightChannel(first.strayBlue(), first.strayAlpha(), second.strayBlue(), second.strayAlpha(), strayAlpha),
                Math.max(first.ringAlphaLevel(), second.ringAlphaLevel()),
                Math.max(first.ringRadius(), second.ringRadius()),
                ringAlpha,
                mixLightChannel(first.ringRed(), first.ringAlpha(), second.ringRed(), second.ringAlpha(), ringAlpha),
                mixLightChannel(first.ringGreen(), first.ringAlpha(), second.ringGreen(), second.ringAlpha(), ringAlpha),
                mixLightChannel(first.ringBlue(), first.ringAlpha(), second.ringBlue(), second.ringAlpha(), ringAlpha),
                first.projectionMode(),
                first.clipMinU(),
                first.clipMinV(),
                first.clipMaxU(),
                first.clipMaxV(),
                first.textureMinU(),
                first.textureMinV(),
                first.textureMaxU(),
                first.textureMaxV(),
                first.quadX0(),
                first.quadY0(),
                first.quadZ0(),
                first.quadTextureU0(),
                first.quadTextureV0(),
                first.quadX1(),
                first.quadY1(),
                first.quadZ1(),
                first.quadTextureU1(),
                first.quadTextureV1(),
                first.quadX2(),
                first.quadY2(),
                first.quadZ2(),
                first.quadTextureU2(),
                first.quadTextureV2(),
                first.quadX3(),
                first.quadY3(),
                first.quadZ3(),
                first.quadTextureU3(),
                first.quadTextureV3(),
                first.debugMarker(),
                first.mergedContributions() + second.mergedContributions(),
                first.colorBucketMask() | second.colorBucketMask()
        );
    }

    private static int composeAlpha(int first, int second) {
        double firstOpacity = first / 255.0D;
        double secondOpacity = second / 255.0D;
        double opacity = 1.0D - (1.0D - firstOpacity) * (1.0D - secondOpacity);
        return Math.max(0, Math.min(240, (int) Math.round(opacity * 255.0D)));
    }

    private static int mixLightChannel(
            int firstChannel,
            int firstAlpha,
            int secondChannel,
            int secondAlpha,
            int outputAlpha
    ) {
        if (outputAlpha <= 0) {
            return clampColor(Math.max(firstChannel, secondChannel));
        }

        double firstEnergy = (firstChannel / 255.0D) * (firstAlpha / 255.0D);
        double secondEnergy = (secondChannel / 255.0D) * (secondAlpha / 255.0D);
        double outputOpacity = outputAlpha / 255.0D;
        double straightChannel = Math.min(1.0D, (firstEnergy + secondEnergy) / outputOpacity);
        return clampColor((int) Math.round(straightChannel * 255.0D));
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static int colorBucketMask(
            int coherentAlphaLevel,
            int coherentRed,
            int coherentGreen,
            int coherentBlue,
            int strayAlphaLevel,
            int strayRed,
            int strayGreen,
            int strayBlue
    ) {
        int red = coherentAlphaLevel > 0 ? coherentRed : strayRed;
        int green = coherentAlphaLevel > 0 ? coherentGreen : strayGreen;
        int blue = coherentAlphaLevel > 0 ? coherentBlue : strayBlue;
        int alphaLevel = coherentAlphaLevel > 0 ? coherentAlphaLevel : strayAlphaLevel;

        if (alphaLevel <= 0) {
            return 1;
        }

        int max = Math.max(red, Math.max(green, blue));

        if (max <= 0) {
            return 1;
        }

        int bucket = 0;
        double threshold = max * 0.42D;

        if (red >= threshold) {
            bucket |= 1;
        }

        if (green >= threshold) {
            bucket |= 2;
        }

        if (blue >= threshold) {
            bucket |= 4;
        }

        return 1 << bucket;
    }

    public static int colorBucketCount(RenderedSpot spot) {
        return Integer.bitCount(spot.colorBucketMask());
    }

    public static GeometryMergeStats geometryMergeStats() {
        return lastGeometryMergeStats;
    }

    private static boolean isPartialFootprintQuad(RenderedSpot spot) {
        return spot.projectionMode() == ProjectionMode.FOOTPRINT_QUAD
                && quadTextureArea(spot) < PARTIAL_QUAD_AREA_THRESHOLD;
    }

    private static double quadTextureArea(RenderedSpot spot) {
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

    private static SpotKey spotKey(SpotRecord spot) {
        return new SpotKey(
                spot.pos(),
                spot.face(),
                spot.projectionMode(),
                spot.clipMinU(),
                spot.clipMinV(),
                spot.clipMaxU(),
                spot.clipMaxV(),
                spot.textureMinU(),
                spot.textureMinV(),
                spot.textureMaxU(),
                spot.textureMaxV(),
                spot.quadX0(),
                spot.quadY0(),
                spot.quadZ0(),
                spot.quadTextureU0(),
                spot.quadTextureV0(),
                spot.quadX1(),
                spot.quadY1(),
                spot.quadZ1(),
                spot.quadTextureU1(),
                spot.quadTextureV1(),
                spot.quadX2(),
                spot.quadY2(),
                spot.quadZ2(),
                spot.quadTextureU2(),
                spot.quadTextureV2(),
                spot.quadX3(),
                spot.quadY3(),
                spot.quadZ3(),
                spot.quadTextureU3(),
                spot.quadTextureV3()
        );
    }

    private static double normalizedByte(int value) {
        return value / 255.0D;
    }

    private static double normalizedQuadUnit(int value) {
        return value / (double) SpotRecord.QUAD_QUANTIZATION_LEVEL;
    }

    private static double faceCenterX(BlockPos pos, Direction face, double localU, double localV) {
        SpotSurfaceFrame.LocalCoordinates local = SpotSurfaceFrame.surfaceLocal(face, localU, localV, SURFACE_OFFSET);
        return pos.getX() + local.x();
    }

    private static double faceCenterY(BlockPos pos, Direction face, double localU, double localV) {
        SpotSurfaceFrame.LocalCoordinates local = SpotSurfaceFrame.surfaceLocal(face, localU, localV, SURFACE_OFFSET);
        return pos.getY() + local.y();
    }

    private static double faceCenterZ(BlockPos pos, Direction face, double localU, double localV) {
        SpotSurfaceFrame.LocalCoordinates local = SpotSurfaceFrame.surfaceLocal(face, localU, localV, SURFACE_OFFSET);
        return pos.getZ() + local.z();
    }

    private static double quadCenterX(BlockPos pos, Direction face, SpotRecord spot) {
        return pos.getX()
                + (normalizedQuadUnit(spot.quadX0()) + normalizedQuadUnit(spot.quadX1())
                + normalizedQuadUnit(spot.quadX2()) + normalizedQuadUnit(spot.quadX3())) * 0.25D
                + face.getStepX() * SURFACE_OFFSET;
    }

    private static double quadCenterY(BlockPos pos, Direction face, SpotRecord spot) {
        return pos.getY()
                + (normalizedQuadUnit(spot.quadY0()) + normalizedQuadUnit(spot.quadY1())
                + normalizedQuadUnit(spot.quadY2()) + normalizedQuadUnit(spot.quadY3())) * 0.25D
                + face.getStepY() * SURFACE_OFFSET;
    }

    private static double quadCenterZ(BlockPos pos, Direction face, SpotRecord spot) {
        return pos.getZ()
                + (normalizedQuadUnit(spot.quadZ0()) + normalizedQuadUnit(spot.quadZ1())
                + normalizedQuadUnit(spot.quadZ2()) + normalizedQuadUnit(spot.quadZ3())) * 0.25D
                + face.getStepZ() * SURFACE_OFFSET;
    }

    private record SpotKey(
            BlockPos pos,
            Direction face,
            ProjectionMode projectionMode,
            int clipMinU,
            int clipMinV,
            int clipMaxU,
            int clipMaxV,
            int textureMinU,
            int textureMinV,
            int textureMaxU,
            int textureMaxV,
            int quadX0,
            int quadY0,
            int quadZ0,
            int quadTextureU0,
            int quadTextureV0,
            int quadX1,
            int quadY1,
            int quadZ1,
            int quadTextureU1,
            int quadTextureV1,
            int quadX2,
            int quadY2,
            int quadZ2,
            int quadTextureU2,
            int quadTextureV2,
            int quadX3,
            int quadY3,
            int quadZ3,
            int quadTextureU3,
            int quadTextureV3
    ) {
    }

    private record PartialQuadGeometryKey(
            BlockPos pos,
            Direction face,
            int quadX0,
            int quadY0,
            int quadZ0,
            int quadX1,
            int quadY1,
            int quadZ1,
            int quadX2,
            int quadY2,
            int quadZ2,
            int quadX3,
            int quadY3,
            int quadZ3
    ) {
        private static PartialQuadGeometryKey from(RenderedSpot spot) {
            return new PartialQuadGeometryKey(
                    spot.pos(),
                    spot.face(),
                    spot.quadX0(),
                    spot.quadY0(),
                    spot.quadZ0(),
                    spot.quadX1(),
                    spot.quadY1(),
                    spot.quadZ1(),
                    spot.quadX2(),
                    spot.quadY2(),
                    spot.quadZ2(),
                    spot.quadX3(),
                    spot.quadY3(),
                    spot.quadZ3()
            );
        }

        private String format() {
            return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "/" + face
                    + "/" + quadX0 + "," + quadY0 + "," + quadZ0
                    + ";" + quadX1 + "," + quadY1 + "," + quadZ1
                    + ";" + quadX2 + "," + quadY2 + "," + quadZ2
                    + ";" + quadX3 + "," + quadY3 + "," + quadZ3;
        }
    }

    private static final class GeometryMergeStatsBuilder {
        private int partialGeometryGroups;
        private int partialGeometryMergedGroups;
        private int partialGeometryMergedInputSpots;
        private int partialGeometryMulticolorGroups;
        private String worstGeometryKey = "none";
        private int worstGeometrySpots;
        private int worstGeometryColorBuckets;

        private GeometryMergeStats build() {
            return new GeometryMergeStats(
                    partialGeometryGroups,
                    partialGeometryMergedGroups,
                    partialGeometryMergedInputSpots,
                    partialGeometryMulticolorGroups,
                    worstGeometryKey,
                    worstGeometrySpots,
                    worstGeometryColorBuckets
            );
        }
    }

    public record GeometryMergeStats(
            int partialGeometryGroups,
            int partialGeometryMergedGroups,
            int partialGeometryMergedInputSpots,
            int partialGeometryMulticolorGroups,
            String worstGeometryKey,
            int worstGeometrySpots,
            int worstGeometryColorBuckets
    ) {
        private static GeometryMergeStats empty() {
            return new GeometryMergeStats(0, 0, 0, 0, "none", 0, 0);
        }
    }

    public record RenderedSpot(
            BlockPos pos,
            Direction face,
            double centerX,
            double centerY,
            double centerZ,
            int coherentAlphaLevel,
            double coherentRadius,
            int coherentAlpha,
            int coherentRed,
            int coherentGreen,
            int coherentBlue,
            int strayAlphaLevel,
            double strayRadius,
            int strayAlpha,
            int strayRed,
            int strayGreen,
            int strayBlue,
            int ringAlphaLevel,
            double ringRadius,
            int ringAlpha,
            int ringRed,
            int ringGreen,
            int ringBlue,
            ProjectionMode projectionMode,
            int clipMinU,
            int clipMinV,
            int clipMaxU,
            int clipMaxV,
            int textureMinU,
            int textureMinV,
            int textureMaxU,
            int textureMaxV,
            int quadX0,
            int quadY0,
            int quadZ0,
            int quadTextureU0,
            int quadTextureV0,
            int quadX1,
            int quadY1,
            int quadZ1,
            int quadTextureU1,
            int quadTextureV1,
            int quadX2,
            int quadY2,
            int quadZ2,
            int quadTextureU2,
            int quadTextureV2,
            int quadX3,
            int quadY3,
            int quadZ3,
            int quadTextureU3,
            int quadTextureV3,
            int debugMarker,
            int mergedContributions,
            int colorBucketMask
    ) {
    }

    private ClientSpotCache() {
    }
}
