package io.github.yoglappland.spectralization.optics.fiber;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record FiberNetworkSnapshot(
        Map<BlockPos, FiberNode> nodes,
        List<FiberCandidateEdge> candidateEdges,
        List<FiberCompiledConnection> connections,
        Map<FiberSegmentKey, Integer> segmentUsage,
        Map<BlockPos, Integer> nodeUsage,
        long epoch
) {
    public static final FiberNetworkSnapshot EMPTY =
            new FiberNetworkSnapshot(Map.of(), List.of(), List.of(), Map.of(), Map.of(), 0L);

    public FiberNetworkSnapshot {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(candidateEdges, "candidateEdges");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(segmentUsage, "segmentUsage");
        Objects.requireNonNull(nodeUsage, "nodeUsage");
        nodes = Map.copyOf(nodes);
        candidateEdges = List.copyOf(candidateEdges);
        connections = List.copyOf(connections);
        segmentUsage = Map.copyOf(segmentUsage);
        nodeUsage = Map.copyOf(nodeUsage);
    }

    public Optional<FiberNode> nodeAt(BlockPos pos) {
        return Optional.ofNullable(nodes.get(pos));
    }

    public boolean hasNode(BlockPos pos) {
        return nodes.containsKey(pos);
    }

    public int nodeUsage(BlockPos pos) {
        return nodeUsage.getOrDefault(pos, 0);
    }

    public int nodeCapacity(BlockPos pos) {
        FiberNode node = nodes.get(pos);
        return node == null ? 0 : node.profile().maxConnections();
    }

    public int remainingConnections(BlockPos pos) {
        return Math.max(0, nodeCapacity(pos) - nodeUsage(pos));
    }

    public boolean canUseNode(BlockPos pos, int neededConnections) {
        return neededConnections > 0 && remainingConnections(pos) >= neededConnections;
    }

    public boolean canAcceptRoute(FiberRoute route) {
        Map<BlockPos, Integer> addedUsage = new HashMap<>();

        for (FiberSegment segment : route.segments()) {
            addedUsage.merge(segment.from(), 1, Integer::sum);
            addedUsage.merge(segment.to(), 1, Integer::sum);
        }

        for (Map.Entry<BlockPos, Integer> entry : addedUsage.entrySet()) {
            if (!canUseNode(entry.getKey(), entry.getValue())) {
                return false;
            }
        }

        return true;
    }
}
