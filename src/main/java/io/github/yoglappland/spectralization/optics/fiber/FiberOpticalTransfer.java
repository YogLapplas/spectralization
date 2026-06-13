package io.github.yoglappland.spectralization.optics.fiber;

import io.github.yoglappland.spectralization.block.FiberOpticInterfaceBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class FiberOpticalTransfer {
    private static final Comparator<BlockPos> POS_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(pos -> pos.getY())
            .thenComparingInt(pos -> pos.getZ());

    public static List<OutputPort> remoteOutputPorts(
            ServerLevel level,
            BlockPos inputPos,
            Direction incomingDirection
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(inputPos, "inputPos");
        Objects.requireNonNull(incomingDirection, "incomingDirection");

        if (!level.isLoaded(inputPos)) {
            return List.of();
        }

        BlockState inputState = level.getBlockState(inputPos);

        if (!(inputState.getBlock() instanceof FiberOpticInterfaceBlock)
                || inputState.getValue(FiberOpticInterfaceBlock.FACING) != incomingDirection) {
            return List.of();
        }

        FiberNetworkSnapshot snapshot = FiberNetworkIndex.snapshot(level);
        FiberNode inputNode = snapshot.nodeAt(inputPos).orElse(null);

        if (inputNode == null || inputNode.kind() != FiberNodeKind.INTERFACE) {
            return List.of();
        }

        List<BlockPos> outputPositions = directEndpointOutputs(snapshot, inputPos);

        if (outputPositions.isEmpty()) {
            return List.of();
        }

        List<OutputPort> outputPorts = new ArrayList<>();

        for (BlockPos outputPos : outputPositions) {
            FiberNode outputNode = snapshot.nodeAt(outputPos).orElse(null);

            if (outputNode == null || outputNode.kind() != FiberNodeKind.INTERFACE || !level.isLoaded(outputPos)) {
                continue;
            }

            BlockState outputState = level.getBlockState(outputPos);

            if (!(outputState.getBlock() instanceof FiberOpticInterfaceBlock)) {
                continue;
            }

            outputPorts.add(new OutputPort(outputPos, outputState.getValue(FiberOpticInterfaceBlock.FACING)));
        }

        if (outputPorts.isEmpty()) {
            return List.of();
        }

        double splitGain = 1.0D / outputPorts.size();
        return outputPorts.stream()
                .map(outputPort -> new OutputPort(outputPort.pos(), outputPort.direction(), splitGain))
                .toList();
    }

    public static List<FiberRoute> directEndpointRoutes(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");

        if (start.equals(end)) {
            return List.of();
        }

        List<FiberRoute> routes = new ArrayList<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            FiberConnection connection = compiledConnection.connection();

            if (connection.connects(start, end)) {
                routes.add(connection.route());
            }
        }

        return routes.isEmpty() ? List.of() : List.copyOf(routes);
    }

    public static Optional<FiberRoute> routeBetween(FiberNetworkSnapshot snapshot, BlockPos start, BlockPos end) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");

        if (start.equals(end) || !snapshot.hasNode(start) || !snapshot.hasNode(end)) {
            return Optional.empty();
        }

        Map<BlockPos, List<RouteEdge>> adjacency = weightedAdjacency(snapshot);
        Map<BlockPos, Double> bestDistance = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        java.util.PriorityQueue<RouteState> pending = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(RouteState::distance)
        );

        bestDistance.put(start, 0.0D);
        pending.add(new RouteState(start, 0.0D));

        while (!pending.isEmpty()) {
            RouteState state = pending.remove();
            double best = bestDistance.getOrDefault(state.pos(), Double.POSITIVE_INFINITY);

            if (state.distance() > best + 1.0E-9D) {
                continue;
            }

            if (state.pos().equals(end)) {
                return Optional.of(FiberRoute.fromNodes(reconstructPath(previous, start, end)));
            }

            for (RouteEdge edge : adjacency.getOrDefault(state.pos(), List.of())) {
                BlockPos next = edge.other(state.pos());
                double distance = state.distance() + edge.length();
                double previousBest = bestDistance.getOrDefault(next, Double.POSITIVE_INFINITY);

                if (distance + 1.0E-9D >= previousBest) {
                    continue;
                }

                bestDistance.put(next, distance);
                previous.put(next, state.pos());
                pending.add(new RouteState(next, distance));
            }
        }

        return Optional.empty();
    }

    private static List<BlockPos> directEndpointOutputs(FiberNetworkSnapshot snapshot, BlockPos inputPos) {
        List<BlockPos> outputs = new ArrayList<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            FiberConnection connection = compiledConnection.connection();
            BlockPos outputPos = null;

            if (connection.endpointA().equals(inputPos)) {
                outputPos = connection.endpointB();
            } else if (connection.endpointB().equals(inputPos)) {
                outputPos = connection.endpointA();
            }

            if (outputPos != null && !outputs.contains(outputPos)) {
                outputs.add(outputPos);
            }
        }

        if (outputs.isEmpty()) {
            return List.of();
        }

        outputs.sort(POS_ORDER);
        return List.copyOf(outputs);
    }

    private static Map<BlockPos, List<RouteEdge>> weightedAdjacency(FiberNetworkSnapshot snapshot) {
        Map<BlockPos, List<RouteEdge>> adjacency = new HashMap<>();

        for (FiberCompiledConnection compiledConnection : snapshot.connections()) {
            if (!compiledConnection.valid()) {
                continue;
            }

            for (FiberSegment segment : compiledConnection.connection().route().segments()) {
                RouteEdge edge = new RouteEdge(segment.from(), segment.to(), segment.length());
                adjacency.computeIfAbsent(segment.from(), ignored -> new ArrayList<>()).add(edge);
                adjacency.computeIfAbsent(segment.to(), ignored -> new ArrayList<>()).add(edge);
            }
        }

        return adjacency;
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

    public record OutputPort(BlockPos pos, Direction direction, double gain) {
        public OutputPort(BlockPos pos, Direction direction) {
            this(pos, direction, 1.0D);
        }

        public OutputPort {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(direction, "direction");
            pos = pos.immutable();

            if (!Double.isFinite(gain) || gain <= 0.0D || gain > 1.0D) {
                throw new IllegalArgumentException("Fiber output gain must be finite and between 0 and 1");
            }
        }
    }

    private record RouteEdge(BlockPos from, BlockPos to, double length) {
        private RouteEdge {
            from = from.immutable();
            to = to.immutable();
        }

        private BlockPos other(BlockPos pos) {
            if (from.equals(pos)) {
                return to;
            }

            if (to.equals(pos)) {
                return from;
            }

            throw new IllegalArgumentException("Position is not on this fiber route edge");
        }
    }

    private record RouteState(BlockPos pos, double distance) {
    }

    private FiberOpticalTransfer() {
    }
}
