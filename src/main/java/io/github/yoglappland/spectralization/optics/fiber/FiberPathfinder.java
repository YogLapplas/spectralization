package io.github.yoglappland.spectralization.optics.fiber;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;

public final class FiberPathfinder {
    private static final double EPSILON = 1.0E-9;

    public static Optional<FiberRoute> findRoute(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        if (start.equals(end) || !snapshot.hasNode(start) || !snapshot.hasNode(end)) {
            return Optional.empty();
        }

        Optional<FiberRoute> directRoute = findDirectRoute(snapshot, start, end);

        if (directRoute.isPresent()) {
            return directRoute;
        }

        return findRelayRoute(snapshot, start, end);
    }

    private static Optional<FiberRoute> findDirectRoute(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        for (FiberCandidateEdge edge : snapshot.candidateEdges()) {
            if ((edge.from().pos().equals(start) && edge.to().pos().equals(end))
                    || (edge.from().pos().equals(end) && edge.to().pos().equals(start))) {
                FiberRoute route = FiberRoute.fromNodes(List.of(start, end));
                return snapshot.canAcceptRoute(route) ? Optional.of(route) : Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Optional<FiberRoute> findRelayRoute(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        if (!snapshot.canUseNode(start, 1) || !snapshot.canUseNode(end, 1)) {
            return Optional.empty();
        }

        Map<BlockPos, List<FiberCandidateEdge>> edgesByNode = edgesByNode(snapshot);
        Map<BlockPos, Double> bestDistance = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        PriorityQueue<SearchState> pending = new PriorityQueue<>(Comparator.comparingDouble(SearchState::distance));

        bestDistance.put(start, 0.0);
        pending.add(new SearchState(start, 0.0));

        while (!pending.isEmpty()) {
            SearchState state = pending.remove();
            double best = bestDistance.getOrDefault(state.pos(), Double.POSITIVE_INFINITY);

            if (state.distance() > best + EPSILON) {
                continue;
            }

            if (state.pos().equals(end)) {
                return Optional.of(FiberRoute.fromNodes(reconstructPath(previous, start, end)));
            }

            for (FiberCandidateEdge edge : edgesByNode.getOrDefault(state.pos(), List.of())) {
                BlockPos next = edge.other(state.pos());
                FiberNode nextNode = snapshot.nodes().get(next);

                if (!next.equals(end) && nextNode.kind() != FiberNodeKind.RELAY) {
                    continue;
                }

                int neededConnections = next.equals(end) ? 1 : 2;

                if (!snapshot.canUseNode(next, neededConnections)) {
                    continue;
                }

                double distance = state.distance() + edge.length();
                double previousBest = bestDistance.getOrDefault(next, Double.POSITIVE_INFINITY);

                if (distance + EPSILON >= previousBest) {
                    continue;
                }

                bestDistance.put(next, distance);
                previous.put(next, state.pos());
                pending.add(new SearchState(next, distance));
            }
        }

        return Optional.empty();
    }

    private static Map<BlockPos, List<FiberCandidateEdge>> edgesByNode(FiberNetworkSnapshot snapshot) {
        Map<BlockPos, List<FiberCandidateEdge>> edgesByNode = new HashMap<>();

        for (FiberCandidateEdge edge : snapshot.candidateEdges()) {
            edgesByNode.computeIfAbsent(edge.from().pos(), ignored -> new ArrayList<>()).add(edge);
            edgesByNode.computeIfAbsent(edge.to().pos(), ignored -> new ArrayList<>()).add(edge);
        }

        return edgesByNode;
    }

    private static List<BlockPos> reconstructPath(Map<BlockPos, BlockPos> previous, BlockPos start, BlockPos end) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos cursor = end;
        reversed.add(cursor);

        while (!cursor.equals(start)) {
            cursor = previous.get(cursor);

            if (cursor == null) {
                return List.of();
            }

            reversed.add(cursor);
        }

        List<BlockPos> path = new ArrayList<>(reversed.size());

        for (int index = reversed.size() - 1; index >= 0; index--) {
            path.add(reversed.get(index));
        }

        return path;
    }

    private record SearchState(BlockPos pos, double distance) {
    }

    private FiberPathfinder() {
    }
}
