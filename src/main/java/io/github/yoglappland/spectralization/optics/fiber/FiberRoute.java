package io.github.yoglappland.spectralization.optics.fiber;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FiberRoute(
        List<BlockPos> nodes,
        List<FiberSegment> segments,
        double totalLength
) {
    public FiberRoute {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(segments, "segments");

        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Fiber route must contain at least two nodes");
        }

        nodes = nodes.stream().map(BlockPos::immutable).toList();
        segments = List.copyOf(segments);

        if (segments.size() != nodes.size() - 1) {
            throw new IllegalArgumentException("Fiber route segment count must match node path");
        }

        if (!Double.isFinite(totalLength) || totalLength <= 0.0) {
            throw new IllegalArgumentException("Fiber route length must be finite and positive");
        }
    }

    public static FiberRoute fromNodes(List<BlockPos> nodes) {
        Objects.requireNonNull(nodes, "nodes");

        if (nodes.size() < 2) {
            throw new IllegalArgumentException("Fiber route must contain at least two nodes");
        }

        List<FiberSegment> segments = new ArrayList<>();
        double length = 0.0;

        for (int index = 0; index < nodes.size() - 1; index++) {
            FiberSegment segment = FiberSegment.between(nodes.get(index), nodes.get(index + 1));
            segments.add(segment);
            length += segment.length();
        }

        return new FiberRoute(nodes, segments, length);
    }

    public BlockPos start() {
        return nodes.getFirst();
    }

    public BlockPos end() {
        return nodes.getLast();
    }

    public int materialLength() {
        return FiberDistances.materialLength(totalLength);
    }
}
