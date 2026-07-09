package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.block.MirrorBlock;
import io.github.yoglappland.spectralization.blockentity.LensHolderBlockEntity;
import io.github.yoglappland.spectralization.config.SpectralizationConfig;
import io.github.yoglappland.spectralization.diagnostics.SpectralDiagnostics;
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
import net.minecraft.world.level.block.state.BlockState;

public final class CompiledSpotLayer {
    private static final double MIN_SPOT_POWER = 1.0E-4;
    private static final double PROJECTED_OPTICAL_SOURCE_MAX_RADIUS = 0.5D;
    private static final int MAX_SPOTS_PER_SAMPLE = 1024;
    public static final SpotLayer EMPTY = new SpotLayer(List.of(), new LongOpenHashSet(), List.of());

    public static SpotLayer sample(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            OutputBeam sourceOutput,
            CompiledBeamProfileLayer beamProfileLayer
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

            if (isTransparentProjectionPassThrough(level, node)) {
                continue;
            }

            double coherentOutgoingPower = Math.min(outgoingPower, solution.coherentPowerAt(node));
            BeamPacket profileTemplate = templateAt(
                    level,
                    sourceOutput,
                    distanceByNode.getOrDefault(node, 0),
                    solution,
                    beamProfileLayer,
                    node
            )
                    .withDirection(node.side());
            long projectionStartNanos = SpectralizationConfig.opticalCompilerDebugLog() ? System.nanoTime() : 0L;
            SpotProjectionResult projectionResult = VoxelSpotProjector.projectLightConeSpots(
                    level,
                    node.pos(),
                    node.side(),
                    node.side(),
                    profileTemplate,
                    outgoingPower,
                    coherentOutgoingPower
            );
            logProjectionProfile(level, node, outgoingPower, projectionResult, projectionStartNanos);

            if (addVisibleSpots(primarySpots, sideSpots, allocations, projectionResult, projectionDependencies)) {
                break;
            }
        }

        return new SpotLayer(cappedSpots(primarySpots, sideSpots), projectionDependencies, allocations);
    }

    private static void logProjectionProfile(
            ServerLevel level,
            PortGraphNode node,
            double outgoingPower,
            SpotProjectionResult projectionResult,
            long projectionStartNanos
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog() || projectionStartNanos <= 0L) {
            return;
        }

        long elapsedNanos = System.nanoTime() - projectionStartNanos;
        SpectralDiagnostics.event(level, "spot_projection", "profile")
                .pos("source", node.pos())
                .field("direction", node.side())
                .field("power", outgoingPower)
                .field("plane_count", VoxelSpotProjector.occlusionPlaneCount())
                .field("elapsed_us", elapsedNanos / 1_000.0D)
                .field("spots", projectionResult.spots().size())
                .field("allocations", projectionResult.allocations().size())
                .field("dependencies", projectionResult.dependencies().size())
                .write();
    }

    private static boolean isTransparentProjectionPassThrough(ServerLevel level, PortGraphNode node) {
        return level.getBlockEntity(node.pos()) instanceof LensHolderBlockEntity lensHolder && !lensHolder.hasLens();
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
                assignDebugMarkers(spots);
                return spots;
            }

            spots.add(spot);
        }

        for (SpotRecord spot : sideSpots) {
            if (spots.size() >= MAX_SPOTS_PER_SAMPLE) {
                assignDebugMarkers(spots);
                return spots;
            }

            spots.add(spot);
        }

        assignDebugMarkers(spots);
        return spots;
    }

    private static void assignDebugMarkers(List<SpotRecord> spots) {
        DebugMarkerAllocator markerAllocator = null;

        for (int index = 0; index < spots.size(); index++) {
            SpotRecord spot = spots.get(index);

            if (spot.projectionMode() != SpotRecord.ProjectionMode.DEBUG_FACE_CENTER) {
                continue;
            }

            if (markerAllocator == null) {
                markerAllocator = new DebugMarkerAllocator();
            }

            spots.set(index, spot.withDebugMarker(markerAllocator.marker(spot.pos(), spot.face())));
        }
    }

    private static int initialDebugMarker(BlockPos pos, net.minecraft.core.Direction face) {
        long positionKey = pos.asLong();
        int hash = (int) (positionKey ^ (positionKey >>> 32));
        hash = 31 * hash + face.ordinal();
        hash ^= hash >>> 16;
        hash *= 0x7FEB352D;
        hash ^= hash >>> 15;
        hash *= 0x846CA68B;
        hash ^= hash >>> 16;
        return hash & 0xFFF;
    }

    private static final class DebugMarkerAllocator {
        private static final int MARKER_COUNT = 0x1000;
        private final Map<DebugFaceKey, Integer> markersByFace = new HashMap<>();
        private final Map<Integer, DebugFaceKey> facesByMarker = new HashMap<>();

        private int marker(BlockPos pos, net.minecraft.core.Direction face) {
            DebugFaceKey key = new DebugFaceKey(pos.immutable(), face);
            Integer existing = markersByFace.get(key);

            if (existing != null) {
                return existing;
            }

            int start = initialDebugMarker(pos, face);

            for (int probe = 0; probe < MARKER_COUNT; probe++) {
                int candidate = (start + probe) & 0xFFF;
                DebugFaceKey owner = facesByMarker.get(candidate);

                if (owner == null || owner.equals(key)) {
                    facesByMarker.put(candidate, key);
                    markersByFace.put(key, candidate);
                    return candidate;
                }
            }

            markersByFace.put(key, -1);
            return -1;
        }
    }

    private record DebugFaceKey(BlockPos pos, net.minecraft.core.Direction face) {
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
            ServerLevel level,
            OutputBeam sourceOutput,
            int distance,
            ScalarPowerSolution solution,
            CompiledBeamProfileLayer beamProfileLayer,
            PortGraphNode node
    ) {
        BeamEnvelope fallbackEnvelope = BeamGeometryOps.propagate(
                sourceOutput.beam().envelope(),
                Math.max(0, distance)
        );
        BeamEnvelope solvedEnvelope = beamProfileLayer != null && !beamProfileLayer.envelopesByLane().isEmpty()
                ? beamProfileLayer.envelopeAt(node, solution)
                : null;
        BeamEnvelope envelope = projectionEnvelopeAtNode(
                level,
                node,
                solvedEnvelope == null ? dominantProfileEnvelope(solution, node, fallbackEnvelope) : solvedEnvelope
        );
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

    private static BeamEnvelope projectionEnvelopeAtNode(ServerLevel level, PortGraphNode node, BeamEnvelope envelope) {
        if (!level.isLoaded(node.pos())) {
            return envelope;
        }

        BlockState state = level.getBlockState(node.pos());
        boolean boundedProjectionSource = state.getBlock() instanceof MirrorBlock
                || level.getBlockEntity(node.pos()) instanceof LensHolderBlockEntity lensHolder && lensHolder.hasLens();

        if (!boundedProjectionSource || envelope.radius() <= PROJECTED_OPTICAL_SOURCE_MAX_RADIUS) {
            return envelope;
        }

        return envelope.withRadius(PROJECTED_OPTICAL_SOURCE_MAX_RADIUS);
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
