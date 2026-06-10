package io.github.yoglappland.spectralization.client.spot;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.network.SpotOverlayPayload;
import java.util.ArrayList;
import java.util.Collection;
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
    private static List<RenderedSpot> activeSpots = List.of();
    private static Object lastLevel;

    public static void accept(SpotRecord spot) {
        if (Minecraft.getInstance().level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(Minecraft.getInstance().level);
        SpotKey key = new SpotKey(spot.pos(), spot.face());
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
                ownerSpots.put(new SpotKey(spot.pos(), spot.face()), renderedSpot(spot));
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
        for (Map<SpotKey, RenderedSpot> ownerSpots : SPOTS_BY_OWNER.values()) {
            for (Map.Entry<SpotKey, RenderedSpot> entry : ownerSpots.entrySet()) {
                mergedSpots.merge(entry.getKey(), entry.getValue(), ClientSpotCache::mergeSpot);
            }
        }

        activeSpots = List.copyOf(new ArrayList<>(mergedSpots.values()));
    }

    private static void clear() {
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
        double centerX = pos.getX() + 0.5D + face.getStepX() * (0.5D + SURFACE_OFFSET);
        double centerY = pos.getY() + 0.5D + face.getStepY() * (0.5D + SURFACE_OFFSET);
        double centerZ = pos.getZ() + 0.5D + face.getStepZ() * (0.5D + SURFACE_OFFSET);
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
                ringBlue
        );
    }

    private static double renderRadius(int radiusLevel, double multiplier) {
        return (0.075D + Math.max(1, radiusLevel) * 0.048D) * multiplier;
    }

    private static int alphaFor(int alphaLevel, double multiplier) {
        return Math.max(0, Math.min(240, (int) Math.round((88 + alphaLevel * 10) * multiplier)));
    }

    private static RenderedSpot mergeSpot(RenderedSpot first, RenderedSpot second) {
        RenderedSpot coherentSource = first.coherentAlphaLevel() >= second.coherentAlphaLevel() ? first : second;
        RenderedSpot straySource = first.strayAlphaLevel() >= second.strayAlphaLevel() ? first : second;
        RenderedSpot ringSource = first.ringAlphaLevel() >= second.ringAlphaLevel() ? first : second;

        return new RenderedSpot(
                first.pos(),
                first.face(),
                first.centerX(),
                first.centerY(),
                first.centerZ(),
                Math.max(first.coherentAlphaLevel(), second.coherentAlphaLevel()),
                Math.max(first.coherentRadius(), second.coherentRadius()),
                Math.max(first.coherentAlpha(), second.coherentAlpha()),
                coherentSource.coherentRed(),
                coherentSource.coherentGreen(),
                coherentSource.coherentBlue(),
                Math.max(first.strayAlphaLevel(), second.strayAlphaLevel()),
                Math.max(first.strayRadius(), second.strayRadius()),
                Math.max(first.strayAlpha(), second.strayAlpha()),
                straySource.strayRed(),
                straySource.strayGreen(),
                straySource.strayBlue(),
                Math.max(first.ringAlphaLevel(), second.ringAlphaLevel()),
                Math.max(first.ringRadius(), second.ringRadius()),
                Math.max(first.ringAlpha(), second.ringAlpha()),
                ringSource.ringRed(),
                ringSource.ringGreen(),
                ringSource.ringBlue()
        );
    }

    private record SpotKey(BlockPos pos, Direction face) {
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
            int ringBlue
    ) {
    }

    private ClientSpotCache() {
    }
}
