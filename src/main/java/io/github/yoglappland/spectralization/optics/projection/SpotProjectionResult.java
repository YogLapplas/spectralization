package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Objects;

public record SpotProjectionResult(
        List<SpotRecord> spots,
        LongSet dependencies,
        List<SpotProjectionAllocation> allocations,
        Stats stats
) {
    public static final SpotProjectionResult EMPTY =
            new SpotProjectionResult(List.of(), new LongOpenHashSet(), List.of(), Stats.EMPTY);

    public SpotProjectionResult(List<SpotRecord> spots, LongSet dependencies) {
        this(spots, dependencies, List.of(), Stats.EMPTY);
    }

    public SpotProjectionResult(
            List<SpotRecord> spots,
            LongSet dependencies,
            List<SpotProjectionAllocation> allocations
    ) {
        this(spots, dependencies, allocations, Stats.EMPTY);
    }

    public SpotProjectionResult {
        Objects.requireNonNull(spots, "spots");
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(allocations, "allocations");
        Objects.requireNonNull(stats, "stats");
        spots = List.copyOf(spots);
        dependencies = new LongOpenHashSet(dependencies);
        allocations = List.copyOf(allocations);
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
            hotDepth = hotDepth == null ? HotDepth.EMPTY : hotDepth;
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
            long frontSubtractNanos,
            long sideScanNanos,
            long sideCandidateNanos,
            long sideEmitNanos,
            long sideTravelSplitNanos,
            long sideWindowNanos,
            long sideRemainingIntersectNanos,
            long sidePatchEmitNanos,
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
                0L
        );

        public StageTimings {
            tileRangeNanos = Math.max(0L, tileRangeNanos);
            projectionRectNanos = Math.max(0L, projectionRectNanos);
            blockLookupNanos = Math.max(0L, blockLookupNanos);
            projectableCheckNanos = Math.max(0L, projectableCheckNanos);
            planeWindowNanos = Math.max(0L, planeWindowNanos);
            frontSubtractNanos = Math.max(0L, frontSubtractNanos);
            sideScanNanos = Math.max(0L, sideScanNanos);
            sideCandidateNanos = Math.max(0L, sideCandidateNanos);
            sideEmitNanos = Math.max(0L, sideEmitNanos);
            sideTravelSplitNanos = Math.max(0L, sideTravelSplitNanos);
            sideWindowNanos = Math.max(0L, sideWindowNanos);
            sideRemainingIntersectNanos = Math.max(0L, sideRemainingIntersectNanos);
            sidePatchEmitNanos = Math.max(0L, sidePatchEmitNanos);
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
