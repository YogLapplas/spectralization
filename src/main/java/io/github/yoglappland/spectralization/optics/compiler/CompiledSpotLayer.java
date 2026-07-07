package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.PlaneWaveComponent;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionAllocation;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionResult;
import io.github.yoglappland.spectralization.optics.projection.VoxelSpotProjector;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class CompiledSpotLayer {
    private static final double MIN_SPOT_POWER = 1.0E-4;
    private static final int MAX_SPOTS_PER_SAMPLE = 1024;
    public static final SpotLayer EMPTY = new SpotLayer(List.of(), new LongOpenHashSet(), List.of());

    public static SpotLayer sample(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            OutputBeam sourceOutput
    ) {
        if (!solution.reliableForReadout() || graph.nodes().isEmpty()) {
            return EMPTY;
        }

        Map<PortGraphNode, Integer> distanceByNode = propagationDistanceByNode(graph);
        List<SpotRecord> primarySpots = new ArrayList<>();
        List<SpotRecord> sideSpots = new ArrayList<>();
        List<SpotProjectionAllocation> allocations = new ArrayList<>();
        LongSet projectionDependencies = new LongOpenHashSet();

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.OUTGOING) {
                continue;
            }

            double outgoingPower = solution.powerAt(node);

            if (outgoingPower <= MIN_SPOT_POWER) {
                continue;
            }

            if (!level.isLoaded(node.pos())) {
                continue;
            }

            double coherentOutgoingPower = Math.min(outgoingPower, solution.coherentPowerAt(node));
            BeamPacket profileTemplate = templateAt(sourceOutput, distanceByNode.getOrDefault(node, 0), solution, node)
                    .withDirection(node.side());
            if (addVisibleSpots(primarySpots, sideSpots, allocations, VoxelSpotProjector.projectLightConeSpots(
                    level,
                    node.pos(),
                    node.side(),
                    node.side(),
                    profileTemplate,
                    outgoingPower,
                    coherentOutgoingPower
            ), projectionDependencies)) {
                break;
            }
        }

        return new SpotLayer(cappedSpots(primarySpots, sideSpots), projectionDependencies, allocations);
    }

    private static Map<PortGraphNode, Integer> propagationDistanceByNode(CompiledPortGraph graph) {
        Map<PortGraphNode, List<PortGraphEdge>> edgesByFrom = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            edgesByFrom.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        Map<PortGraphNode, Integer> distanceByNode = new HashMap<>();
        PriorityQueue<NodeDistance> queue = new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        distanceByNode.put(graph.sourceNode(), 0);
        queue.add(new NodeDistance(graph.sourceNode(), 0));

        while (!queue.isEmpty()) {
            NodeDistance current = queue.poll();

            if (current.distance() != distanceByNode.getOrDefault(current.node(), Integer.MAX_VALUE)) {
                continue;
            }

            for (PortGraphEdge edge : edgesByFrom.getOrDefault(current.node(), List.of())) {
                int distance = current.distance() + Math.max(0, edge.distance());
                int previousDistance = distanceByNode.getOrDefault(edge.to(), Integer.MAX_VALUE);

                if (distance < previousDistance) {
                    distanceByNode.put(edge.to(), distance);
                    queue.add(new NodeDistance(edge.to(), distance));
                }
            }
        }

        return distanceByNode;
    }

    private static boolean addVisibleSpot(List<SpotRecord> primarySpots, List<SpotRecord> sideSpots, SpotRecord spot) {
        if (!spot.visible()) {
            return false;
        }

        if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_QUAD
                || spot.projectionMode() == SpotRecord.ProjectionMode.DEBUG_FACE_CENTER) {
            if (sideSpots.size() < MAX_SPOTS_PER_SAMPLE) {
                sideSpots.add(spot);
            }
            return false;
        }

        primarySpots.add(spot);
        return primarySpots.size() >= MAX_SPOTS_PER_SAMPLE;
    }

    private static boolean addVisibleSpots(
            List<SpotRecord> primarySpots,
            List<SpotRecord> sideSpots,
            List<SpotProjectionAllocation> allocations,
            SpotProjectionResult projectionResult,
            LongSet projectionDependencies
    ) {
        projectionDependencies.addAll(projectionResult.dependencies());
        allocations.addAll(projectionResult.allocations());

        for (SpotRecord spot : projectionResult.spots()) {
            if (addVisibleSpot(primarySpots, sideSpots, spot)) {
                return true;
            }
        }

        return false;
    }

    private static List<SpotRecord> cappedSpots(List<SpotRecord> primarySpots, List<SpotRecord> sideSpots) {
        List<SpotRecord> spots = new ArrayList<>(Math.min(MAX_SPOTS_PER_SAMPLE, primarySpots.size() + sideSpots.size()));

        for (SpotRecord spot : primarySpots) {
            if (spots.size() >= MAX_SPOTS_PER_SAMPLE) {
                return spots;
            }

            spots.add(spot);
        }

        for (SpotRecord spot : sideSpots) {
            if (spots.size() >= MAX_SPOTS_PER_SAMPLE) {
                return spots;
            }

            spots.add(spot);
        }

        return spots;
    }

    public record SpotLayer(
            List<SpotRecord> spots,
            LongSet projectionDependencies,
            List<SpotProjectionAllocation> allocations
    ) {
        public SpotLayer {
            spots = List.copyOf(spots);
            projectionDependencies = new LongOpenHashSet(projectionDependencies);
            allocations = List.copyOf(allocations);
        }
    }

    private static BeamPacket templateAt(
            OutputBeam sourceOutput,
            int distance,
            ScalarPowerSolution solution,
            PortGraphNode node
    ) {
        BeamEnvelope fallbackEnvelope = BeamGeometryOps.propagate(
                sourceOutput.beam().envelope(),
                Math.max(0, distance)
        );
        BeamEnvelope envelope = dominantProfileEnvelope(solution, node, fallbackEnvelope);
        List<PlaneWaveComponent> components = solution.powerByLane().entrySet().stream()
                .map(entry -> {
                    double power = entry.getValue().getOrDefault(node, 0.0);
                    if (power <= 0.0) {
                        return null;
                    }

                    return new PlaneWaveComponent(entry.getKey().frequency(), power, node.side(), entry.getKey().coherence());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(PlaneWaveComponent::power).reversed())
                .limit(BeamPacket.MAX_COMPONENTS)
                .toList();

        if (!components.isEmpty()) {
            return new BeamPacket(components, envelope);
        }

        return sourceOutput.beam()
                .withDirection(sourceOutput.outgoingDirection())
                .withEnvelope(envelope);
    }

    private static BeamEnvelope dominantProfileEnvelope(
            ScalarPowerSolution solution,
            PortGraphNode node,
            BeamEnvelope fallback
    ) {
        double strongestPower = 0.0D;
        BeamEnvelope strongestEnvelope = fallback;

        for (Map.Entry<SpectralPowerLane, Map<PortGraphNode, Double>> entry : solution.powerByLane().entrySet()) {
            double power = entry.getValue().getOrDefault(node, 0.0D);

            if (power > strongestPower) {
                strongestPower = power;
                strongestEnvelope = entry.getKey().profile().toEnvelope();
            }
        }

        return strongestEnvelope;
    }

    private record NodeDistance(PortGraphNode node, int distance) {
    }

    private CompiledSpotLayer() {
    }
}
