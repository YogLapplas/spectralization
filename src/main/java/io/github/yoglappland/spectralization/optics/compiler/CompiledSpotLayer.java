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
import io.github.yoglappland.spectralization.optics.SpotRecord.GeometryKey;
import io.github.yoglappland.spectralization.optics.SpotProjectionLimits;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionAllocation;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPerformanceTracker;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionResult;
import io.github.yoglappland.spectralization.optics.projection.VoxelSpotProjector;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class CompiledSpotLayer {
    private static final double MIN_SPOT_POWER = 1.0E-4;
    private static final double PROJECTED_OPTICAL_SOURCE_MAX_RADIUS = 0.5D;
    private static final int MAX_SPOTS_PER_SAMPLE = SpotProjectionLimits.MAX_SPOTS_PER_OWNER;
    private static final int MAX_GEOMETRY_CACHE_ENTRIES_PER_LEVEL = 64;
    private static final int MAX_GEOMETRY_TEMPLATES_PER_ENTRY = MAX_SPOTS_PER_SAMPLE;
    private static final int MAX_GEOMETRY_TEMPLATES_PER_LEVEL = MAX_SPOTS_PER_SAMPLE * 4;
    private static final int UNCACHED_NETWORK_ID = Integer.MIN_VALUE;
    private static final Map<ServerLevel, LinkedHashMap<ProjectionGeometryKey, SpotProjectionResult>>
            GEOMETRY_CACHE_BY_LEVEL = new WeakHashMap<>();
    public static final SpotLayer EMPTY = new SpotLayer(List.of(), new LongOpenHashSet(), List.of());

    public static SpotLayer sample(
            ServerLevel level,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            OutputBeam sourceOutput,
            CompiledBeamProfileLayer beamProfileLayer
    ) {
        return sample(level, UNCACHED_NETWORK_ID, graph, solution, sourceOutput, beamProfileLayer);
    }

    public static SpotLayer sample(
            ServerLevel level,
            int networkId,
            CompiledPortGraph graph,
            ScalarPowerSolution solution,
            OutputBeam sourceOutput,
            CompiledBeamProfileLayer beamProfileLayer
    ) {
        if (!solution.reliableForReadout() || graph.nodes().isEmpty()) {
            return EMPTY;
        }

        Map<PortGraphNode, Integer> distanceByNode = propagationDistanceByNode(graph);
        Map<GeometryKey, SpotRecord> primarySpots = new LinkedHashMap<>();
        Map<GeometryKey, SpotRecord> sideSpots = new LinkedHashMap<>();
        List<SpotProjectionAllocation> allocations = new ArrayList<>();
        LongSet projectionDependencies = new LongOpenHashSet();
        SpotBudgetStats budgetStats = new SpotBudgetStats();

        for (PortGraphNode node : graph.nodes()) {
            if (node.waveKind() != PortWaveKind.OUTGOING) {
                continue;
            }
            budgetStats.outgoingNodes++;

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
            long projectionStartNanos = System.nanoTime();
            budgetStats.projectedNodes++;
            SpotProjectionResult projectionResult = projectWithGeometryCache(
                    level,
                    networkId,
                    node,
                    profileTemplate,
                    outgoingPower,
                    coherentOutgoingPower,
                    budgetStats
            );
            long projectionElapsedNanos = Math.max(0L, System.nanoTime() - projectionStartNanos);
            SpotProjectionPerformanceTracker.record(
                    level,
                    node.pos(),
                    node.side(),
                    projectionElapsedNanos,
                    projectionResult
            );
            logProjectionProfile(level, node, outgoingPower, projectionResult, projectionElapsedNanos);

            addVisibleSpots(
                    primarySpots,
                    sideSpots,
                    allocations,
                    projectionResult,
                    projectionDependencies,
                    budgetStats,
                    node.side()
            );
        }

        List<SpotRecord> spots = cappedSpots(primarySpots, sideSpots);
        logSpotBudget(level, spots.size(), projectionDependencies.size(), budgetStats);
        return new SpotLayer(spots, projectionDependencies, allocations);
    }

    private static SpotProjectionResult projectWithGeometryCache(
            ServerLevel level,
            int networkId,
            PortGraphNode node,
            BeamPacket profileTemplate,
            double outgoingPower,
            double coherentOutgoingPower,
            SpotBudgetStats budgetStats
    ) {
        boolean cacheEligible = networkId != UNCACHED_NETWORK_ID
                && !VoxelSpotProjector.debugFaceCentersEnabled()
                && !VoxelSpotProjector.validationEnabledFor(level, node.pos(), node.side());
        ProjectionGeometryKey key = new ProjectionGeometryKey(
                networkId,
                node.pos().immutable(),
                node.side(),
                profileTemplate.envelope(),
                VoxelSpotProjector.occlusionPlaneCount()
        );

        if (cacheEligible) {
            SpotProjectionResult cached = geometryCache(level).get(key);
            if (cached != null) {
                budgetStats.geometryCacheHits++;
                budgetStats.appearanceOnlyUpdates++;
                return VoxelSpotProjector.reapplyCachedAppearance(
                        level,
                        node.pos(),
                        node.side(),
                        profileTemplate,
                        outgoingPower,
                        coherentOutgoingPower,
                        cached
                );
            }
            budgetStats.geometryCacheMisses++;
        }

        SpotProjectionResult rebuilt = VoxelSpotProjector.projectLightConeSpots(
                level,
                node.pos(),
                node.side(),
                node.side(),
                profileTemplate,
                outgoingPower,
                coherentOutgoingPower
        );
        budgetStats.fullGeometryRebuilds++;
        if (cacheEligible && rebuilt.cacheMode() == SpotProjectionResult.CacheMode.FULL_REBUILD) {
            putGeometryCache(level, key, rebuilt);
        }
        return rebuilt;
    }

    private static LinkedHashMap<ProjectionGeometryKey, SpotProjectionResult> geometryCache(ServerLevel level) {
        return GEOMETRY_CACHE_BY_LEVEL.computeIfAbsent(level, ignored -> new LinkedHashMap<>(16, 0.75F, true));
    }

    private static void putGeometryCache(
            ServerLevel level,
            ProjectionGeometryKey key,
            SpotProjectionResult result
    ) {
        if (result.geometryTemplates().size() > MAX_GEOMETRY_TEMPLATES_PER_ENTRY) {
            return;
        }
        LinkedHashMap<ProjectionGeometryKey, SpotProjectionResult> cache = geometryCache(level);
        cache.put(key, result);
        while (cache.size() > MAX_GEOMETRY_CACHE_ENTRIES_PER_LEVEL
                || geometryTemplateCount(cache) > MAX_GEOMETRY_TEMPLATES_PER_LEVEL) {
            ProjectionGeometryKey eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    private static int geometryTemplateCount(Map<ProjectionGeometryKey, SpotProjectionResult> cache) {
        int count = 0;
        for (SpotProjectionResult result : cache.values()) {
            count += result.geometryTemplates().size();
        }
        return count;
    }

    public static void invalidateProjectionGeometry(
            ServerLevel level,
            int networkId,
            BlockPos changedPos,
            String reason
    ) {
        LinkedHashMap<ProjectionGeometryKey, SpotProjectionResult> cache = GEOMETRY_CACHE_BY_LEVEL.get(level);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        int removed = 0;
        int earliestDepth = Integer.MAX_VALUE;
        var iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ProjectionGeometryKey, SpotProjectionResult> entry = iterator.next();
            ProjectionGeometryKey key = entry.getKey();
            if (key.networkId() != networkId) {
                continue;
            }
            if (changedPos != null && !entry.getValue().dependencies().contains(changedPos.asLong())) {
                continue;
            }
            if (changedPos != null) {
                earliestDepth = Math.min(earliestDepth, projectionDepth(key.sourcePos(), changedPos, key.direction()));
            }
            iterator.remove();
            removed++;
        }
        logGeometryInvalidation(level, networkId, changedPos, reason, removed, earliestDepth);
    }

    public static void invalidateProjectionGeometry(
            ServerLevel level,
            BlockPos sourcePos,
            Direction direction,
            String reason
    ) {
        LinkedHashMap<ProjectionGeometryKey, SpotProjectionResult> cache = GEOMETRY_CACHE_BY_LEVEL.get(level);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getKey().sourcePos().equals(sourcePos)
                && entry.getKey().direction() == direction);
        logGeometryInvalidation(
                level,
                UNCACHED_NETWORK_ID,
                sourcePos,
                reason,
                before - cache.size(),
                0
        );
    }

    private static void logGeometryInvalidation(
            ServerLevel level,
            int networkId,
            BlockPos changedPos,
            String reason,
            int removed,
            int earliestDepth
    ) {
        if (removed <= 0 || !SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }
        SpectralDiagnostics.Event event = SpectralDiagnostics.event(
                level, "spot_projection", "geometry_cache_invalidated"
        )
                .field("network_id", networkId)
                .field("reason", reason)
                .field("removed_entries", removed)
                .field("earliest_invalidated_depth", earliestDepth == Integer.MAX_VALUE ? -1 : earliestDepth);
        if (changedPos != null) {
            event.pos("changed", changedPos);
        }
        event.write();
    }

    private static int projectionDepth(BlockPos sourcePos, BlockPos targetPos, Direction direction) {
        int dx = targetPos.getX() - sourcePos.getX();
        int dy = targetPos.getY() - sourcePos.getY();
        int dz = targetPos.getZ() - sourcePos.getZ();
        return Math.max(0, dx * direction.getStepX() + dy * direction.getStepY() + dz * direction.getStepZ());
    }

    private static void logProjectionProfile(
            ServerLevel level,
            PortGraphNode node,
            double outgoingPower,
            SpotProjectionResult projectionResult,
            long projectionElapsedNanos
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        if (projectionResult.cacheMode() == SpotProjectionResult.CacheMode.APPEARANCE_ONLY) {
            SpotProjectionResult.AppearanceTimings appearance = projectionResult.appearanceTimings();
            long residualNanos = Math.max(0L, projectionElapsedNanos - appearance.totalNanos());
            SpectralDiagnostics.event(level, "spot_projection", "profile")
                    .pos("source", node.pos())
                    .field("direction", node.side())
                    .field("power", outgoingPower)
                    .field("cache_mode", "appearance_only")
                    .field("log_detail", "compact_appearance")
                    .field("geometry_templates", projectionResult.geometryTemplates().size())
                    .field("elapsed_us", projectionElapsedNanos / 1_000.0D)
                    .field("appearance_total_us", appearance.totalNanos() / 1_000.0D)
                    .field("appearance_prepare_us", appearance.prepareNanos() / 1_000.0D)
                    .field("appearance_surface_build_us", appearance.surfaceBuildNanos() / 1_000.0D)
                    .field("appearance_record_update_us", appearance.recordUpdateNanos() / 1_000.0D)
                    .field("appearance_residual_us", residualNanos / 1_000.0D)
                    .field("appearance_templates", appearance.templates())
                    .field("appearance_unique_surfaces", appearance.uniqueSurfaces())
                    .field("appearance_surface_builds", appearance.surfaceBuilds())
                    .field("appearance_surface_cache_hits", appearance.surfaceCacheHits())
                    .field("appearance_plan_reused", appearance.planReused())
                    .field("dependency_snapshot_reused", true)
                    .field("spots", projectionResult.spots().size())
                    .field("dependencies", projectionResult.dependencies().size())
                    .write();
            return;
        }

        SpotProjectionResult.Stats stats = projectionResult.stats();
        SpotProjectionResult.StageTimings timings = stats.timings();
        SpotProjectionResult.SideDiagnostics sideDiagnostics = stats.sideDiagnostics();
        SpotProjectionResult.SubtractionStats subtraction = stats.subtraction();
        SpotProjectionResult.RemainingStats remaining = stats.remaining();
        SpotProjectionResult.OptimizationStats optimization = stats.optimization();
        SpotProjectionResult.HotDepth hotDepth = stats.hotDepth();
        long attributedProjectionNanos = timings.tileRangeNanos()
                + timings.projectionRectNanos()
                + timings.blockLookupNanos()
                + timings.projectableCheckNanos()
                + timings.planeWindowNanos()
                + timings.frontPassNanos()
                + timings.sideScanNanos()
                + timings.fullOccupancyNanos()
                + timings.occlusionAddNanos();
        long projectionResidualNanos = Math.max(0L, projectionElapsedNanos - attributedProjectionNanos);
        SpectralDiagnostics.WriteStats logWriteStats = SpectralDiagnostics.writeStats();
        SpectralDiagnostics.event(level, "spot_projection", "profile")
                .pos("source", node.pos())
                .field("direction", node.side())
                .field("power", outgoingPower)
                .field("cache_mode", projectionResult.cacheMode().name().toLowerCase(java.util.Locale.ROOT))
                .field("geometry_templates", projectionResult.geometryTemplates().size())
                .field("plane_count", VoxelSpotProjector.occlusionPlaneCount())
                .field("elapsed_us", projectionElapsedNanos / 1_000.0D)
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
                .field("side_internal_travel_intervals", sideDiagnostics.internalTravelIntervals())
                .field("side_external_travel_intervals", sideDiagnostics.externalTravelIntervals())
                .field("side_internal_window_attempts", sideDiagnostics.internalWindowAttempts())
                .field("side_external_window_attempts", sideDiagnostics.externalWindowAttempts())
                .field("side_internal_degenerate_travel", sideDiagnostics.internalDegenerateTravel())
                .field("side_external_degenerate_travel", sideDiagnostics.externalDegenerateTravel())
                .field("side_internal_not_renderable", sideDiagnostics.internalNotRenderable())
                .field("side_external_not_renderable", sideDiagnostics.externalNotRenderable())
                .field("side_internal_cross_null", sideDiagnostics.internalCrossNull())
                .field("side_external_cross_null", sideDiagnostics.externalCrossNull())
                .field("side_internal_window_null", sideDiagnostics.internalWindowNull())
                .field("side_external_window_null", sideDiagnostics.externalWindowNull())
                .field("side_internal_window_candidates", sideDiagnostics.internalWindowCandidates())
                .field("side_external_window_candidates", sideDiagnostics.externalWindowCandidates())
                .field("side_internal_visible_empty", sideDiagnostics.internalVisibleEmpty())
                .field("side_external_visible_empty", sideDiagnostics.externalVisibleEmpty())
                .field("side_internal_low_power", sideDiagnostics.internalLowPower())
                .field("side_external_low_power", sideDiagnostics.externalLowPower())
                .field("side_internal_patch_null", sideDiagnostics.internalPatchNull())
                .field("side_external_patch_null", sideDiagnostics.externalPatchNull())
                .field("side_internal_spot_invisible", sideDiagnostics.internalSpotInvisible())
                .field("side_external_spot_invisible", sideDiagnostics.externalSpotInvisible())
                .field("side_internal_emitted_quads", sideDiagnostics.internalEmittedQuads())
                .field("side_external_emitted_quads", sideDiagnostics.externalEmittedQuads())
                .field("side_internal_tiny_texture_patches", sideDiagnostics.internalTinyTexturePatches())
                .field("side_external_tiny_texture_patches", sideDiagnostics.externalTinyTexturePatches())
                .field("side_internal_large_stretch_patches", sideDiagnostics.internalLargeStretchPatches())
                .field("side_external_large_stretch_patches", sideDiagnostics.externalLargeStretchPatches())
                .field("side_max_stretch_ratio", sideDiagnostics.maxStretchRatio())
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
                .field("footprint_integral_calls", optimization.footprintIntegralCalls())
                .field("surface_appearance_builds", optimization.surfaceAppearanceBuilds())
                .field("surface_appearance_cache_hits", optimization.surfaceAppearanceCacheHits())
                .field("side_candidate_tiles_visited", optimization.sideCandidateTilesVisited())
                .field("side_visible_windows_before_merge", optimization.sideVisibleWindowsBeforeMerge())
                .field("side_visible_windows_after_merge", optimization.sideVisibleWindowsAfterMerge())
                .field("side_boundary_missing_examples", String.join(" | ", optimization.sideBoundaryMissingExamples()))
                .field("remaining_subtract_validation_checks", optimization.remainingSubtractValidationChecks())
                .field("remaining_subtract_validation_mismatches", optimization.remainingSubtractValidationMismatches())
                .field("same_depth_split_index_queries", optimization.sameDepthSplitIndexQueries())
                .field("same_depth_travel_groups_visited", optimization.sameDepthTravelGroupsVisited())
                .field("same_depth_prefix_index_queries", optimization.sameDepthPrefixIndexQueries())
                .field("same_depth_prefix_index_candidates", optimization.sameDepthPrefixIndexCandidates())
                .field("same_depth_prefix_index_hits", optimization.sameDepthPrefixIndexHits())
                .field("same_depth_split_validation_checks", optimization.sameDepthSplitValidationChecks())
                .field("same_depth_split_validation_mismatches", optimization.sameDepthSplitValidationMismatches())
                .field("same_depth_prefix_validation_checks", optimization.sameDepthPrefixValidationChecks())
                .field("same_depth_prefix_validation_mismatches", optimization.sameDepthPrefixValidationMismatches())
                .field("side_canonical_validation_checks", optimization.sideCanonicalValidationChecks())
                .field("side_canonical_validation_mismatches", optimization.sideCanonicalValidationMismatches())
                .field("depth_boundary_radius_checks", optimization.depthBoundaryRadiusChecks())
                .field("depth_boundary_radius_mismatches", optimization.depthBoundaryRadiusMismatches())
                .field("depth_boundary_radius_max_gap", optimization.depthBoundaryRadiusMaxGap())
                .field("structural_validation_examples", String.join(" | ", optimization.structuralValidationExamples()))
                .field("projection_attributed_us", attributedProjectionNanos / 1_000.0D)
                .field("projection_residual_us", projectionResidualNanos / 1_000.0D)
                .field("tile_range_us", timings.tileRangeNanos() / 1_000.0D)
                .field("projection_rect_us", timings.projectionRectNanos() / 1_000.0D)
                .field("block_lookup_us", timings.blockLookupNanos() / 1_000.0D)
                .field("projectable_check_us", timings.projectableCheckNanos() / 1_000.0D)
                .field("plane_window_us", timings.planeWindowNanos() / 1_000.0D)
                .field("front_pass_us", timings.frontPassNanos() / 1_000.0D)
                .field("front_intersect_us", timings.frontSubtractNanos() / 1_000.0D)
                .field("side_scan_us", timings.sideScanNanos() / 1_000.0D)
                .field("side_candidate_us", timings.sideCandidateNanos() / 1_000.0D)
                .field("side_emit_us", timings.sideEmitNanos() / 1_000.0D)
                .field("side_travel_split_us", timings.sideTravelSplitNanos() / 1_000.0D)
                .field("side_occlusion_index_build_us", timings.sideOcclusionIndexBuildNanos() / 1_000.0D)
                .field("side_same_depth_split_us", timings.sideSameDepthSplitNanos() / 1_000.0D)
                .field("side_window_us", timings.sideWindowNanos() / 1_000.0D)
                .field("side_prefix_query_us", timings.sidePrefixQueryNanos() / 1_000.0D)
                .field("side_remaining_intersect_us", timings.sideRemainingIntersectNanos() / 1_000.0D)
                .field("side_region_intersect_us", timings.sideRegionIntersectNanos() / 1_000.0D)
                .field("side_canonical_normalize_us", timings.sideCanonicalNormalizeNanos() / 1_000.0D)
                .field("side_debug_audit_us", timings.sideDebugAuditNanos() / 1_000.0D)
                .field("side_patch_emit_us", timings.sidePatchEmitNanos() / 1_000.0D)
                .field("surface_appearance_build_us", timings.surfaceAppearanceBuildNanos() / 1_000.0D)
                .field("patch_clip_us", timings.patchClipNanos() / 1_000.0D)
                .field("spot_record_pack_us", timings.spotRecordPackNanos() / 1_000.0D)
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

        for (SpotProjectionResult.BoundaryMissingFace missing : optimization.sideBoundaryMissingDetails()) {
            BlockPos relative = missing.pos().subtract(node.pos());
            SpectralDiagnostics.event(level, "spot_projection", "boundary_missing_face")
                    .pos("source", node.pos())
                    .field("direction", node.side())
                    .field("depth", missing.depth())
                    .pos("target", missing.pos())
                    .field("relative_x", relative.getX())
                    .field("relative_y", relative.getY())
                    .field("relative_z", relative.getZ())
                    .field("face", missing.face())
                    .field("state", missing.blockState())
                    .field("candidate_path", missing.candidatePath())
                    .field("reject_stage", missing.rejectionStage())
                    .write();
        }
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

    private static void addVisibleSpot(
            Map<GeometryKey, SpotRecord> primarySpots,
            Map<GeometryKey, SpotRecord> sideSpots,
            SpotRecord spot,
            SpotBudgetStats budgetStats,
            Direction travelDirection
    ) {
        if (!spot.visible()) {
            return;
        }

        if (spot.face() != travelDirection.getOpposite()) {
            budgetStats.generatedSide++;
            addQuantizedSpot(sideSpots, spot, budgetStats);
            return;
        }

        budgetStats.generatedPrimary++;
        addQuantizedSpot(primarySpots, spot, budgetStats);
    }

    private static void addQuantizedSpot(
            Map<GeometryKey, SpotRecord> spots,
            SpotRecord spot,
            SpotBudgetStats budgetStats
    ) {
        GeometryKey geometryKey = spot.geometryKey();
        if (spots.containsKey(geometryKey)) {
            spots.put(geometryKey, spot);
            budgetStats.deduplicated++;
        } else if (spots.size() < MAX_SPOTS_PER_SAMPLE) {
            spots.put(geometryKey, spot);
        }
    }

    private static void addVisibleSpots(
            Map<GeometryKey, SpotRecord> primarySpots,
            Map<GeometryKey, SpotRecord> sideSpots,
            List<SpotProjectionAllocation> allocations,
            SpotProjectionResult projectionResult,
            LongSet projectionDependencies,
            SpotBudgetStats budgetStats,
            Direction travelDirection
    ) {
        projectionDependencies.addAll(projectionResult.dependencies());
        allocations.addAll(projectionResult.allocations());

        for (SpotRecord spot : projectionResult.spots()) {
            addVisibleSpot(primarySpots, sideSpots, spot, budgetStats, travelDirection);
        }
    }

    private static void logSpotBudget(
            ServerLevel level,
            int exportedSpots,
            int dependencyCount,
            SpotBudgetStats budgetStats
    ) {
        if (!SpectralizationConfig.opticalCompilerDebugLog()) {
            return;
        }

        int generatedSpots = budgetStats.generatedPrimary + budgetStats.generatedSide;
        int uniqueGeneratedSpots = Math.max(0, generatedSpots - budgetStats.deduplicated);
        SpectralDiagnostics.event(level, "spot_projection", "budget")
                .field("quota", MAX_SPOTS_PER_SAMPLE)
                .field("generated_primary", budgetStats.generatedPrimary)
                .field("generated_side", budgetStats.generatedSide)
                .field("generated", generatedSpots)
                .field("unique_generated", uniqueGeneratedSpots)
                .field("deduplicated", budgetStats.deduplicated)
                .field("exported", exportedSpots)
                .field("dropped", Math.max(0, uniqueGeneratedSpots - exportedSpots))
                .field("payload_chunks", Math.max(1, (exportedSpots + SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK - 1)
                        / SpotProjectionLimits.SPOTS_PER_PAYLOAD_CHUNK))
                .field("outgoing_nodes", budgetStats.outgoingNodes)
                .field("projected_nodes", budgetStats.projectedNodes)
                .field("geometry_cache_hits", budgetStats.geometryCacheHits)
                .field("geometry_cache_misses", budgetStats.geometryCacheMisses)
                .field("appearance_only_updates", budgetStats.appearanceOnlyUpdates)
                .field("full_geometry_rebuilds", budgetStats.fullGeometryRebuilds)
                .field("dependencies", dependencyCount)
                .write();
    }

    private static List<SpotRecord> cappedSpots(
            Map<GeometryKey, SpotRecord> primarySpots,
            Map<GeometryKey, SpotRecord> sideSpots
    ) {
        List<SpotRecord> spots = new ArrayList<>(Math.min(MAX_SPOTS_PER_SAMPLE, primarySpots.size() + sideSpots.size()));

        for (SpotRecord spot : primarySpots.values()) {
            if (spots.size() >= MAX_SPOTS_PER_SAMPLE) {
                assignDebugMarkers(spots);
                return spots;
            }

            spots.add(spot);
        }

        for (SpotRecord spot : sideSpots.values()) {
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

    private static final class SpotBudgetStats {
        private int generatedPrimary;
        private int generatedSide;
        private int deduplicated;
        private int outgoingNodes;
        private int projectedNodes;
        private int geometryCacheHits;
        private int geometryCacheMisses;
        private int appearanceOnlyUpdates;
        private int fullGeometryRebuilds;
    }

    private record ProjectionGeometryKey(
            int networkId,
            BlockPos sourcePos,
            Direction direction,
            BeamEnvelope envelope,
            int occlusionPlaneCount
    ) {
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
