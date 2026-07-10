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
        SpotProjectionResult.Stats stats = projectionResult.stats();
        SpotProjectionResult.StageTimings timings = stats.timings();
        SpotProjectionResult.SubtractionStats subtraction = stats.subtraction();
        SpotProjectionResult.RemainingStats remaining = stats.remaining();
        SpotProjectionResult.HotDepth hotDepth = stats.hotDepth();
        SpectralDiagnostics.WriteStats logWriteStats = SpectralDiagnostics.writeStats();
        SpectralDiagnostics.event(level, "spot_projection", "profile")
                .pos("source", node.pos())
                .field("direction", node.side())
                .field("power", outgoingPower)
                .field("plane_count", VoxelSpotProjector.occlusionPlaneCount())
                .field("elapsed_us", elapsedNanos / 1_000.0D)
                .field("spots", projectionResult.spots().size())
                .field("allocations", projectionResult.allocations().size())
                .field("dependencies", projectionResult.dependencies().size())
                .field("depths", stats.depths())
                .field("tiles_scanned", stats.scannedTiles())
                .field("candidate_tiles", stats.candidateTiles())
                .field("loaded_tiles", stats.loadedTiles())
                .field("air_tiles", stats.airTiles())
                .field("non_projectable_tiles", stats.nonProjectableTiles())
                .field("projectable_tiles", stats.projectableTiles())
                .field("side_tiles_scanned", stats.sideTilesScanned())
                .field("side_loaded_tiles", stats.sideLoadedTiles())
                .field("side_projectable_tiles", stats.sideProjectableTiles())
                .field("side_open_checks", stats.sideOpenChecks())
                .field("side_open_faces", stats.sideOpenFaces())
                .field("side_travel_intervals", stats.sideTravelIntervals())
                .field("side_window_candidates", stats.sideWindowCandidates())
                .field("side_fast_path_patches", stats.sideFastPathPatches())
                .field("side_fast_path_skipped", stats.sideFastPathSkipped())
                .field("side_range_culled_tiles", stats.sideRangeCulledTiles())
                .field("depth_tile_cache_hits", stats.depthTileCacheHits())
                .field("depth_tile_cache_misses", stats.depthTileCacheMisses())
                .field("side_boundary_face_tests", stats.sideBoundaryFaceTests())
                .field("side_boundary_projectable_faces", stats.sideBoundaryProjectableFaces())
                .field("side_boundary_open_faces", stats.sideBoundaryOpenFaces())
                .field("side_boundary_travel_intervals", stats.sideBoundaryTravelIntervals())
                .field("side_boundary_travel_faces", stats.sideBoundaryTravelFaces())
                .field("side_legacy_candidate_faces", stats.sideLegacyCandidateFaces())
                .field("side_boundary_candidate_faces", stats.sideBoundaryCandidateFaces())
                .field("side_boundary_missing_faces", stats.sideBoundaryMissingFaces())
                .field("side_boundary_extra_faces", stats.sideBoundaryExtraFaces())
                .field("plane_window_tests", stats.planeWindowTests())
                .field("plane_window_candidates", stats.planeWindowCandidates())
                .field("plane_window_remaining_culled", stats.planeWindowRemainingCulled())
                .field("plane_windows", stats.planeWindows())
                .field("plane_windows_effective", stats.planeWindows())
                .field("front_visible_queries", stats.frontSubtractions())
                .field("side_visible_queries", stats.sideSubtractions())
                .field("max_visible_fragments", stats.maxVisibleFragments())
                .field("front_fragments_before_merge", stats.frontFragmentsBeforeMerge())
                .field("front_fragments_after_merge", stats.frontFragmentsAfterMerge())
                .field("tile_range_us", timings.tileRangeNanos() / 1_000.0D)
                .field("projection_rect_us", timings.projectionRectNanos() / 1_000.0D)
                .field("block_lookup_us", timings.blockLookupNanos() / 1_000.0D)
                .field("projectable_check_us", timings.projectableCheckNanos() / 1_000.0D)
                .field("plane_window_us", timings.planeWindowNanos() / 1_000.0D)
                .field("front_intersect_us", timings.frontSubtractNanos() / 1_000.0D)
                .field("side_scan_us", timings.sideScanNanos() / 1_000.0D)
                .field("side_candidate_us", timings.sideCandidateNanos() / 1_000.0D)
                .field("side_emit_us", timings.sideEmitNanos() / 1_000.0D)
                .field("side_travel_split_us", timings.sideTravelSplitNanos() / 1_000.0D)
                .field("side_window_us", timings.sideWindowNanos() / 1_000.0D)
                .field("side_remaining_intersect_us", timings.sideRemainingIntersectNanos() / 1_000.0D)
                .field("side_patch_emit_us", timings.sidePatchEmitNanos() / 1_000.0D)
                .field("side_candidate_verify_us", timings.sideCandidateVerifyNanos() / 1_000.0D)
                .field("side_intersect_us", timings.sideSubtractNanos() / 1_000.0D)
                .field("front_emit_us", timings.frontEmitNanos() / 1_000.0D)
                .field("remaining_update_us", timings.fullOccupancyNanos() / 1_000.0D)
                .field("remaining_blocker_apply_us", timings.remainingUnionNanos() / 1_000.0D)
                .field("remaining_subtract_us", timings.remainingSubtractNanos() / 1_000.0D)
                .field("debug_occlusion_store_us", timings.occlusionAddNanos() / 1_000.0D)
                .field("subtract_intersecting_windows", subtraction.intersectingWindows())
                .field("subtract_max_intersecting_windows", subtraction.maxIntersectingWindows())
                .field("subtract_apply_steps", subtraction.applySteps())
                .field("subtract_split_steps", subtraction.splitSteps())
                .field("subtract_empty_results", subtraction.emptyResults())
                .field("remaining_slabs", remaining.slabs())
                .field("remaining_max_slabs", remaining.maxSlabs())
                .field("remaining_intervals", remaining.intervals())
                .field("remaining_max_intervals", remaining.maxIntervals())
                .field("remaining_area", remaining.area())
                .field("remaining_min_area", remaining.minArea())
                .field("remaining_intersection_queries", remaining.intersectionQueries())
                .field("remaining_intersection_slab_tests", remaining.intersectionSlabTests())
                .field("remaining_intersection_interval_tests", remaining.intersectionIntervalTests())
                .field("remaining_visible_fragments", remaining.visibleFragments())
                .field("remaining_prefilter_queries", remaining.prefilterQueries())
                .field("remaining_prefilter_hits", remaining.prefilterHits())
                .field("remaining_prefilter_slab_tests", remaining.prefilterSlabTests())
                .field("remaining_prefilter_interval_tests", remaining.prefilterIntervalTests())
                .field("remaining_union_input_rects", remaining.unionInputRects())
                .field("remaining_union_merged_rects", remaining.unionMergedRects())
                .field("remaining_blocker_tests", remaining.blockerTests())
                .field("remaining_blocker_hits", remaining.blockerHits())
                .field("remaining_clipped_blockers", remaining.clippedBlockers())
                .field("remaining_blocker_slab_steps", remaining.blockerSlabSteps())
                .field("remaining_interval_clip_tests", remaining.intervalClipTests())
                .field("remaining_apply_steps", remaining.applySteps())
                .field("remaining_split_steps", remaining.splitSteps())
                .field("remaining_empty_results", remaining.emptyResults())
                .field("hot_depth", hotDepth.depth())
                .field("hot_depth_elapsed_us", hotDepth.elapsedNanos() / 1_000.0D)
                .field("hot_depth_tiles_scanned", hotDepth.scannedTiles())
                .field("hot_depth_candidate_tiles", hotDepth.candidateTiles())
                .field("hot_depth_projectable_tiles", hotDepth.projectableTiles())
                .field("hot_depth_plane_windows", hotDepth.planeWindows())
                .field("hot_depth_front_visible_queries", hotDepth.frontSubtractions())
                .field("hot_depth_side_visible_queries", hotDepth.sideSubtractions())
                .field("hot_depth_spots", hotDepth.spots())
                .field("log_write_count_before", logWriteStats.count())
                .field("log_write_bytes_before", logWriteStats.bytes())
                .field("log_write_avg_us_before", logWriteStats.averageNanos() / 1_000.0D)
                .field("log_write_max_us_before", logWriteStats.maxNanos() / 1_000.0D)
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
