package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamPacket;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import io.github.yoglappland.spectralization.optics.OutputBeam;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import io.github.yoglappland.spectralization.optics.OpticalSpotTracker;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class CompiledSpotLayer {
    private static final double MIN_SPOT_POWER = 1.0E-4;
    private static final int MAX_SPOTS_PER_SAMPLE = 768;

    public static List<SpotRecord> sample(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            OutputBeam sourceOutput
    ) {
        if (!solution.reliableForReadout() || graph.nodes().isEmpty()) {
            return List.of();
        }

        Map<PortGraphNode, List<PortGraphEdge>> localEdgesByInput = localEdgesByInput(graph);
        Map<PortGraphNode, Integer> distanceByNode = propagationDistanceByNode(graph);
        List<SpotRecord> spots = new ArrayList<>();

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.INCOMING) {
                continue;
            }

            double incomingPower = solution.powerAt(node);

            if (incomingPower <= MIN_SPOT_POWER) {
                continue;
            }

            if (!level.isLoaded(node.pos())) {
                continue;
            }

            BlockState state = level.getBlockState(node.pos());

            if (OpticalMaterialProfiles.isAirLike(state) || !state.isCollisionShapeFullBlock(level, node.pos())) {
                continue;
            }

            double coherentIncomingPower = Math.min(incomingPower, solution.coherentPowerAt(node));
            BeamPacket profileTemplate = templateAt(sourceOutput, distanceByNode.getOrDefault(node, 0));
            List<PortGraphEdge> localEdges = localEdgesByInput.getOrDefault(node, List.of());
            SpotRecord incidentSpot = OpticalSpotTracker.createCompiledSurfaceSpot(
                    level,
                    node.pos(),
                    node.side(),
                    state,
                    profileTemplate,
                    incomingPower,
                    coherentIncomingPower
            );

            if (addVisibleSpot(spots, incidentSpot)) {
                return List.copyOf(spots);
            }

            for (PortGraphEdge edge : localEdges) {
                if (edge.to().side() == node.side()) {
                    continue;
                }

                double transmittedPower = incomingPower * edge.sampleGain();

                if (transmittedPower <= MIN_SPOT_POWER) {
                    continue;
                }

                SpotRecord exitSpot = OpticalSpotTracker.createCompiledSurfaceSpot(
                        level,
                        node.pos(),
                        edge.to().side(),
                        state,
                        profileTemplate,
                        transmittedPower,
                        scaledCoherentPower(transmittedPower, incomingPower, coherentIncomingPower)
                );

                if (addVisibleSpot(spots, exitSpot)) {
                    return List.copyOf(spots);
                }
            }
        }

        return List.copyOf(spots);
    }

    private static Map<PortGraphNode, List<PortGraphEdge>> localEdgesByInput(CompiledPortGraph graph) {
        Map<PortGraphNode, List<PortGraphEdge>> localEdgesByInput = new HashMap<>();

        for (PortGraphEdge edge : graph.edges()) {
            if (edge.kind() != PortGraphEdgeKind.LOCAL_SCATTERING) {
                continue;
            }

            localEdgesByInput.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
        }

        return localEdgesByInput;
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

    private static boolean addVisibleSpot(List<SpotRecord> spots, SpotRecord spot) {
        if (!spot.visible()) {
            return false;
        }

        spots.add(spot);
        return spots.size() >= MAX_SPOTS_PER_SAMPLE;
    }

    private static BeamPacket templateAt(OutputBeam sourceOutput, int distance) {
        BeamEnvelope envelope = BeamGeometryOps.propagate(
                sourceOutput.beam().envelope(),
                Math.max(0, distance)
        );

        return sourceOutput.beam()
                .withDirection(sourceOutput.outgoingDirection())
                .withEnvelope(envelope);
    }

    private static double scaledCoherentPower(double targetPower, double sourceTotalPower, double sourceCoherentPower) {
        if (targetPower <= 0.0 || sourceTotalPower <= 0.0 || sourceCoherentPower <= 0.0) {
            return 0.0;
        }

        return targetPower * Math.min(1.0, sourceCoherentPower / sourceTotalPower);
    }

    private record NodeDistance(PortGraphNode node, int distance) {
    }

    private CompiledSpotLayer() {
    }
}
