package io.github.yoglappland.spectralization.client.compact;

import io.github.yoglappland.spectralization.network.CompactMachineOverlayPayload;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class ClientCompactMachineOverlayCache {
    private static final Comparator<CompactMachineOverlayPayload.Segment> SEGMENT_ORDER = Comparator
            .comparingInt((CompactMachineOverlayPayload.Segment segment) -> segment.from().getX())
            .thenComparingInt(segment -> segment.from().getY())
            .thenComparingInt(segment -> segment.from().getZ())
            .thenComparingInt(segment -> segment.to().getX())
            .thenComparingInt(segment -> segment.to().getY())
            .thenComparingInt(segment -> segment.to().getZ());
    private static List<CompactMachineOverlayPayload.Segment> activeSegments = List.of();
    private static Object lastLevel;

    public static void accept(CompactMachineOverlayPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }

        clearIfLevelChanged(minecraft.level);
        activeSegments = payload.segments().stream().sorted(SEGMENT_ORDER).toList();
    }

    public static Collection<CompactMachineOverlayPayload.Segment> activeSegments() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return List.of();
        }

        clearIfLevelChanged(minecraft.level);
        return activeSegments;
    }

    public static void clear() {
        activeSegments = List.of();
        lastLevel = null;
    }

    private static void clearIfLevelChanged(Object level) {
        if (lastLevel == level) {
            return;
        }

        activeSegments = List.of();
        lastLevel = level;
    }

    private ClientCompactMachineOverlayCache() {
    }
}
