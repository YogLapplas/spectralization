package io.github.yoglappland.spectralization.client.spot;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.SpotRecord.GeometryKey;
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
    private static final Map<Integer, Map<GeometryKey, RenderedSpot>> SPOTS_BY_OWNER = new HashMap<>();
    private static final Map<Integer, PendingSpotSnapshot> PENDING_SNAPSHOTS_BY_OWNER = new HashMap<>();
    private static final Map<Integer, Long> COMPLETED_SNAPSHOT_TOKENS_BY_OWNER = new HashMap<>();
    private static final Comparator<GeometryKey> SPOT_KEY_ORDER = Comparator
            .comparingInt((GeometryKey key) -> key.pos().getX())
            .thenComparingInt(key -> key.pos().getY())
            .thenComparingInt(key -> key.pos().getZ())
            .thenComparingInt(key -> key.projectionMode().ordinal())
            .thenComparingInt(key -> key.face().ordinal())
            .thenComparingInt(GeometryKey::clipMinU)
            .thenComparingInt(GeometryKey::clipMinV)
            .thenComparingInt(GeometryKey::clipMaxU)
            .thenComparingInt(GeometryKey::clipMaxV)
            .thenComparingInt(GeometryKey::textureMinU)
            .thenComparingInt(GeometryKey::textureMinV)
            .thenComparingInt(GeometryKey::textureMaxU)
            .thenComparingInt(GeometryKey::textureMaxV)
            .thenComparingInt(GeometryKey::quadX0)
            .thenComparingInt(GeometryKey::quadY0)
            .thenComparingInt(GeometryKey::quadZ0)
            .thenComparingInt(GeometryKey::quadTextureU0)
            .thenComparingInt(GeometryKey::quadTextureV0)
            .thenComparingInt(GeometryKey::quadX1)
            .thenComparingInt(GeometryKey::quadY1)
            .thenComparingInt(GeometryKey::quadZ1)
            .thenComparingInt(GeometryKey::quadTextureU1)
            .thenComparingInt(GeometryKey::quadTextureV1)
            .thenComparingInt(GeometryKey::quadX2)
            .thenComparingInt(GeometryKey::quadY2)
            .thenComparingInt(GeometryKey::quadZ2)
            .thenComparingInt(GeometryKey::quadTextureU2)
            .thenComparingInt(GeometryKey::quadTextureV2)
            .thenComparingInt(GeometryKey::quadX3)
            .thenComparingInt(GeometryKey::quadY3)
            .thenComparingInt(GeometryKey::quadZ3)
            .thenComparingInt(GeometryKey::quadTextureU3)
            .thenComparingInt(GeometryKey::quadTextureV3);
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
        GeometryKey key = spotKey(spot);
        Map<GeometryKey, RenderedSpot> legacySpots = SPOTS_BY_OWNER.computeIfAbsent(LEGACY_OWNER_ID, ignored -> new HashMap<>());

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

        if (payload.totalSpots() == 0) {
            SPOTS_BY_OWNER.remove(payload.ownerId());
            PENDING_SNAPSHOTS_BY_OWNER.remove(payload.ownerId());
            COMPLETED_SNAPSHOT_TOKENS_BY_OWNER.remove(payload.ownerId());
            rebuildActiveSpots();
            return;
        }

        Long completedToken = COMPLETED_SNAPSHOT_TOKENS_BY_OWNER.get(payload.ownerId());
        if (completedToken != null && completedToken == payload.snapshotToken()) {
            return;
        }

        PendingSpotSnapshot pending = PENDING_SNAPSHOTS_BY_OWNER.get(payload.ownerId());
        if (pending == null || !pending.matches(payload)) {
            pending = new PendingSpotSnapshot(payload);
            PENDING_SNAPSHOTS_BY_OWNER.put(payload.ownerId(), pending);
        }
        if (!pending.accept(payload) || !pending.complete()) {
            return;
        }

        List<SpotRecord> completedSpots = pending.assemble();
        PENDING_SNAPSHOTS_BY_OWNER.remove(payload.ownerId());
        COMPLETED_SNAPSHOT_TOKENS_BY_OWNER.put(payload.ownerId(), payload.snapshotToken());

        Map<GeometryKey, RenderedSpot> ownerSpots = new HashMap<>();
        for (SpotRecord spot : completedSpots) {
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

        List<RenderedSpot> sortedSpots = new ArrayList<>();
        List<Integer> ownerIds = new ArrayList<>(SPOTS_BY_OWNER.keySet());
        ownerIds.sort(Integer::compare);

        for (int ownerId : ownerIds) {
            Map<GeometryKey, RenderedSpot> ownerSpots = SPOTS_BY_OWNER.get(ownerId);

            if (ownerSpots == null) {
                continue;
            }

            List<Map.Entry<GeometryKey, RenderedSpot>> entries = new ArrayList<>(ownerSpots.entrySet());
            entries.sort(Map.Entry.comparingByKey(SPOT_KEY_ORDER));

            for (Map.Entry<GeometryKey, RenderedSpot> entry : entries) {
                sortedSpots.add(entry.getValue());
            }
        }

        lastGeometryMergeStats = analyzePartialGeometrySpots(sortedSpots);
        sortedSpots.sort(RENDERED_SPOT_ORDER);
        activeSpots = List.copyOf(sortedSpots);
    }

    private static GeometryMergeStats analyzePartialGeometrySpots(Collection<RenderedSpot> spots) {
        Map<PartialQuadGeometryKey, List<RenderedSpot>> partialGroups = new HashMap<>();

        for (RenderedSpot spot : spots) {
            if (isPartialFootprintQuad(spot)) {
                partialGroups.computeIfAbsent(PartialQuadGeometryKey.from(spot), ignored -> new ArrayList<>())
                        .add(spot);
            }
        }

        GeometryMergeStatsBuilder stats = new GeometryMergeStatsBuilder();
        stats.partialGeometryGroups = partialGroups.size();

        for (Map.Entry<PartialQuadGeometryKey, List<RenderedSpot>> entry : partialGroups.entrySet()) {
            List<RenderedSpot> group = entry.getValue();

            if (group.size() < 2) {
                continue;
            }

            int colorBucketMask = 0;
            for (RenderedSpot spot : group) {
                colorBucketMask |= spot.colorBucketMask();
            }

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

        return stats.build();
    }

    public static void clear() {
        SPOTS_BY_OWNER.clear();
        PENDING_SNAPSHOTS_BY_OWNER.clear();
        COMPLETED_SNAPSHOT_TOKENS_BY_OWNER.clear();
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
                alphaFor(spot.coherentAlphaLevel(), 240, 0.65D),
                spot.coherentRed(),
                spot.coherentGreen(),
                spot.coherentBlue(),
                spot.strayAlphaLevel(),
                renderRadius(spot.strayRadiusLevel(), 1.25D),
                alphaFor(spot.strayAlphaLevel(), 168, 0.80D),
                spot.strayRed(),
                spot.strayGreen(),
                spot.strayBlue(),
                spot.ringAlphaLevel(),
                renderRadius(ringRadiusLevel, 1.35D),
                alphaFor(spot.ringAlphaLevel(), 128, 0.90D),
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

    private static int alphaFor(int alphaLevel, int maxAlpha, double gamma) {
        if (alphaLevel <= 0) {
            return 0;
        }

        double normalized = Math.min(15, alphaLevel) / 15.0D;
        return Math.max(0, Math.min(255, (int) Math.round(maxAlpha * Math.pow(normalized, gamma))));
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

    private static GeometryKey spotKey(SpotRecord spot) {
        return spot.geometryKey();
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

    private static final class PendingSpotSnapshot {
        private final long snapshotToken;
        private final int chunkCount;
        private final int totalSpots;
        private final List<List<SpotRecord>> chunks;
        private int receivedChunks;

        private PendingSpotSnapshot(SpotOverlayPayload firstPayload) {
            snapshotToken = firstPayload.snapshotToken();
            chunkCount = firstPayload.chunkCount();
            totalSpots = firstPayload.totalSpots();
            chunks = new ArrayList<>(chunkCount);
            for (int index = 0; index < chunkCount; index++) {
                chunks.add(null);
            }
        }

        private boolean matches(SpotOverlayPayload payload) {
            return snapshotToken == payload.snapshotToken()
                    && chunkCount == payload.chunkCount()
                    && totalSpots == payload.totalSpots();
        }

        private boolean accept(SpotOverlayPayload payload) {
            if (!matches(payload) || chunks.get(payload.chunkIndex()) != null) {
                return false;
            }
            chunks.set(payload.chunkIndex(), payload.spots());
            receivedChunks++;
            return true;
        }

        private boolean complete() {
            return receivedChunks == chunkCount;
        }

        private List<SpotRecord> assemble() {
            List<SpotRecord> spots = new ArrayList<>(totalSpots);
            for (List<SpotRecord> chunk : chunks) {
                if (chunk == null) {
                    throw new IllegalStateException("Cannot assemble an incomplete spot snapshot");
                }
                spots.addAll(chunk);
            }
            if (spots.size() != totalSpots) {
                throw new IllegalStateException("Reassembled spot snapshot has the wrong size");
            }
            return spots;
        }
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
