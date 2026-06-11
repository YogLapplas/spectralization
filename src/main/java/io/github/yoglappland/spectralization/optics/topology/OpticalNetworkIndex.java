package io.github.yoglappland.spectralization.optics.topology;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.IntrinsicOpticalSources;
import io.github.yoglappland.spectralization.optics.OpticalElement;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OpticalNetworkCompiler;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldEffectType;
import io.github.yoglappland.spectralization.optics.field.OpticalFieldSources;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class OpticalNetworkIndex {
    public static final int DEFAULT_REBUILD_RADIUS = 64;
    public static final int MIN_REBUILD_RADIUS = 8;
    public static final int MAX_REBUILD_RADIUS = 128;
    private static final BeamPacket TOPOLOGY_TEST_BEAM = BeamPacket.single(
            new PlaneWaveComponent(FrequencyKey.DEBUG_VISIBLE, 1.0, Direction.NORTH, CoherenceKind.COHERENT),
            BeamEnvelope.DEFAULT_COLLIMATED
    );
    private static final Map<ResourceKey<Level>, OpticalNetworkSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    public static OpticalNetworkSnapshot snapshot(ServerLevel level) {
        return SNAPSHOTS.getOrDefault(level.dimension(), OpticalNetworkSnapshot.EMPTY);
    }

    public static void clearAll() {
        SNAPSHOTS.clear();
    }

    public static void clear(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            SNAPSHOTS.remove(serverLevel.dimension());
        }
    }

    public static void markDirty(LevelAccessor level) {
        if (level instanceof ServerLevel serverLevel) {
            markDirty(serverLevel);
        }
    }

    public static void markDirty(ServerLevel level) {
        OpticalNetworkSnapshot current = snapshot(level);
        SNAPSHOTS.put(level.dimension(), new OpticalNetworkSnapshot(
                current.networks(),
                current.overlayPositions(),
                true
        ));
    }

    public static OpticalNetworkSnapshot rebuildAround(ServerLevel level, BlockPos center, int requestedRadius) {
        int radius = clampRadius(requestedRadius);
        Map<BlockPos, OpticalNodeRecord> candidates = findCandidateNodes(level, center, radius);

        if (candidates.isEmpty()) {
            OpticalNetworkSnapshot snapshot = new OpticalNetworkSnapshot(List.of(), Set.of(), false);
            SNAPSHOTS.put(level.dimension(), snapshot);
            return snapshot;
        }

        ScanResult scanResult = scanReachableEdges(level, candidates, radius);
        Map<BlockPos, Set<BlockPos>> adjacency = buildAdjacency(scanResult.reachableNodes(), scanResult.edges());
        OpticalNetworkSnapshot snapshot = buildSnapshot(candidates, scanResult, adjacency);
        SNAPSHOTS.put(level.dimension(), snapshot);
        return snapshot;
    }

    private static Map<BlockPos, OpticalNodeRecord> findCandidateNodes(ServerLevel level, BlockPos center, int radius) {
        Map<BlockPos, OpticalNodeRecord> candidates = new HashMap<>();
        int minX = center.getX() - radius;
        int maxX = center.getX() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + radius);
        int minZ = center.getZ() - radius;
        int maxZ = center.getZ() + radius;

        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos immutablePos = pos.immutable();

            if (!level.isLoaded(immutablePos)) {
                continue;
            }

            BlockState state = level.getBlockState(immutablePos);
            OpticalNodeFlags flags = flagsFor(state);

            if (flags.possibleSource() || flags.opticalElement() || flags.materialNode()) {
                candidates.put(immutablePos, new OpticalNodeRecord(immutablePos, flags));
            }
        }

        return candidates;
    }

    private static OpticalNodeFlags flagsFor(BlockState state) {
        Block block = state.getBlock();

        return new OpticalNodeFlags(
                IntrinsicOpticalSources.isSource(state),
                block instanceof OpticalElement,
                OpticalMaterialProfiles.isExplicitOpticalMaterial(state)
        );
    }

    private static ScanResult scanReachableEdges(
            ServerLevel level,
            Map<BlockPos, OpticalNodeRecord> candidates,
            int radius
    ) {
        Set<PotentialEdge> edges = new LinkedHashSet<>();
        Map<PotentialEdge, Set<BlockPos>> scatterMarkersByEdge = new HashMap<>();
        Set<BlockPos> reachableNodes = new HashSet<>();
        Set<TravelFront> visitedFronts = new HashSet<>();
        ArrayDeque<TravelFront> pendingFronts = new ArrayDeque<>();
        int maxDistance = Math.max(1, radius * 2);

        for (OpticalNodeRecord node : candidates.values()) {
            if (!node.flags().possibleSource()) {
                continue;
            }

            reachableNodes.add(node.pos());
            seedSourceFronts(level, node.pos(), pendingFronts);
        }

        while (!pendingFronts.isEmpty()) {
            TravelFront front = pendingFronts.removeFirst();

            if (!visitedFronts.add(front)) {
                continue;
            }

            EdgeScan edgeScan = scanEdge(level, candidates, front, maxDistance);

            if (edgeScan == null) {
                continue;
            }

            reachableNodes.addAll(edgeScan.reachableNodes());

            for (PotentialEdge edge : edgeScan.edges()) {
                edges.add(edge);

                if (!edgeScan.scatterMarkersByEdge().isEmpty()
                        && edgeScan.scatterMarkersByEdge().containsKey(edge)) {
                    scatterMarkersByEdge.put(edge, edgeScan.scatterMarkersByEdge().get(edge));
                }
            }

            PotentialEdge terminalEdge = edgeScan.terminalEdge();

            if (terminalEdge == null) {
                continue;
            }

            Direction incomingDirection = terminalEdge.direction().getOpposite();

            for (Direction outgoingDirection : outgoingDirections(level, terminalEdge.to(), incomingDirection)) {
                pendingFronts.addLast(new TravelFront(terminalEdge.to(), outgoingDirection));
            }
        }

        return new ScanResult(Set.copyOf(reachableNodes), List.copyOf(edges), Map.copyOf(scatterMarkersByEdge));
    }

    private static void seedSourceFronts(ServerLevel level, BlockPos pos, ArrayDeque<TravelFront> pendingFronts) {
        BlockState state = level.getBlockState(pos);

        if (!IntrinsicOpticalSources.isSource(state)) {
            return;
        }

        for (OutputBeam outputBeam : IntrinsicOpticalSources.outputBeams(state, level, pos)) {
            pendingFronts.addLast(new TravelFront(pos, outputBeam.outgoingDirection()));
        }
    }

    private static EdgeScan scanEdge(
            ServerLevel level,
            Map<BlockPos, OpticalNodeRecord> candidates,
            TravelFront front,
            int maxDistance
    ) {
        List<PotentialEdge> edges = new ArrayList<>();
        Map<PotentialEdge, Set<BlockPos>> scatterMarkersByEdge = new HashMap<>();
        Set<BlockPos> reachableNodes = new HashSet<>();
        List<BlockPos> edgeScatterMarkers = new ArrayList<>();
        BlockPos edgeStart = front.pos();
        BlockPos cursor = front.pos().relative(front.direction());

        reachableNodes.add(front.pos());

        for (int distance = 1; distance <= maxDistance; distance++) {
            if (candidates.containsKey(cursor)) {
                PotentialEdge edge = new PotentialEdge(
                        edgeStart,
                        cursor.immutable(),
                        front.direction(),
                        Math.max(1, manhattanDistance(edgeStart, cursor))
                );
                edges.add(edge);
                reachableNodes.add(cursor.immutable());

                if (!edgeScatterMarkers.isEmpty()) {
                    scatterMarkersByEdge.put(edge, Set.copyOf(edgeScatterMarkers));
                }

                return new EdgeScan(reachableNodes, edges, scatterMarkersByEdge, edge);
            }

            if (!level.isLoaded(cursor)) {
                cursor = cursor.relative(front.direction());
                continue;
            }

            BlockState state = level.getBlockState(cursor);

            if (!OpticalMaterialProfiles.isAirLike(state)) {
                if (OpticalFieldSources.isScatteringFieldSource(state)) {
                    PotentialEdge edge = new PotentialEdge(
                            edgeStart,
                            cursor.immutable(),
                            front.direction(),
                            Math.max(1, manhattanDistance(edgeStart, cursor))
                    );
                    edges.add(edge);
                    reachableNodes.add(cursor.immutable());

                    if (!edgeScatterMarkers.isEmpty()) {
                        scatterMarkersByEdge.put(edge, Set.copyOf(edgeScatterMarkers));
                    }

                    return new EdgeScan(reachableNodes, edges, scatterMarkersByEdge, edge);
                }

                return edges.isEmpty() ? null : new EdgeScan(reachableNodes, edges, scatterMarkersByEdge, null);
            }

            if (state.isAir() && OpticalFieldSources.hasEffect(level, cursor, OpticalFieldEffectType.SCATTERING)) {
                BlockPos affectedAirPos = cursor.immutable();
                PotentialEdge edge = new PotentialEdge(
                        edgeStart,
                        affectedAirPos,
                        front.direction(),
                        Math.max(1, manhattanDistance(edgeStart, affectedAirPos))
                );
                edges.add(edge);
                reachableNodes.add(affectedAirPos);
                edgeScatterMarkers.add(affectedAirPos);
                scatterMarkersByEdge.put(edge, Set.of(affectedAirPos));
                edgeStart = affectedAirPos;
                edgeScatterMarkers = new ArrayList<>();
            }

            cursor = cursor.relative(front.direction());
        }

        return edges.isEmpty() ? null : new EdgeScan(reachableNodes, edges, scatterMarkersByEdge, null);
    }

    private static Set<Direction> outgoingDirections(ServerLevel level, BlockPos pos, Direction incomingDirection) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof OpticalTopologyProvider topologyProvider) {
            return topologyProvider.potentialOutgoingDirections(state, level, pos, incomingDirection);
        }

        return OpticalNetworkCompiler.compile(level, pos, state)
                .outputDirections(TOPOLOGY_TEST_BEAM.withDirection(incomingDirection.getOpposite()), incomingDirection);
    }

    private static Map<BlockPos, Set<BlockPos>> buildAdjacency(Set<BlockPos> nodes, List<PotentialEdge> edges) {
        Map<BlockPos, Set<BlockPos>> adjacency = new HashMap<>();

        for (BlockPos node : nodes) {
            adjacency.put(node, new HashSet<>());
        }

        for (PotentialEdge edge : edges) {
            adjacency.get(edge.from()).add(edge.to());
            adjacency.get(edge.to()).add(edge.from());
        }

        return adjacency;
    }

    private static OpticalNetworkSnapshot buildSnapshot(
            Map<BlockPos, OpticalNodeRecord> candidates,
            ScanResult scanResult,
            Map<BlockPos, Set<BlockPos>> adjacency
    ) {
        List<OpticalNetworkRecord> networks = new ArrayList<>();
        Set<BlockPos> overlayPositions = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        int networkId = 0;

        for (BlockPos start : candidates.keySet()) {
            if (!adjacency.containsKey(start)) {
                continue;
            }

            if (!visited.add(start)) {
                continue;
            }

            Set<BlockPos> component = collectComponent(start, adjacency, visited);
            int possibleSourceCount = countPossibleSources(component, candidates);

            if (possibleSourceCount == 0) {
                continue;
            }

            List<PotentialEdge> componentEdges = edgesInside(component, scanResult.edges());
            networks.add(new OpticalNetworkRecord(networkId++, component, componentEdges, possibleSourceCount, true));
            overlayPositions.addAll(component);

            for (PotentialEdge edge : componentEdges) {
                Set<BlockPos> scatterMarkers = scanResult.scatterMarkersByEdge().get(edge);

                if (scatterMarkers != null) {
                    overlayPositions.addAll(scatterMarkers);
                }
            }
        }

        return new OpticalNetworkSnapshot(networks, overlayPositions, false);
    }

    private static Set<BlockPos> collectComponent(
            BlockPos start,
            Map<BlockPos, Set<BlockPos>> adjacency,
            Set<BlockPos> visited
    ) {
        Set<BlockPos> component = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        component.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();

            for (BlockPos next : adjacency.getOrDefault(current, Set.of())) {
                if (visited.add(next)) {
                    component.add(next);
                    queue.addLast(next);
                }
            }
        }

        return component;
    }

    private static int countPossibleSources(Set<BlockPos> component, Map<BlockPos, OpticalNodeRecord> candidates) {
        int count = 0;

        for (BlockPos pos : component) {
            OpticalNodeRecord node = candidates.get(pos);

            if (node != null && node.flags().possibleSource()) {
                count++;
            }
        }

        return count;
    }

    private static List<PotentialEdge> edgesInside(Set<BlockPos> component, List<PotentialEdge> edges) {
        List<PotentialEdge> componentEdges = new ArrayList<>();

        for (PotentialEdge edge : edges) {
            if (component.contains(edge.from()) && component.contains(edge.to())) {
                componentEdges.add(edge);
            }
        }

        return componentEdges;
    }

    private static int clampRadius(int radius) {
        return Math.max(MIN_REBUILD_RADIUS, Math.min(MAX_REBUILD_RADIUS, radius));
    }

    private static int manhattanDistance(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getY() - to.getY())
                + Math.abs(from.getZ() - to.getZ());
    }

    private record ScanResult(
            Set<BlockPos> reachableNodes,
            List<PotentialEdge> edges,
            Map<PotentialEdge, Set<BlockPos>> scatterMarkersByEdge
    ) {
    }

    private record TravelFront(BlockPos pos, Direction direction) {
        private TravelFront {
            pos = pos.immutable();
        }
    }

    private record EdgeScan(
            Set<BlockPos> reachableNodes,
            List<PotentialEdge> edges,
            Map<PotentialEdge, Set<BlockPos>> scatterMarkersByEdge,
            PotentialEdge terminalEdge
    ) {
    }

    private OpticalNetworkIndex() {
    }
}
