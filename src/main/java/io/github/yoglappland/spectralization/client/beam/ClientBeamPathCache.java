package io.github.yoglappland.spectralization.client.beam;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientBeamPathCache {
    private static final Map<SegmentKey, BeamPathOverlayPayload.Segment> SEGMENTS = new HashMap<>();
    private static List<BeamPathOverlayPayload.Segment> activeSegments = List.of();
    private static Object lastLevel;

    public static void accept(BeamPathOverlayPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            clearLevel();
            return;
        }

        clearIfLevelChanged(minecraft.level);

        SEGMENTS.keySet().removeIf(key -> key.ownerId() == payload.ownerId());

        for (BeamPathOverlayPayload.Segment segment : payload.segments()) {
            SEGMENTS.put(new SegmentKey(payload.ownerId(), segment.from(), segment.to()), segment);
        }

        rebuildActiveSegments();
    }

    public static Collection<BeamPathOverlayPayload.Segment> activeSegments() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            clearLevel();
            return List.of();
        }

        clearIfLevelChanged(minecraft.level);

        return activeSegments;
    }

    public static void clear() {
        clearLevel();
    }

    private static void clearIfLevelChanged(Object level) {
        if (lastLevel == level) {
            return;
        }

        SEGMENTS.clear();
        activeSegments = List.of();
        lastLevel = level;
    }

    private static void clearLevel() {
        SEGMENTS.clear();
        activeSegments = List.of();
        lastLevel = null;
    }

    private static void rebuildActiveSegments() {
        activeSegments = SEGMENTS.isEmpty() ? List.of() : List.copyOf(SEGMENTS.values());
    }

    private record SegmentKey(int ownerId, BlockPos from, BlockPos to) {
        private SegmentKey {
            from = from.immutable();
            to = to.immutable();
        }
    }
    private ClientBeamPathCache() {
    }
}
