package io.github.yoglappland.spectralization.optics.fiber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;

public final class FiberGraphCompiler {
    private static final Comparator<FiberNode> NODE_ORDER = Comparator
            .comparingInt((FiberNode node) -> node.pos().getX())
            .thenComparingInt(node -> node.pos().getY())
            .thenComparingInt(node -> node.pos().getZ());

    public static FiberNetworkSnapshot compile(
            LevelAccessor level,
            Collection<FiberNode> nodes,
            Collection<FiberConnection> storedConnections,
            long epoch
    ) {
        List<FiberNode> orderedNodes = nodes.stream()
                .sorted(NODE_ORDER)
                .toList();
        Map<BlockPos, FiberNode> nodesByPos = new HashMap<>();

        for (FiberNode node : orderedNodes) {
            nodesByPos.put(node.pos(), node);
        }

        List<FiberCandidateEdge> edges = compileCandidateEdges(level, orderedNodes);
        List<FiberCompiledConnection> compiledConnections = new ArrayList<>();
        Map<FiberSegmentKey, Integer> segmentUsage = new HashMap<>();
        Map<BlockPos, Integer> nodeUsage = new HashMap<>();

        for (FiberConnection connection : storedConnections) {
            FiberConnectionStatus status = validateConnection(level, nodesByPos, connection);
            compiledConnections.add(new FiberCompiledConnection(connection, status));

            if (status == FiberConnectionStatus.VALID) {
                for (FiberSegment segment : connection.route().segments()) {
                    segmentUsage.merge(FiberSegmentKey.of(segment.from(), segment.to()), 1, Integer::sum);
                    nodeUsage.merge(segment.from(), 1, Integer::sum);
                    nodeUsage.merge(segment.to(), 1, Integer::sum);
                }
            }
        }

        return new FiberNetworkSnapshot(nodesByPos, edges, compiledConnections, segmentUsage, nodeUsage, epoch);
    }

    private static List<FiberCandidateEdge> compileCandidateEdges(LevelAccessor level, List<FiberNode> nodes) {
        List<FiberCandidateEdge> edges = new ArrayList<>();

        for (int leftIndex = 0; leftIndex < nodes.size(); leftIndex++) {
            FiberNode left = nodes.get(leftIndex);

            for (int rightIndex = leftIndex + 1; rightIndex < nodes.size(); rightIndex++) {
                FiberNode right = nodes.get(rightIndex);
                double length = FiberDistances.segmentLength(left.pos(), right.pos());

                if (length > maxSharedSegmentLength(left, right)) {
                    continue;
                }

                if (!FiberLineOfSight.clearBetween(level, left.pos(), right.pos())) {
                    continue;
                }

                edges.add(new FiberCandidateEdge(left, right, length));
            }
        }

        return edges;
    }

    private static FiberConnectionStatus validateConnection(
            LevelAccessor level,
            Map<BlockPos, FiberNode> nodesByPos,
            FiberConnection connection
    ) {
        List<BlockPos> pathNodes = connection.nodes();

        if (pathNodes.size() < 2) {
            return FiberConnectionStatus.EMPTY_ROUTE;
        }

        for (BlockPos pathNode : pathNodes) {
            if (!nodesByPos.containsKey(pathNode)) {
                return FiberConnectionStatus.MISSING_NODE;
            }
        }

        if (nodesByPos.get(pathNodes.getFirst()).kind() != FiberNodeKind.INTERFACE
                || nodesByPos.get(pathNodes.getLast()).kind() != FiberNodeKind.INTERFACE) {
            return FiberConnectionStatus.WRONG_NODE_KIND;
        }

        for (int index = 1; index < pathNodes.size() - 1; index++) {
            if (nodesByPos.get(pathNodes.get(index)).kind() != FiberNodeKind.RELAY) {
                return FiberConnectionStatus.WRONG_NODE_KIND;
            }
        }

        for (int index = 0; index < pathNodes.size() - 1; index++) {
            FiberNode from = nodesByPos.get(pathNodes.get(index));
            FiberNode to = nodesByPos.get(pathNodes.get(index + 1));
            double length = FiberDistances.segmentLength(from.pos(), to.pos());

            if (length > maxSharedSegmentLength(from, to)) {
                return FiberConnectionStatus.SEGMENT_TOO_LONG;
            }

            if (!FiberLineOfSight.clearBetween(level, from.pos(), to.pos())) {
                return FiberConnectionStatus.BLOCKED;
            }
        }

        return FiberConnectionStatus.VALID;
    }

    private static double maxSharedSegmentLength(FiberNode left, FiberNode right) {
        return Math.min(left.profile().maxSegmentLength(), right.profile().maxSegmentLength());
    }

    private FiberGraphCompiler() {
    }
}
