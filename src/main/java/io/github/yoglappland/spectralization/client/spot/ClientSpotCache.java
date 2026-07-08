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
    private static final double SURFACE_OFFSET = 0.003D;
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

        List<RenderedSpot> sortedSpots = new ArrayList<>(mergedSpots.values());
        sortedSpots.sort(RENDERED_SPOT_ORDER);
        activeSpots = List.copyOf(sortedSpots);
    }

    public static void clear() {
        SPOTS_BY_OWNER.clear();
        activeSpots = List.of();
        lastLevel = null;
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
                spot.quadTextureV3()
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
                mixChannel(first.coherentRed(), first.coherentAlpha(), second.coherentRed(), second.coherentAlpha()),
                mixChannel(first.coherentGreen(), first.coherentAlpha(), second.coherentGreen(), second.coherentAlpha()),
                mixChannel(first.coherentBlue(), first.coherentAlpha(), second.coherentBlue(), second.coherentAlpha()),
                Math.max(first.strayAlphaLevel(), second.strayAlphaLevel()),
                Math.max(first.strayRadius(), second.strayRadius()),
                strayAlpha,
                mixChannel(first.strayRed(), first.strayAlpha(), second.strayRed(), second.strayAlpha()),
                mixChannel(first.strayGreen(), first.strayAlpha(), second.strayGreen(), second.strayAlpha()),
                mixChannel(first.strayBlue(), first.strayAlpha(), second.strayBlue(), second.strayAlpha()),
                Math.max(first.ringAlphaLevel(), second.ringAlphaLevel()),
                Math.max(first.ringRadius(), second.ringRadius()),
                ringAlpha,
                mixChannel(first.ringRed(), first.ringAlpha(), second.ringRed(), second.ringAlpha()),
                mixChannel(first.ringGreen(), first.ringAlpha(), second.ringGreen(), second.ringAlpha()),
                mixChannel(first.ringBlue(), first.ringAlpha(), second.ringBlue(), second.ringAlpha()),
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
                first.quadTextureV3()
        );
    }

    private static int composeAlpha(int first, int second) {
        double firstOpacity = first / 255.0D;
        double secondOpacity = second / 255.0D;
        double opacity = 1.0D - (1.0D - firstOpacity) * (1.0D - secondOpacity);
        return Math.max(0, Math.min(240, (int) Math.round(opacity * 255.0D)));
    }

    private static int mixChannel(int firstChannel, int firstAlpha, int secondChannel, int secondAlpha) {
        int weight = firstAlpha + secondAlpha;

        if (weight <= 0) {
            return Math.max(0, Math.min(255, firstChannel));
        }

        return Math.max(
                0,
                Math.min(
                        255,
                        (int) Math.round((firstChannel * firstAlpha + secondChannel * secondAlpha) / (double) weight)
                )
        );
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
            int quadTextureV3
    ) {
    }

    private ClientSpotCache() {
    }
}
