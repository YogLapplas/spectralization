package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.SpotRecord;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record SpotProjectionResult(
        List<SpotRecord> spots,
        DependencySnapshot dependencySnapshot,
        List<SpotProjectionAllocation> allocations,
        Stats stats,
        List<SpotRecord> geometryTemplates,
        AppearancePlan appearancePlan,
        CacheMode cacheMode,
        AppearanceTimings appearanceTimings
) {
    public static final SpotProjectionResult EMPTY =
            new SpotProjectionResult(
                    List.of(), DependencySnapshot.EMPTY, List.of(), Stats.EMPTY, List.of(), AppearancePlan.EMPTY,
                    CacheMode.EMPTY,
                    AppearanceTimings.EMPTY
            );

    public SpotProjectionResult(List<SpotRecord> spots, LongSet dependencies) {
        this(spots, new DependencySnapshot(dependencies), List.of(), Stats.EMPTY, spots, AppearancePlan.EMPTY,
                CacheMode.FULL_REBUILD, AppearanceTimings.EMPTY);
    }

    public SpotProjectionResult(
            List<SpotRecord> spots,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations
    ) {
        this(spots, new DependencySnapshot(dependencies), allocations, Stats.EMPTY, spots, AppearancePlan.EMPTY,
                CacheMode.FULL_REBUILD, AppearanceTimings.EMPTY);
    }

    public SpotProjectionResult(
            List<SpotRecord> spots,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations,
            Stats stats
    ) {
        this(spots, new DependencySnapshot(dependencies), allocations, stats, spots, AppearancePlan.EMPTY,
                CacheMode.FULL_REBUILD, AppearanceTimings.EMPTY);
    }

    public SpotProjectionResult {
        Objects.requireNonNull(spots, "spots");
        Objects.requireNonNull(dependencySnapshot, "dependencySnapshot");
        Objects.requireNonNull(allocations, "allocations");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(geometryTemplates, "geometryTemplates");
        Objects.requireNonNull(appearancePlan, "appearancePlan");
        Objects.requireNonNull(cacheMode, "cacheMode");
        Objects.requireNonNull(appearanceTimings, "appearanceTimings");
        spots = List.copyOf(spots);
        allocations = List.copyOf(allocations);
        geometryTemplates = List.copyOf(geometryTemplates);
    }

    public LongSet dependencies() {
        return dependencySnapshot.positions();
    }

    public enum CacheMode {
        EMPTY,
        FULL_REBUILD,
        APPEARANCE_ONLY
    }

    public static final class DependencySnapshot {
        public static final DependencySnapshot EMPTY = new DependencySnapshot(new LongOpenHashSet());
        private final LongSet positions;

        public DependencySnapshot(LongSet positions) {
            Objects.requireNonNull(positions, "positions");
            this.positions = LongSets.unmodifiable(new LongOpenHashSet(positions));
        }

        public int size() {
            return positions.size();
        }

        private LongSet positions() {
            return positions;
        }
    }

    public record AppearanceSurface(
            BlockPos pos,
            Direction face,
            BeamEnvelope envelope,
            double powerScale
    ) {
        public AppearanceSurface {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(face, "face");
            Objects.requireNonNull(envelope, "envelope");
            if (!Double.isFinite(powerScale) || powerScale < 0.0D) {
                throw new IllegalArgumentException("Appearance surface power scale must be finite and non-negative");
            }
            pos = pos.immutable();
        }
    }

    public static final class AppearancePlan {
        public static final AppearancePlan EMPTY = new AppearancePlan(List.of(), new int[0]);
        private final List<AppearanceSurface> surfaces;
        private final int[] surfaceIndexByTemplate;

        public AppearancePlan(List<AppearanceSurface> surfaces, int[] surfaceIndexByTemplate) {
            this.surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            this.surfaceIndexByTemplate = Objects.requireNonNull(
                    surfaceIndexByTemplate,
                    "surfaceIndexByTemplate"
            ).clone();
            for (int surfaceIndex : this.surfaceIndexByTemplate) {
                if (surfaceIndex < -1 || surfaceIndex >= this.surfaces.size()) {
                    throw new IllegalArgumentException("Appearance plan contains an invalid surface index");
                }
            }
        }

        public int surfaceCount() {
            return surfaces.size();
        }

        public AppearanceSurface surface(int index) {
            return surfaces.get(index);
        }

        public int templateCount() {
            return surfaceIndexByTemplate.length;
        }

        public int surfaceIndexForTemplate(int templateIndex) {
            return surfaceIndexByTemplate[templateIndex];
        }
    }

    public record AppearanceTimings(
            long totalNanos,
            long prepareNanos,
            long surfaceBuildNanos,
            long recordUpdateNanos,
            int templates,
            int uniqueSurfaces,
            int surfaceBuilds,
            int surfaceCacheHits,
            boolean planReused
    ) {
        public static final AppearanceTimings EMPTY = new AppearanceTimings(
                0L, 0L, 0L, 0L, 0, 0, 0, 0, false
        );

        public AppearanceTimings {
            totalNanos = Math.max(0L, totalNanos);
            prepareNanos = Math.max(0L, prepareNanos);
            surfaceBuildNanos = Math.max(0L, surfaceBuildNanos);
            recordUpdateNanos = Math.max(0L, recordUpdateNanos);
            templates = Math.max(0, templates);
            uniqueSurfaces = Math.max(0, uniqueSurfaces);
            surfaceBuilds = Math.max(0, surfaceBuilds);
            surfaceCacheHits = Math.max(0, surfaceCacheHits);
        }
    }

    public record Stats(
            int depths,
            long scannedTiles,
            long candidateTiles,
            long loadedTiles,
            long airTiles,
            long nonProjectableTiles,
            long projectableTiles,
            long sideTilesScanned,
            long sideLoadedTiles,
            long sideProjectableTiles,
            long sideOpenChecks,
            long sideOpenFaces,
            long sideTravelIntervals,
            long sideWindowCandidates,
            long sideFastPathPatches,
            long sideFastPathSkipped,
            long sideRangeCulledTiles,
            long depthTileCacheHits,
            long depthTileCacheMisses,
            long sideBoundaryFaceTests,
            long sideBoundaryProjectableFaces,
            long sideBoundaryOpenFaces,
            long sideBoundaryTravelIntervals,
            long sideBoundaryTravelFaces,
            long sideLegacyCandidateFaces,
            long sideBoundaryCandidateFaces,
            long sideBoundaryMissingFaces,
            long sideBoundaryExtraFaces,
            long planeWindowTests,
            long planeWindowCandidates,
            long planeWindowRemainingCulled,
            long planeWindows,
            long frontSubtractions,
            long sideSubtractions,
            long occupiedWindowTests,
            long occupiedWindowHits,
            int maxOccupiedWindows,
            int maxVisibleFragments,
            long frontFragmentsBeforeMerge,
            long frontFragmentsAfterMerge,
            SideDiagnostics sideDiagnostics,
            StageTimings timings,
            IndexStats index,
            SubtractionStats subtraction,
            RemainingStats remaining,
            OptimizationStats optimization,
            HotDepth hotDepth
    ) {
        public static final Stats EMPTY = new Stats(
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                0L,
                0L,
                SideDiagnostics.EMPTY,
                StageTimings.EMPTY,
                IndexStats.EMPTY,
                SubtractionStats.EMPTY,
                RemainingStats.EMPTY,
                OptimizationStats.EMPTY,
                HotDepth.EMPTY
        );

        public Stats {
            depths = Math.max(0, depths);
            scannedTiles = Math.max(0L, scannedTiles);
            candidateTiles = Math.max(0L, candidateTiles);
            loadedTiles = Math.max(0L, loadedTiles);
            airTiles = Math.max(0L, airTiles);
            nonProjectableTiles = Math.max(0L, nonProjectableTiles);
            projectableTiles = Math.max(0L, projectableTiles);
            sideTilesScanned = Math.max(0L, sideTilesScanned);
            sideLoadedTiles = Math.max(0L, sideLoadedTiles);
            sideProjectableTiles = Math.max(0L, sideProjectableTiles);
            sideOpenChecks = Math.max(0L, sideOpenChecks);
            sideOpenFaces = Math.max(0L, sideOpenFaces);
            sideTravelIntervals = Math.max(0L, sideTravelIntervals);
            sideWindowCandidates = Math.max(0L, sideWindowCandidates);
            sideFastPathPatches = Math.max(0L, sideFastPathPatches);
            sideFastPathSkipped = Math.max(0L, sideFastPathSkipped);
            sideRangeCulledTiles = Math.max(0L, sideRangeCulledTiles);
            depthTileCacheHits = Math.max(0L, depthTileCacheHits);
            depthTileCacheMisses = Math.max(0L, depthTileCacheMisses);
            sideBoundaryFaceTests = Math.max(0L, sideBoundaryFaceTests);
            sideBoundaryProjectableFaces = Math.max(0L, sideBoundaryProjectableFaces);
            sideBoundaryOpenFaces = Math.max(0L, sideBoundaryOpenFaces);
            sideBoundaryTravelIntervals = Math.max(0L, sideBoundaryTravelIntervals);
            sideBoundaryTravelFaces = Math.max(0L, sideBoundaryTravelFaces);
            sideLegacyCandidateFaces = Math.max(0L, sideLegacyCandidateFaces);
            sideBoundaryCandidateFaces = Math.max(0L, sideBoundaryCandidateFaces);
            sideBoundaryMissingFaces = Math.max(0L, sideBoundaryMissingFaces);
            sideBoundaryExtraFaces = Math.max(0L, sideBoundaryExtraFaces);
            planeWindowTests = Math.max(0L, planeWindowTests);
            planeWindowCandidates = Math.max(0L, planeWindowCandidates);
            planeWindowRemainingCulled = Math.max(0L, planeWindowRemainingCulled);
            planeWindows = Math.max(0L, planeWindows);
            frontSubtractions = Math.max(0L, frontSubtractions);
            sideSubtractions = Math.max(0L, sideSubtractions);
            occupiedWindowTests = Math.max(0L, occupiedWindowTests);
            occupiedWindowHits = Math.max(0L, occupiedWindowHits);
            maxOccupiedWindows = Math.max(0, maxOccupiedWindows);
            maxVisibleFragments = Math.max(0, maxVisibleFragments);
            frontFragmentsBeforeMerge = Math.max(0L, frontFragmentsBeforeMerge);
            frontFragmentsAfterMerge = Math.max(0L, frontFragmentsAfterMerge);
            sideDiagnostics = sideDiagnostics == null ? SideDiagnostics.EMPTY : sideDiagnostics;
            timings = timings == null ? StageTimings.EMPTY : timings;
            index = index == null ? IndexStats.EMPTY : index;
            subtraction = subtraction == null ? SubtractionStats.EMPTY : subtraction;
            remaining = remaining == null ? RemainingStats.EMPTY : remaining;
            optimization = optimization == null ? OptimizationStats.EMPTY : optimization;
            hotDepth = hotDepth == null ? HotDepth.EMPTY : hotDepth;
        }
    }

    public record OptimizationStats(
            long footprintIntegralCalls,
            long surfaceAppearanceBuilds,
            long surfaceAppearanceCacheHits,
            long sideCandidateTilesVisited,
            long sideVisibleWindowsBeforeMerge,
            long sideVisibleWindowsAfterMerge,
            List<String> sideBoundaryMissingExamples,
            List<BoundaryMissingFace> sideBoundaryMissingDetails,
            long remainingSubtractValidationChecks,
            long remainingSubtractValidationMismatches,
            long sameDepthSplitIndexQueries,
            long sameDepthTravelGroupsVisited,
            long sameDepthPrefixIndexQueries,
            long sameDepthPrefixIndexCandidates,
            long sameDepthPrefixIndexHits,
            long sameDepthSplitValidationChecks,
            long sameDepthSplitValidationMismatches,
            long sameDepthPrefixValidationChecks,
            long sameDepthPrefixValidationMismatches,
            long sideCanonicalValidationChecks,
            long sideCanonicalValidationMismatches,
            List<String> structuralValidationExamples
    ) {
        public static final OptimizationStats EMPTY = new OptimizationStats(
                0L, 0L, 0L, 0L, 0L, 0L, List.of(), List.of(),
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, List.of()
        );

        public OptimizationStats {
            footprintIntegralCalls = Math.max(0L, footprintIntegralCalls);
            surfaceAppearanceBuilds = Math.max(0L, surfaceAppearanceBuilds);
            surfaceAppearanceCacheHits = Math.max(0L, surfaceAppearanceCacheHits);
            sideCandidateTilesVisited = Math.max(0L, sideCandidateTilesVisited);
            sideVisibleWindowsBeforeMerge = Math.max(0L, sideVisibleWindowsBeforeMerge);
            sideVisibleWindowsAfterMerge = Math.max(0L, sideVisibleWindowsAfterMerge);
            sideBoundaryMissingExamples = sideBoundaryMissingExamples == null
                    ? List.of()
                    : List.copyOf(sideBoundaryMissingExamples);
            sideBoundaryMissingDetails = sideBoundaryMissingDetails == null
                    ? List.of()
                    : List.copyOf(sideBoundaryMissingDetails);
            remainingSubtractValidationChecks = Math.max(0L, remainingSubtractValidationChecks);
            remainingSubtractValidationMismatches = Math.max(0L, remainingSubtractValidationMismatches);
            sameDepthSplitIndexQueries = Math.max(0L, sameDepthSplitIndexQueries);
            sameDepthTravelGroupsVisited = Math.max(0L, sameDepthTravelGroupsVisited);
            sameDepthPrefixIndexQueries = Math.max(0L, sameDepthPrefixIndexQueries);
            sameDepthPrefixIndexCandidates = Math.max(0L, sameDepthPrefixIndexCandidates);
            sameDepthPrefixIndexHits = Math.max(0L, sameDepthPrefixIndexHits);
            sameDepthSplitValidationChecks = Math.max(0L, sameDepthSplitValidationChecks);
            sameDepthSplitValidationMismatches = Math.max(0L, sameDepthSplitValidationMismatches);
            sameDepthPrefixValidationChecks = Math.max(0L, sameDepthPrefixValidationChecks);
            sameDepthPrefixValidationMismatches = Math.max(0L, sameDepthPrefixValidationMismatches);
            sideCanonicalValidationChecks = Math.max(0L, sideCanonicalValidationChecks);
            sideCanonicalValidationMismatches = Math.max(0L, sideCanonicalValidationMismatches);
            structuralValidationExamples = structuralValidationExamples == null
                    ? List.of()
                    : List.copyOf(structuralValidationExamples);
        }
    }

    public record BoundaryMissingFace(
            int depth,
            BlockPos pos,
            Direction face,
            String blockState,
            String candidatePath,
            String rejectionStage
    ) {
        public BoundaryMissingFace {
            depth = Math.max(0, depth);
            pos = Objects.requireNonNull(pos, "pos").immutable();
            face = Objects.requireNonNull(face, "face");
            blockState = Objects.requireNonNullElse(blockState, "unknown");
            candidatePath = Objects.requireNonNullElse(candidatePath, "unknown");
            rejectionStage = Objects.requireNonNullElse(rejectionStage, "unknown");
        }
    }

    public record SideDiagnostics(
            long internalTravelIntervals,
            long externalTravelIntervals,
            long internalWindowAttempts,
            long externalWindowAttempts,
            long internalDegenerateTravel,
            long externalDegenerateTravel,
            long internalNotRenderable,
            long externalNotRenderable,
            long internalCrossNull,
            long externalCrossNull,
            long internalWindowNull,
            long externalWindowNull,
            long internalWindowCandidates,
            long externalWindowCandidates,
            long internalVisibleEmpty,
            long externalVisibleEmpty,
            long internalLowPower,
            long externalLowPower,
            long internalPatchNull,
            long externalPatchNull,
            long internalSpotInvisible,
            long externalSpotInvisible,
            long internalEmittedQuads,
            long externalEmittedQuads,
            long internalTinyTexturePatches,
            long externalTinyTexturePatches,
            long internalLargeStretchPatches,
            long externalLargeStretchPatches,
            double maxStretchRatio
    ) {
        public static final SideDiagnostics EMPTY = new SideDiagnostics(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0D
        );

        public SideDiagnostics {
            internalTravelIntervals = Math.max(0L, internalTravelIntervals);
            externalTravelIntervals = Math.max(0L, externalTravelIntervals);
            internalWindowAttempts = Math.max(0L, internalWindowAttempts);
            externalWindowAttempts = Math.max(0L, externalWindowAttempts);
            internalDegenerateTravel = Math.max(0L, internalDegenerateTravel);
            externalDegenerateTravel = Math.max(0L, externalDegenerateTravel);
            internalNotRenderable = Math.max(0L, internalNotRenderable);
            externalNotRenderable = Math.max(0L, externalNotRenderable);
            internalCrossNull = Math.max(0L, internalCrossNull);
            externalCrossNull = Math.max(0L, externalCrossNull);
            internalWindowNull = Math.max(0L, internalWindowNull);
            externalWindowNull = Math.max(0L, externalWindowNull);
            internalWindowCandidates = Math.max(0L, internalWindowCandidates);
            externalWindowCandidates = Math.max(0L, externalWindowCandidates);
            internalVisibleEmpty = Math.max(0L, internalVisibleEmpty);
            externalVisibleEmpty = Math.max(0L, externalVisibleEmpty);
            internalLowPower = Math.max(0L, internalLowPower);
            externalLowPower = Math.max(0L, externalLowPower);
            internalPatchNull = Math.max(0L, internalPatchNull);
            externalPatchNull = Math.max(0L, externalPatchNull);
            internalSpotInvisible = Math.max(0L, internalSpotInvisible);
            externalSpotInvisible = Math.max(0L, externalSpotInvisible);
            internalEmittedQuads = Math.max(0L, internalEmittedQuads);
            externalEmittedQuads = Math.max(0L, externalEmittedQuads);
            internalTinyTexturePatches = Math.max(0L, internalTinyTexturePatches);
            externalTinyTexturePatches = Math.max(0L, externalTinyTexturePatches);
            internalLargeStretchPatches = Math.max(0L, internalLargeStretchPatches);
            externalLargeStretchPatches = Math.max(0L, externalLargeStretchPatches);
            maxStretchRatio = Double.isFinite(maxStretchRatio) ? Math.max(0.0D, maxStretchRatio) : 0.0D;
        }
    }

    public record StageTimings(
            long tileRangeNanos,
            long projectionRectNanos,
            long blockLookupNanos,
            long projectableCheckNanos,
            long planeWindowNanos,
            long frontPassNanos,
            long frontSubtractNanos,
            long sideScanNanos,
            long sideCandidateNanos,
            long sideEmitNanos,
            long sideTravelSplitNanos,
            long sideOcclusionIndexBuildNanos,
            long sideSameDepthSplitNanos,
            long sideWindowNanos,
            long sidePrefixQueryNanos,
            long sideRemainingIntersectNanos,
            long sideRegionIntersectNanos,
            long sideCanonicalNormalizeNanos,
            long sideDebugAuditNanos,
            long sidePatchEmitNanos,
            long surfaceAppearanceBuildNanos,
            long patchClipNanos,
            long spotRecordPackNanos,
            long sideCandidateVerifyNanos,
            long sideSubtractNanos,
            long indexQueryNanos,
            long subtractApplyNanos,
            long frontEmitNanos,
            long fullOccupancyNanos,
            long remainingUnionNanos,
            long remainingSubtractNanos,
            long occlusionAddNanos
    ) {
        public static final StageTimings EMPTY = new StageTimings(
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
        );

        public StageTimings {
            tileRangeNanos = Math.max(0L, tileRangeNanos);
            projectionRectNanos = Math.max(0L, projectionRectNanos);
            blockLookupNanos = Math.max(0L, blockLookupNanos);
            projectableCheckNanos = Math.max(0L, projectableCheckNanos);
            planeWindowNanos = Math.max(0L, planeWindowNanos);
            frontPassNanos = Math.max(0L, frontPassNanos);
            frontSubtractNanos = Math.max(0L, frontSubtractNanos);
            sideScanNanos = Math.max(0L, sideScanNanos);
            sideCandidateNanos = Math.max(0L, sideCandidateNanos);
            sideEmitNanos = Math.max(0L, sideEmitNanos);
            sideTravelSplitNanos = Math.max(0L, sideTravelSplitNanos);
            sideOcclusionIndexBuildNanos = Math.max(0L, sideOcclusionIndexBuildNanos);
            sideSameDepthSplitNanos = Math.max(0L, sideSameDepthSplitNanos);
            sideWindowNanos = Math.max(0L, sideWindowNanos);
            sidePrefixQueryNanos = Math.max(0L, sidePrefixQueryNanos);
            sideRemainingIntersectNanos = Math.max(0L, sideRemainingIntersectNanos);
            sideRegionIntersectNanos = Math.max(0L, sideRegionIntersectNanos);
            sideCanonicalNormalizeNanos = Math.max(0L, sideCanonicalNormalizeNanos);
            sideDebugAuditNanos = Math.max(0L, sideDebugAuditNanos);
            sidePatchEmitNanos = Math.max(0L, sidePatchEmitNanos);
            surfaceAppearanceBuildNanos = Math.max(0L, surfaceAppearanceBuildNanos);
            patchClipNanos = Math.max(0L, patchClipNanos);
            spotRecordPackNanos = Math.max(0L, spotRecordPackNanos);
            sideCandidateVerifyNanos = Math.max(0L, sideCandidateVerifyNanos);
            sideSubtractNanos = Math.max(0L, sideSubtractNanos);
            indexQueryNanos = Math.max(0L, indexQueryNanos);
            subtractApplyNanos = Math.max(0L, subtractApplyNanos);
            frontEmitNanos = Math.max(0L, frontEmitNanos);
            fullOccupancyNanos = Math.max(0L, fullOccupancyNanos);
            remainingUnionNanos = Math.max(0L, remainingUnionNanos);
            remainingSubtractNanos = Math.max(0L, remainingSubtractNanos);
            occlusionAddNanos = Math.max(0L, occlusionAddNanos);
        }
    }

    public record IndexStats(
            long bucketQueries,
            long bucketEntries,
            long duplicateSkips,
            long largeWindowTests,
            long largeWindowHits,
            int maxQueryBuckets,
            int maxQueryEntries,
            int maxLargeWindows
    ) {
        public static final IndexStats EMPTY = new IndexStats(0L, 0L, 0L, 0L, 0L, 0, 0, 0);

        public IndexStats {
            bucketQueries = Math.max(0L, bucketQueries);
            bucketEntries = Math.max(0L, bucketEntries);
            duplicateSkips = Math.max(0L, duplicateSkips);
            largeWindowTests = Math.max(0L, largeWindowTests);
            largeWindowHits = Math.max(0L, largeWindowHits);
            maxQueryBuckets = Math.max(0, maxQueryBuckets);
            maxQueryEntries = Math.max(0, maxQueryEntries);
            maxLargeWindows = Math.max(0, maxLargeWindows);
        }
    }

    public record SubtractionStats(
            long intersectingWindows,
            int maxIntersectingWindows,
            long applySteps,
            long splitSteps,
            long emptyResults
    ) {
        public static final SubtractionStats EMPTY = new SubtractionStats(0L, 0, 0L, 0L, 0L);

        public SubtractionStats {
            intersectingWindows = Math.max(0L, intersectingWindows);
            maxIntersectingWindows = Math.max(0, maxIntersectingWindows);
            applySteps = Math.max(0L, applySteps);
            splitSteps = Math.max(0L, splitSteps);
            emptyResults = Math.max(0L, emptyResults);
        }
    }

    public record RemainingStats(
            int slabs,
            int maxSlabs,
            int intervals,
            int maxIntervals,
            double area,
            double minArea,
            long intersectionQueries,
            long intersectionSlabTests,
            long intersectionIntervalTests,
            long visibleFragments,
            long prefilterQueries,
            long prefilterHits,
            long prefilterSlabTests,
            long prefilterIntervalTests,
            long unionInputRects,
            long unionMergedRects,
            long blockerTests,
            long blockerHits,
            long clippedBlockers,
            long blockerSlabSteps,
            long intervalClipTests,
            long applySteps,
            long splitSteps,
            long emptyResults
    ) {
        public static final RemainingStats EMPTY = new RemainingStats(
                0,
                0,
                0,
                0,
                0.0D,
                0.0D,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L
        );

        public RemainingStats {
            slabs = Math.max(0, slabs);
            maxSlabs = Math.max(0, maxSlabs);
            intervals = Math.max(0, intervals);
            maxIntervals = Math.max(0, maxIntervals);
            area = Math.max(0.0D, area);
            minArea = Math.max(0.0D, minArea);
            intersectionQueries = Math.max(0L, intersectionQueries);
            intersectionSlabTests = Math.max(0L, intersectionSlabTests);
            intersectionIntervalTests = Math.max(0L, intersectionIntervalTests);
            visibleFragments = Math.max(0L, visibleFragments);
            prefilterQueries = Math.max(0L, prefilterQueries);
            prefilterHits = Math.max(0L, prefilterHits);
            prefilterSlabTests = Math.max(0L, prefilterSlabTests);
            prefilterIntervalTests = Math.max(0L, prefilterIntervalTests);
            unionInputRects = Math.max(0L, unionInputRects);
            unionMergedRects = Math.max(0L, unionMergedRects);
            blockerTests = Math.max(0L, blockerTests);
            blockerHits = Math.max(0L, blockerHits);
            clippedBlockers = Math.max(0L, clippedBlockers);
            blockerSlabSteps = Math.max(0L, blockerSlabSteps);
            intervalClipTests = Math.max(0L, intervalClipTests);
            applySteps = Math.max(0L, applySteps);
            splitSteps = Math.max(0L, splitSteps);
            emptyResults = Math.max(0L, emptyResults);
        }
    }

    public record HotDepth(
            int depth,
            long elapsedNanos,
            long scannedTiles,
            long candidateTiles,
            long projectableTiles,
            long planeWindows,
            long frontSubtractions,
            long sideSubtractions,
            long occupiedWindowTests,
            long occupiedWindowHits,
            int spots
    ) {
        public static final HotDepth EMPTY = new HotDepth(0, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0);

        public HotDepth {
            depth = Math.max(0, depth);
            elapsedNanos = Math.max(0L, elapsedNanos);
            scannedTiles = Math.max(0L, scannedTiles);
            candidateTiles = Math.max(0L, candidateTiles);
            projectableTiles = Math.max(0L, projectableTiles);
            planeWindows = Math.max(0L, planeWindows);
            frontSubtractions = Math.max(0L, frontSubtractions);
            sideSubtractions = Math.max(0L, sideSubtractions);
            occupiedWindowTests = Math.max(0L, occupiedWindowTests);
            occupiedWindowHits = Math.max(0L, occupiedWindowHits);
            spots = Math.max(0, spots);
        }
    }
}
