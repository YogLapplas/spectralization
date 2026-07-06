package io.github.yoglappland.spectralization.client.beam;

import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload;
import io.github.yoglappland.spectralization.network.BeamPathOverlayPayload.EndpointPlacement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class ClientBeamPathCache {
    private static final Map<SegmentKey, BeamPathOverlayPayload.Segment> SEGMENTS = new HashMap<>();
    private static final Comparator<RenderedSegment> SEGMENT_ORDER = Comparator
            .comparingInt((RenderedSegment rendered) -> rendered.segment().from().getX())
            .thenComparingInt(rendered -> rendered.segment().from().getY())
            .thenComparingInt(rendered -> rendered.segment().from().getZ())
            .thenComparingInt(rendered -> rendered.segment().to().getX())
            .thenComparingInt(rendered -> rendered.segment().to().getY())
            .thenComparingInt(rendered -> rendered.segment().to().getZ())
            .thenComparingInt(rendered -> rendered.segment().direction().ordinal())
            .thenComparingInt(rendered -> rendered.segment().startPlacement().ordinal())
            .thenComparingInt(rendered -> rendered.segment().startSide().ordinal())
            .thenComparingInt(rendered -> rendered.segment().endPlacement().ordinal())
            .thenComparingInt(rendered -> rendered.segment().endSide().ordinal());
    private static List<BeamPathOverlayPayload.Segment> activeSegments = List.of();
    private static List<RenderedSegment> activeRenderedSegments = List.of();
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
            SEGMENTS.put(SegmentKey.of(payload.ownerId(), segment), segment);
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

    public static Collection<RenderedSegment> activeRenderedSegments() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            clearLevel();
            return List.of();
        }

        clearIfLevelChanged(minecraft.level);

        return activeRenderedSegments;
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
        activeRenderedSegments = List.of();
        lastLevel = level;
    }

    private static void clearLevel() {
        SEGMENTS.clear();
        activeSegments = List.of();
        activeRenderedSegments = List.of();
        lastLevel = null;
    }

    private static void rebuildActiveSegments() {
        if (SEGMENTS.isEmpty()) {
            activeSegments = List.of();
            activeRenderedSegments = List.of();
            return;
        }

        Map<PhysicalSegmentKey, OwnedSegment> mergedSegments = new HashMap<>();

        for (Map.Entry<SegmentKey, BeamPathOverlayPayload.Segment> entry : SEGMENTS.entrySet()) {
            OwnedSegment ownedSegment = new OwnedSegment(entry.getKey().ownerId(), entry.getValue());
            mergedSegments.merge(
                    PhysicalSegmentKey.of(entry.getValue()),
                    ownedSegment,
                    ClientBeamPathCache::mergeOwnedSegment
            );
        }

        List<RenderedSegment> rebuilt = new ArrayList<>(mergedSegments.size());

        for (OwnedSegment ownedSegment : mergedSegments.values()) {
            rebuilt.add(new RenderedSegment(ownedSegment.ownerId(), ownedSegment.segment()));
        }

        rebuilt.sort(SEGMENT_ORDER);
        activeRenderedSegments = List.copyOf(rebuilt);
        activeSegments = rebuilt.stream().map(RenderedSegment::segment).toList();
    }

    private static OwnedSegment mergeOwnedSegment(OwnedSegment first, OwnedSegment second) {
        boolean firstSystem = first.ownerId() < 0;
        boolean secondSystem = second.ownerId() < 0;

        if (firstSystem != secondSystem) {
            return firstSystem ? first : second;
        }

        int visualCompare = Integer.compare(first.segment().visualLevel(), second.segment().visualLevel());
        if (visualCompare != 0) {
            return visualCompare > 0 ? first : second;
        }

        int ownerCompare = Integer.compare(first.ownerId(), second.ownerId());
        return ownerCompare <= 0 ? first : second;
    }

    private record SegmentKey(
            int ownerId,
            BlockPos from,
            BlockPos to,
            Direction direction,
            EndpointPlacement startPlacement,
            Direction startSide,
            EndpointPlacement endPlacement,
            Direction endSide
    ) {
        private static SegmentKey of(int ownerId, BeamPathOverlayPayload.Segment segment) {
            return new SegmentKey(
                    ownerId,
                    segment.from(),
                    segment.to(),
                    segment.direction(),
                    segment.startPlacement(),
                    segment.startSide(),
                    segment.endPlacement(),
                    segment.endSide()
            );
        }

        private SegmentKey {
            from = from.immutable();
            to = to.immutable();
        }
    }

    private record PhysicalSegmentKey(EndpointKey first, EndpointKey second) {
        private static PhysicalSegmentKey of(BeamPathOverlayPayload.Segment segment) {
            EndpointKey start = EndpointKey.start(segment);
            EndpointKey end = EndpointKey.end(segment);

            return compare(start, end) <= 0
                    ? new PhysicalSegmentKey(start, end)
                    : new PhysicalSegmentKey(end, start);
        }
    }

    private record EndpointKey(BlockPos pos, EndpointPlacement placement, Direction side) {
        private static EndpointKey start(BeamPathOverlayPayload.Segment segment) {
            return of(segment.from(), segment.startPlacement(), segment.startSide());
        }

        private static EndpointKey end(BeamPathOverlayPayload.Segment segment) {
            return of(segment.to(), segment.endPlacement(), segment.endSide());
        }

        private static EndpointKey of(BlockPos pos, EndpointPlacement placement, Direction side) {
            Direction normalizedSide = placement == EndpointPlacement.BLOCK_FACE ? side : Direction.NORTH;
            return new EndpointKey(pos, placement, normalizedSide);
        }

        private EndpointKey {
            pos = pos.immutable();
        }
    }

    private record OwnedSegment(int ownerId, BeamPathOverlayPayload.Segment segment) {
    }

    public record RenderedSegment(int ownerId, BeamPathOverlayPayload.Segment segment) {
    }

    private static int compare(BlockPos left, BlockPos right) {
        int dx = Integer.compare(left.getX(), right.getX());
        if (dx != 0) {
            return dx;
        }

        int dy = Integer.compare(left.getY(), right.getY());
        if (dy != 0) {
            return dy;
        }

        return Integer.compare(left.getZ(), right.getZ());
    }

    private static int compare(EndpointKey left, EndpointKey right) {
        int posCompare = compare(left.pos(), right.pos());
        if (posCompare != 0) {
            return posCompare;
        }

        int placementCompare = Integer.compare(left.placement().ordinal(), right.placement().ordinal());
        if (placementCompare != 0) {
            return placementCompare;
        }

        return Integer.compare(left.side().ordinal(), right.side().ordinal());
    }

    private ClientBeamPathCache() {
    }
}
