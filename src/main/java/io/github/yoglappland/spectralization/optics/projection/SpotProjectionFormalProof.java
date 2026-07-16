package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.LocalPoint;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.Patch;
import io.github.yoglappland.spectralization.optics.projection.SpotProjectionPatch.TexturePoint;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.Direction;

public final class SpotProjectionFormalProof {
    private static final double EPSILON = 1.0E-9D;
    private static final double[] RADII = {0.72D, 1.0D, 1.37D, 1.88D, 2.65D, 3.5D};
    private static final int TILE_LIMIT = 4;

    public static void main(String[] args) {
        ProofResult result = prove();

        if (!result.failures().isEmpty()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), result.failures()));
        }

        System.out.println(result.format());
    }

    static ProofResult prove() {
        List<String> failures = new ArrayList<>();
        Map<Direction, Integer> patchCounts = new EnumMap<>(Direction.class);
        int rawNegativePatches = 0;
        int checkedPatches = 0;

        for (Direction travelDirection : Direction.values()) {
            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);
            int directionCount = 0;

            for (double entryRadius : RADII) {
                for (double exitRadius : RADII) {
                    for (int tileV = -TILE_LIMIT; tileV <= TILE_LIMIT; tileV++) {
                        for (int tileU = -TILE_LIMIT; tileU <= TILE_LIMIT; tileU++) {
                            SideCheck left = checkUSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection.getOpposite(),
                                    tileU - 0.5D,
                                    0.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck right = checkUSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection,
                                    tileU + 0.5D,
                                    1.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck bottom = checkVSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection.getOpposite(),
                                    tileV - 0.5D,
                                    0.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                            SideCheck top = checkVSide(
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection,
                                    tileV + 0.5D,
                                    1.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );

                            SideCheck[] checks = {left, right, bottom, top};
                            for (SideCheck check : checks) {
                                if (!check.valid()) {
                                    continue;
                                }

                                checkedPatches++;
                                directionCount++;

                                if (check.rawDot() < -EPSILON) {
                                    rawNegativePatches++;
                                }

                                if (check.orientedDot() <= EPSILON) {
                                    failures.add(check.label() + " did not orient toward face; raw="
                                            + check.rawDot() + " oriented=" + check.orientedDot());
                                }

                                if (!check.onFace()) {
                                    failures.add(check.label() + " produced a patch not lying on its target face.");
                                }

                                if (!check.textureInUnitSquare()) {
                                    failures.add(check.label() + " produced texture coordinates outside [0, 1].");
                                }
                            }
                        }
                    }
                }
            }

            patchCounts.put(travelDirection, directionCount);
        }

        int firstCount = patchCounts.get(Direction.values()[0]);
        for (Direction direction : Direction.values()) {
            int count = patchCounts.get(direction);
            if (count != firstCount) {
                failures.add("direction symmetry failed: " + direction.getSerializedName()
                        + " count=" + count + " expected=" + firstCount);
            }
        }

        int clippedGeometryChecks = proveClippedSideGeometry(failures);
        int frontOrderChecks = proveFrontPlaneOrder(failures);
        int canonicalRegionChecks = 0;
        int sameDepthIndexChecks = 0;
        int cuboidSweepChecks = 0;
        int polygonSweepChecks = 0;
        int analyticSideTravelChecks = 0;
        int remainingBoundedScanChecks = 0;
        int snapshotCaptureChecks = 0;
        try {
            canonicalRegionChecks = VoxelSpotProjector.verifyCanonicalRegionSubtractSweep();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            sameDepthIndexChecks = VoxelSpotProjector.verifySameDepthOcclusionIndex();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            cuboidSweepChecks = VoxelSpotProjector.verifyCuboidSweeps();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            polygonSweepChecks = proveCanonicalPolygonSweep();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            analyticSideTravelChecks = VoxelSpotProjector.verifyAnalyticSideTravelIntervals();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            remainingBoundedScanChecks = VoxelSpotProjector.verifyRemainingBoundedTileScan();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }
        try {
            snapshotCaptureChecks = VoxelSpotProjector.verifySnapshotCaptureBounds();
            snapshotCaptureChecks += ProjectionSectionSnapshot.verifyAddressing();
            snapshotCaptureChecks += ProjectionSectionSnapshotCache.verifyCopyOnWrite();
        } catch (RuntimeException exception) {
            failures.add(exception.getMessage());
        }

        if (rawNegativePatches == 0) {
            failures.add("formal proof did not exercise any negative-winding side patch.");
        }

        if (clippedGeometryChecks == 0) {
            failures.add("formal proof did not exercise clipped side geometry.");
        }

        if (frontOrderChecks == 0) {
            failures.add("formal proof did not exercise same-depth front event ordering.");
        }

        if (analyticSideTravelChecks == 0) {
            failures.add("formal proof did not exercise analytic side travel intervals.");
        }

        if (remainingBoundedScanChecks == 0) {
            failures.add("formal proof did not exercise remaining-bounded tile scans.");
        }

        if (snapshotCaptureChecks == 0) {
            failures.add("formal proof did not exercise eager snapshot capture bounds.");
        }

        return new ProofResult(
                checkedPatches,
                rawNegativePatches,
                clippedGeometryChecks,
                frontOrderChecks,
                canonicalRegionChecks,
                sameDepthIndexChecks,
                cuboidSweepChecks,
                polygonSweepChecks,
                analyticSideTravelChecks,
                remainingBoundedScanChecks,
                snapshotCaptureChecks,
                Map.copyOf(patchCounts),
                failures
        );
    }

    private static int proveCanonicalPolygonSweep() {
        List<CanonicalPolygonOps.Point> endpoints = new ArrayList<>();
        addScaledRectangle(endpoints, 0.30D, 0.15D, 0.52D, 0.38D, 0.55D);
        addScaledRectangle(endpoints, 0.30D, 0.15D, 0.52D, 0.38D, 1.35D);
        CanonicalPolygonOps.Polygon sweep = CanonicalPolygonOps.convexHull(endpoints);
        if (sweep == null || !sweep.hasDiagonalEdge()) {
            throw new IllegalStateException("canonical polygon sweep failed to retain a diagonal boundary");
        }
        CanonicalPolygonOps.Bounds bounds = sweep.bounds();
        double boundsArea = (bounds.maxU() - bounds.minU()) * (bounds.maxV() - bounds.minV());
        if (boundsArea - sweep.area() <= 1.0E-6D) {
            throw new IllegalStateException("canonical polygon sweep collapsed to its axis-aligned bounds");
        }

        int checked = 0;
        for (int sample = 0; sample <= 128; sample++) {
            double scale = 0.55D + (1.35D - 0.55D) * sample / 128.0D;
            List<CanonicalPolygonOps.Point> crossSection = new ArrayList<>();
            addScaledRectangle(crossSection, 0.30D, 0.15D, 0.52D, 0.38D, scale);
            for (CanonicalPolygonOps.Point point : crossSection) {
                if (!sweep.contains(point)) {
                    throw new IllegalStateException("canonical polygon sweep omitted a continuous cross-section");
                }
                checked++;
            }
        }

        CanonicalPolygonOps.Region remaining = CanonicalPolygonOps.Region.full().subtract(sweep);
        CanonicalPolygonOps.Polygon clippedSweep = CanonicalPolygonOps.intersection(
                sweep,
                CanonicalPolygonOps.UNIT_SQUARE
        );
        double expectedArea = 1.0D - (clippedSweep == null ? 0.0D : clippedSweep.area());
        if (Math.abs(remaining.area() - expectedArea) > 1.0E-8D) {
            throw new IllegalStateException("canonical polygon subtraction failed area conservation");
        }
        checked += remaining.cellCount();

        Random hullWorkspaceRandom = new Random(0xC0B01D5EEDL);
        CanonicalPolygonOps.ConvexHullWorkspace reusedHullWorkspace =
                new CanonicalPolygonOps.ConvexHullWorkspace();
        for (int iteration = 0; iteration < 4096; iteration++) {
            List<CanonicalPolygonOps.Point> hullPoints = reusedHullWorkspace.resetInput();
            double minU = -0.8D + hullWorkspaceRandom.nextDouble() * 1.6D;
            double minV = -0.8D + hullWorkspaceRandom.nextDouble() * 1.6D;
            double maxU = minU + 0.02D + hullWorkspaceRandom.nextDouble() * 0.8D;
            double maxV = minV + 0.02D + hullWorkspaceRandom.nextDouble() * 0.8D;
            double scale0 = 0.25D + hullWorkspaceRandom.nextDouble() * 1.75D;
            addScaledRectangle(hullPoints, minU, minV, maxU, maxV, scale0);
            if (hullWorkspaceRandom.nextBoolean()) {
                double scale1 = 0.25D + hullWorkspaceRandom.nextDouble() * 1.75D;
                addScaledRectangle(hullPoints, minU, minV, maxU, maxV, scale1);
            }
            CanonicalPolygonOps.Polygon legacyHull = CanonicalPolygonOps.clippedConvexHull(hullPoints);
            CanonicalPolygonOps.Polygon reusedHull = CanonicalPolygonOps.clippedConvexHull(
                    hullPoints,
                    reusedHullWorkspace
            );
            if (legacyHull == null ? reusedHull != null : !legacyHull.equals(reusedHull)) {
                throw new IllegalStateException(
                        "reused convex-hull workspace changed clipped sweep output at iteration " + iteration
                );
            }
            checked++;
        }

        List<CanonicalPolygonOps.Polygon> indexedCells = new ArrayList<>();
        for (int v = 0; v < 4; v++) {
            for (int u = 0; u < 4; u++) {
                indexedCells.add(CanonicalPolygonOps.rectangle(
                        u * 0.25D,
                        v * 0.25D,
                        (u + 1) * 0.25D,
                        (v + 1) * 0.25D
                ));
            }
        }
        CanonicalPolygonOps.Region indexedRegion = CanonicalPolygonOps.Region.ofCells(indexedCells);
        CanonicalPolygonOps.Polygon indexQuery = CanonicalPolygonOps.rectangle(0.30D, 0.20D, 0.69D, 0.78D);
        CanonicalPolygonOps.IntersectionResult indexedResult = indexedRegion.intersectDetailed(indexQuery);
        List<CanonicalPolygonOps.Polygon> linearResult = new ArrayList<>();
        for (CanonicalPolygonOps.Polygon cell : indexedRegion.cells()) {
            CanonicalPolygonOps.Polygon clipped = CanonicalPolygonOps.intersection(cell, indexQuery);
            if (clipped != null) {
                linearResult.add(clipped);
            }
        }
        if (!indexedResult.queryStats().indexed() || !indexedResult.polygons().equals(linearResult)) {
            throw new IllegalStateException("canonical polygon cell index changed exact intersection output");
        }
        CanonicalPolygonOps.IntersectionResult rectangleResult = indexedRegion.intersectRectangleDetailed(
                0.30D, 0.20D, 0.69D, 0.78D
        );
        if (!rectangleResult.polygons().equals(indexedResult.polygons())) {
            throw new IllegalStateException("rectangle-specialized polygon intersection changed exact output");
        }
        checked += indexedResult.polygons().size();

        Random batchRandom = new Random(0xBA7C4EEDL);
        List<CanonicalPolygonOps.Bounds> batchRectangles = new ArrayList<>();
        List<List<CanonicalPolygonOps.Polygon>> scalarBatchReference = new ArrayList<>();
        for (int queryIndexValue = 0; queryIndexValue < 128; queryIndexValue++) {
            double minU = batchRandom.nextDouble() * 0.85D;
            double minV = batchRandom.nextDouble() * 0.85D;
            double maxU = Math.min(1.0D, minU + 0.03D + batchRandom.nextDouble() * 0.25D);
            double maxV = Math.min(1.0D, minV + 0.03D + batchRandom.nextDouble() * 0.25D);
            batchRectangles.add(new CanonicalPolygonOps.Bounds(minU, minV, maxU, maxV));
            CanonicalPolygonOps.Polygon rectanglePolygon =
                    CanonicalPolygonOps.rectangle(minU, minV, maxU, maxV);
            scalarBatchReference.add(indexedRegion.intersectDetailed(rectanglePolygon).polygons());
        }
        CanonicalPolygonOps.BatchIntersectionResult batchResult =
                indexedRegion.intersectRectanglesDetailed(batchRectangles);
        if (batchResult.queryStats().queries() != batchRectangles.size()
                || batchResult.queryStats().indexedQueries() != batchRectangles.size()
                || !batchResult.polygonsByRectangle().equals(scalarBatchReference)) {
            throw new IllegalStateException("batched rectangle intersection changed scalar query output");
        }
        checked += batchResult.visiblePolygons();

        CanonicalPolygonOps.QueryWorkspace reusableQueryWorkspace =
                new CanonicalPolygonOps.QueryWorkspace();
        List<CanonicalPolygonOps.Polygon> reusableRectangleOutput = new ArrayList<>();
        for (int queryIndexValue = 0; queryIndexValue < batchRectangles.size(); queryIndexValue++) {
            CanonicalPolygonOps.Bounds rectangle = batchRectangles.get(queryIndexValue);
            CanonicalPolygonOps.RectangleIntersectionStats reusableStats = indexedRegion.intersectRectangleInto(
                    rectangle.minU(),
                    rectangle.minV(),
                    rectangle.maxU(),
                    rectangle.maxV(),
                    reusableQueryWorkspace,
                    reusableRectangleOutput
            );
            if (!reusableStats.queryStats().indexed()
                    || !reusableRectangleOutput.equals(scalarBatchReference.get(queryIndexValue))) {
                throw new IllegalStateException("reused rectangle query workspace changed exact output");
            }
            CanonicalPolygonOps.Polygon rectanglePolygon = CanonicalPolygonOps.rectangle(
                    rectangle.minU(), rectangle.minV(), rectangle.maxU(), rectangle.maxV()
            );
            boolean scalarHit = indexedRegion.intersectsDetailed(rectanglePolygon).hit();
            boolean reusableHit = indexedRegion.intersectsDetailed(
                    rectanglePolygon, reusableQueryWorkspace
            ).hit();
            if (scalarHit != reusableHit) {
                throw new IllegalStateException("reused polygon query workspace changed intersection membership");
            }
            checked += 2;
        }

        Random rectangleWorkspaceRandom = new Random(0x5EC7A11EL);
        CanonicalPolygonOps.PolygonWorkspace rectangleWorkspace =
                new CanonicalPolygonOps.PolygonWorkspace();
        for (int iteration = 0; iteration < 4096; iteration++) {
            List<CanonicalPolygonOps.Point> subjectPoints = new ArrayList<>();
            double subjectMinU = -0.25D + rectangleWorkspaceRandom.nextDouble() * 1.1D;
            double subjectMinV = -0.25D + rectangleWorkspaceRandom.nextDouble() * 1.1D;
            double subjectMaxU = subjectMinU + 0.08D + rectangleWorkspaceRandom.nextDouble() * 0.65D;
            double subjectMaxV = subjectMinV + 0.08D + rectangleWorkspaceRandom.nextDouble() * 0.65D;
            addScaledRectangle(
                    subjectPoints,
                    subjectMinU,
                    subjectMinV,
                    subjectMaxU,
                    subjectMaxV,
                    0.65D + rectangleWorkspaceRandom.nextDouble() * 0.35D
            );
            addScaledRectangle(
                    subjectPoints,
                    subjectMinU,
                    subjectMinV,
                    subjectMaxU,
                    subjectMaxV,
                    1.0D + rectangleWorkspaceRandom.nextDouble() * 0.45D
            );
            CanonicalPolygonOps.Polygon subject =
                    CanonicalPolygonOps.clippedConvexHull(subjectPoints);
            if (subject == null) {
                continue;
            }

            double minU = rectangleWorkspaceRandom.nextDouble() * 0.9D;
            double minV = rectangleWorkspaceRandom.nextDouble() * 0.9D;
            double maxU = Math.min(1.0D, minU + 0.02D + rectangleWorkspaceRandom.nextDouble() * 0.4D);
            double maxV = Math.min(1.0D, minV + 0.02D + rectangleWorkspaceRandom.nextDouble() * 0.4D);
            CanonicalPolygonOps.Polygon clip =
                    CanonicalPolygonOps.rectangle(minU, minV, maxU, maxV);
            CanonicalPolygonOps.Polygon generic =
                    CanonicalPolygonOps.intersection(subject, clip);
            CanonicalPolygonOps.Polygon optimized = CanonicalPolygonOps.intersectionRectangle(
                    subject,
                    minU,
                    minV,
                    maxU,
                    maxV,
                    rectangleWorkspace
            );
            if (generic == null ? optimized != null : !generic.equals(optimized)) {
                throw new IllegalStateException(
                        "final-only rectangle clipping changed exact polygon output at iteration " + iteration
                );
            }
            checked++;
        }

        Random workspaceRandom = new Random(0xA110CA7EL);
        CanonicalPolygonOps.PolygonWorkspace reusedWorkspace = new CanonicalPolygonOps.PolygonWorkspace();
        for (int iteration = 0; iteration < 2048; iteration++) {
            List<CanonicalPolygonOps.Point> subjectPoints = new ArrayList<>();
            List<CanonicalPolygonOps.Point> clipPoints = new ArrayList<>();
            double subjectMinU = -0.15D + workspaceRandom.nextDouble() * 0.8D;
            double subjectMinV = -0.15D + workspaceRandom.nextDouble() * 0.8D;
            double clipMinU = -0.15D + workspaceRandom.nextDouble() * 0.9D;
            double clipMinV = -0.15D + workspaceRandom.nextDouble() * 0.9D;
            addScaledRectangle(
                    subjectPoints,
                    subjectMinU,
                    subjectMinV,
                    subjectMinU + 0.12D + workspaceRandom.nextDouble() * 0.3D,
                    subjectMinV + 0.12D + workspaceRandom.nextDouble() * 0.3D,
                    0.7D
            );
            addScaledRectangle(
                    subjectPoints,
                    subjectMinU,
                    subjectMinV,
                    subjectMinU + 0.12D + workspaceRandom.nextDouble() * 0.3D,
                    subjectMinV + 0.12D + workspaceRandom.nextDouble() * 0.3D,
                    1.25D
            );
            addScaledRectangle(
                    clipPoints,
                    clipMinU,
                    clipMinV,
                    clipMinU + 0.1D + workspaceRandom.nextDouble() * 0.35D,
                    clipMinV + 0.1D + workspaceRandom.nextDouble() * 0.35D,
                    0.75D
            );
            addScaledRectangle(
                    clipPoints,
                    clipMinU,
                    clipMinV,
                    clipMinU + 0.1D + workspaceRandom.nextDouble() * 0.35D,
                    clipMinV + 0.1D + workspaceRandom.nextDouble() * 0.35D,
                    1.2D
            );
            CanonicalPolygonOps.Polygon subject = CanonicalPolygonOps.clippedConvexHull(subjectPoints);
            CanonicalPolygonOps.Polygon clipPolygon = CanonicalPolygonOps.clippedConvexHull(clipPoints);
            if (subject == null || clipPolygon == null) {
                continue;
            }
            List<CanonicalPolygonOps.Polygon> freshSubtract = CanonicalPolygonOps.subtract(subject, clipPolygon);
            List<CanonicalPolygonOps.Polygon> reusedSubtract = new ArrayList<>();
            CanonicalPolygonOps.subtractInto(subject, clipPolygon, reusedWorkspace, reusedSubtract);
            if (!freshSubtract.equals(reusedSubtract)) {
                throw new IllegalStateException("reused polygon subtraction workspace changed fragment output");
            }
            CanonicalPolygonOps.Polygon freshIntersection = CanonicalPolygonOps.intersection(subject, clipPolygon);
            CanonicalPolygonOps.Polygon reusedIntersection =
                    CanonicalPolygonOps.intersection(subject, clipPolygon, reusedWorkspace);
            if (freshIntersection == null ? reusedIntersection != null : !freshIntersection.equals(reusedIntersection)) {
                throw new IllegalStateException("reused polygon clipping workspace changed intersection output");
            }
            checked += freshSubtract.size() + (freshIntersection == null ? 0 : 1);
        }

        Random bulkRandom = new Random(0xB10C5EEDL);
        List<CanonicalPolygonOps.Polygon> bulkBlockers = new ArrayList<>();
        for (int blockerIndex = 0; blockerIndex < 24; blockerIndex++) {
            double minU = -0.1D + bulkRandom.nextDouble() * 0.95D;
            double minV = -0.1D + bulkRandom.nextDouble() * 0.95D;
            double maxU = minU + 0.08D + bulkRandom.nextDouble() * 0.24D;
            double maxV = minV + 0.08D + bulkRandom.nextDouble() * 0.24D;
            List<CanonicalPolygonOps.Point> blockerPoints = new ArrayList<>();
            addScaledRectangle(blockerPoints, minU, minV, maxU, maxV, 0.75D);
            addScaledRectangle(blockerPoints, minU, minV, maxU, maxV, 1.25D);
            CanonicalPolygonOps.Polygon blocker = CanonicalPolygonOps.clippedConvexHull(blockerPoints);
            if (blocker != null) {
                bulkBlockers.add(blocker);
            }
        }
        CanonicalPolygonOps.Region sequentialDifference = indexedRegion.subtractAll(bulkBlockers);
        CanonicalPolygonOps.BulkSubtractionResult bulkDifference = indexedRegion.subtractIndexed(bulkBlockers);
        CanonicalPolygonOps.BulkSubtractionResult compactedBulkDifference =
                indexedRegion.subtractIndexedCompacted(bulkBlockers);
        if (!bulkDifference.indexed()
                || Math.abs(sequentialDifference.area() - bulkDifference.region().area()) > 1.0E-8D) {
            throw new IllegalStateException("indexed bulk polygon subtraction changed remaining area");
        }
        if (Math.abs(bulkDifference.region().area() - compactedBulkDifference.region().area()) > 1.0E-8D
                || compactedBulkDifference.compactionCellsBefore() != bulkDifference.region().cellCount()
                || compactedBulkDifference.compactionCellsAfter() > compactedBulkDifference.compactionCellsBefore()) {
            throw new IllegalStateException("integrated bulk compaction changed remaining coverage or cell accounting");
        }
        for (int probeIndex = 0; probeIndex < 64; probeIndex++) {
            double minU = bulkRandom.nextDouble() * 0.8D;
            double minV = bulkRandom.nextDouble() * 0.8D;
            CanonicalPolygonOps.Polygon probe = CanonicalPolygonOps.rectangle(
                    minU,
                    minV,
                    Math.min(1.0D, minU + 0.05D + bulkRandom.nextDouble() * 0.2D),
                    Math.min(1.0D, minV + 0.05D + bulkRandom.nextDouble() * 0.2D)
            );
            double sequentialArea = intersectionArea(sequentialDifference, probe);
            double bulkArea = intersectionArea(bulkDifference.region(), probe);
            double compactedBulkArea = intersectionArea(compactedBulkDifference.region(), probe);
            if (Math.abs(sequentialArea - bulkArea) > 1.0E-8D
                    || Math.abs(bulkArea - compactedBulkArea) > 1.0E-8D) {
                throw new IllegalStateException("indexed bulk polygon subtraction changed probe coverage");
            }
            checked++;
        }
        assertDisjointCells(bulkDifference.region(), "indexed bulk polygon subtraction");
        assertDisjointCells(compactedBulkDifference.region(), "integrated compacted bulk polygon subtraction");

        CanonicalPolygonOps.Region splitSquare = CanonicalPolygonOps.Region.ofCells(List.of(
                CanonicalPolygonOps.polygon(List.of(
                        new CanonicalPolygonOps.Point(0.0D, 0.0D),
                        new CanonicalPolygonOps.Point(1.0D, 0.0D),
                        new CanonicalPolygonOps.Point(1.0D, 1.0D)
                )),
                CanonicalPolygonOps.polygon(List.of(
                        new CanonicalPolygonOps.Point(0.0D, 0.0D),
                        new CanonicalPolygonOps.Point(1.0D, 1.0D),
                        new CanonicalPolygonOps.Point(0.0D, 1.0D)
                ))
        ));
        CanonicalPolygonOps.CompactionResult compactSquare = splitSquare.compactConvexCells();
        if (compactSquare.merges() != 1
                || compactSquare.region().cellCount() != 1
                || Math.abs(compactSquare.region().area() - 1.0D) > 1.0E-8D) {
            throw new IllegalStateException("canonical polygon convex-cell compaction changed square coverage");
        }
        checked++;

        CanonicalPolygonOps.Region gridSquare = CanonicalPolygonOps.Region.ofCells(List.of(
                CanonicalPolygonOps.rectangle(0.0D, 0.0D, 0.5D, 0.5D),
                CanonicalPolygonOps.rectangle(0.5D, 0.0D, 1.0D, 0.5D),
                CanonicalPolygonOps.rectangle(0.0D, 0.5D, 0.5D, 1.0D),
                CanonicalPolygonOps.rectangle(0.5D, 0.5D, 1.0D, 1.0D)
        ));
        CanonicalPolygonOps.CompactionResult compactGridSquare = gridSquare.compactConvexCells();
        if (compactGridSquare.merges() != 3
                || compactGridSquare.region().cellCount() != 1
                || Math.abs(compactGridSquare.region().area() - 1.0D) > 1.0E-8D) {
            throw new IllegalStateException("incremental polygon compaction did not finish a merge cascade");
        }
        checked++;

        Random random = new Random(0xD1A60A1L);
        for (int iteration = 0; iteration < 256; iteration++) {
            CanonicalPolygonOps.Region region = CanonicalPolygonOps.Region.full();
            for (int blockerIndex = 0; blockerIndex < 4; blockerIndex++) {
                double minU = -0.25D + random.nextDouble() * 1.0D;
                double minV = -0.25D + random.nextDouble() * 1.0D;
                double maxU = minU + 0.1D + random.nextDouble() * 0.45D;
                double maxV = minV + 0.1D + random.nextDouble() * 0.45D;
                double scale0 = 0.45D + random.nextDouble() * 0.5D;
                double scale1 = scale0 + 0.1D + random.nextDouble() * 0.8D;
                List<CanonicalPolygonOps.Point> blockerPoints = new ArrayList<>();
                addScaledRectangle(blockerPoints, minU, minV, maxU, maxV, scale0);
                addScaledRectangle(blockerPoints, minU, minV, maxU, maxV, scale1);
                CanonicalPolygonOps.Polygon blocker = CanonicalPolygonOps.clippedConvexHull(blockerPoints);
                if (blocker == null) {
                    continue;
                }

                double overlapArea = 0.0D;
                for (CanonicalPolygonOps.Polygon overlap : region.intersect(blocker)) {
                    overlapArea += overlap.area();
                }
                double before = region.area();
                CanonicalPolygonOps.Region after = region.subtract(blocker);
                if (Math.abs((before - overlapArea) - after.area()) > 1.0E-8D) {
                    throw new IllegalStateException("canonical polygon random subtraction lost area");
                }
                CanonicalPolygonOps.CompactionResult compaction = after.compactConvexCells();
                if (compaction.cellsAfter() > compaction.cellsBefore()
                        || Math.abs(compaction.region().area() - after.area()) > 1.0E-8D) {
                    throw new IllegalStateException("canonical polygon random compaction changed area");
                }
                CanonicalPolygonOps.Polygon coverageProbe = CanonicalPolygonOps.rectangle(
                        random.nextDouble() * 0.65D,
                        random.nextDouble() * 0.65D,
                        0.35D + random.nextDouble() * 0.65D,
                        0.35D + random.nextDouble() * 0.65D
                );
                if (coverageProbe != null) {
                    double beforeProbeArea = intersectionArea(after, coverageProbe);
                    double afterProbeArea = intersectionArea(compaction.region(), coverageProbe);
                    if (Math.abs(beforeProbeArea - afterProbeArea) > 1.0E-8D) {
                        throw new IllegalStateException("canonical polygon random compaction changed query coverage");
                    }
                }
                after = compaction.region();
                assertDisjointCells(after, "canonical polygon subtraction");
                region = after;
                checked++;
            }
        }
        return checked;
    }

    private static double intersectionArea(
            CanonicalPolygonOps.Region region,
            CanonicalPolygonOps.Polygon polygon
    ) {
        double area = 0.0D;
        for (CanonicalPolygonOps.Polygon clipped : region.intersect(polygon)) {
            area += clipped.area();
        }
        return area;
    }

    private static void assertDisjointCells(CanonicalPolygonOps.Region region, String label) {
        List<CanonicalPolygonOps.Polygon> cells = region.cells();
        for (int first = 0; first < cells.size(); first++) {
            for (int second = first + 1; second < cells.size(); second++) {
                CanonicalPolygonOps.Polygon overlap = CanonicalPolygonOps.intersection(
                        cells.get(first), cells.get(second)
                );
                if (overlap != null && overlap.area() > 1.0E-8D) {
                    throw new IllegalStateException(label + " produced overlapping cells");
                }
            }
        }
    }

    private static void addScaledRectangle(
            List<CanonicalPolygonOps.Point> output,
            double minU,
            double minV,
            double maxU,
            double maxV,
            double scale
    ) {
        output.add(new CanonicalPolygonOps.Point(0.5D + (minU - 0.5D) * scale, 0.5D + (minV - 0.5D) * scale));
        output.add(new CanonicalPolygonOps.Point(0.5D + (maxU - 0.5D) * scale, 0.5D + (minV - 0.5D) * scale));
        output.add(new CanonicalPolygonOps.Point(0.5D + (maxU - 0.5D) * scale, 0.5D + (maxV - 0.5D) * scale));
        output.add(new CanonicalPolygonOps.Point(0.5D + (minU - 0.5D) * scale, 0.5D + (maxV - 0.5D) * scale));
    }

    private static int proveFrontPlaneOrder(List<String> failures) {
        int checked = 0;
        checked += expectFrontPlanePhase(
                failures,
                "strictly earlier sampled plane",
                0.25D,
                0.25D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.BEFORE
        );
        checked += expectFrontPlanePhase(
                failures,
                "continuing volume coplanar plane",
                0.5D,
                0.0D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.CONTINUING
        );
        checked += expectFrontPlanePhase(
                failures,
                "new volume front plane",
                0.5D,
                0.5D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.STARTING
        );
        checked += expectFrontPlanePhase(
                failures,
                "later sampled plane",
                0.75D,
                0.0D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.AFTER
        );
        checked += expectFrontPlanePhase(
                failures,
                "epsilon-coplanar continuing plane",
                0.5D + 5.0E-8D,
                0.0D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.CONTINUING
        );
        checked += expectFrontPlanePhase(
                failures,
                "epsilon-coplanar starting plane",
                0.5D - 5.0E-8D,
                0.5D - 5.0E-8D,
                0.5D,
                VoxelSpotProjector.FrontPlanePhase.STARTING
        );
        return checked;
    }

    private static int expectFrontPlanePhase(
            List<String> failures,
            String label,
            double planeTravel,
            double volumeStartTravel,
            double frontGroupTravel,
            VoxelSpotProjector.FrontPlanePhase expected
    ) {
        VoxelSpotProjector.FrontPlanePhase actual = VoxelSpotProjector.classifyFrontPlaneEvent(
                planeTravel,
                volumeStartTravel,
                frontGroupTravel
        );
        if (actual != expected) {
            failures.add("front event order failed for " + label
                    + ": expected=" + expected + " actual=" + actual);
        }
        return 1;
    }

    private static int proveClippedSideGeometry(List<String> failures) {
        int checked = 0;

        for (Direction travelDirection : Direction.values()) {
            Direction displayFace = travelDirection.getOpposite();
            Direction uDirection = SpotSurfaceFrame.uDirection(displayFace);
            Direction vDirection = SpotSurfaceFrame.vDirection(displayFace);

            for (double entryRadius : RADII) {
                for (double exitRadius : RADII) {
                    for (int tileV = -TILE_LIMIT; tileV <= TILE_LIMIT; tileV++) {
                        for (int tileU = -TILE_LIMIT; tileU <= TILE_LIMIT; tileU++) {
                            checked += proveClippedUSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection.getOpposite(),
                                    tileU - 0.5D,
                                    0.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedUSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    uDirection,
                                    tileU + 0.5D,
                                    1.0D,
                                    tileV,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedVSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection.getOpposite(),
                                    tileV - 0.5D,
                                    0.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                            checked += proveClippedVSideGeometry(
                                    failures,
                                    travelDirection,
                                    uDirection,
                                    vDirection,
                                    vDirection,
                                    tileV + 0.5D,
                                    1.0D,
                                    tileU,
                                    entryRadius,
                                    exitRadius
                            );
                        }
                    }
                }
            }
        }

        return checked;
    }

    private static int proveClippedUSideGeometry(
            List<String> failures,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            double entryRadius,
            double exitRadius
    ) {
        VoxelSpotProjector.SideCrossSection entry = VoxelSpotProjector.uSideCrossSection(entryRadius, boundaryWorldU, tileV);
        VoxelSpotProjector.SideCrossSection exit = VoxelSpotProjector.uSideCrossSection(exitRadius, boundaryWorldU, tileV);

        if (entry == null || exit == null) {
            return 0;
        }

        VoxelSpotProjector.CanonicalRect fullWindow = VoxelSpotProjector.sideCanonicalWindow(
                entry.axisCanonical(),
                entry.axisCanonical(),
                exit.axisCanonical(),
                exit.axisCanonical(),
                entry.minCanonical(),
                entry.maxCanonical(),
                exit.minCanonical(),
                exit.maxCanonical()
        );

        if (fullWindow == null) {
            return 0;
        }

        Patch full = VoxelSpotProjector.clippedUSidePatch(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedULocal,
                0.0D,
                1.0D,
                entry,
                exit,
                fullWindow
        );

        if (full == null) {
            failures.add("u-side clipped proof could not construct a full patch.");
            return 0;
        }

        int checked = 0;
        for (VoxelSpotProjector.CanonicalRect clippedWindow : clippedTestWindows(fullWindow)) {
            Patch clipped = VoxelSpotProjector.clippedUSidePatch(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedULocal,
                    0.0D,
                    1.0D,
                    entry,
                    exit,
                    clippedWindow
            );
            checked += checkClippedPatch(failures, "u-side/" + travelDirection.getSerializedName(), full, clipped, clippedWindow);
        }

        return checked;
    }

    private static int proveClippedVSideGeometry(
            List<String> failures,
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            double entryRadius,
            double exitRadius
    ) {
        VoxelSpotProjector.SideCrossSection entry = VoxelSpotProjector.vSideCrossSection(entryRadius, boundaryWorldV, tileU);
        VoxelSpotProjector.SideCrossSection exit = VoxelSpotProjector.vSideCrossSection(exitRadius, boundaryWorldV, tileU);

        if (entry == null || exit == null) {
            return 0;
        }

        VoxelSpotProjector.CanonicalRect fullWindow = VoxelSpotProjector.sideCanonicalWindow(
                entry.minCanonical(),
                entry.maxCanonical(),
                exit.minCanonical(),
                exit.maxCanonical(),
                entry.axisCanonical(),
                entry.axisCanonical(),
                exit.axisCanonical(),
                exit.axisCanonical()
        );

        if (fullWindow == null) {
            return 0;
        }

        Patch full = VoxelSpotProjector.clippedVSidePatch(
                sideFace,
                travelDirection,
                uDirection,
                vDirection,
                fixedVLocal,
                0.0D,
                1.0D,
                entry,
                exit,
                fullWindow
        );

        if (full == null) {
            failures.add("v-side clipped proof could not construct a full patch.");
            return 0;
        }

        int checked = 0;
        for (VoxelSpotProjector.CanonicalRect clippedWindow : clippedTestWindows(fullWindow)) {
            Patch clipped = VoxelSpotProjector.clippedVSidePatch(
                    sideFace,
                    travelDirection,
                    uDirection,
                    vDirection,
                    fixedVLocal,
                    0.0D,
                    1.0D,
                    entry,
                    exit,
                    clippedWindow
            );
            checked += checkClippedPatch(failures, "v-side/" + travelDirection.getSerializedName(), full, clipped, clippedWindow);
        }

        return checked;
    }

    private static List<VoxelSpotProjector.CanonicalRect> clippedTestWindows(VoxelSpotProjector.CanonicalRect fullWindow) {
        double midU = (fullWindow.minU() + fullWindow.maxU()) * 0.5D;
        double midV = (fullWindow.minV() + fullWindow.maxV()) * 0.5D;
        List<VoxelSpotProjector.CanonicalRect> windows = new ArrayList<>(4);
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), fullWindow.minV(), midU, fullWindow.maxV()));
        windows.add(new VoxelSpotProjector.CanonicalRect(midU, fullWindow.minV(), fullWindow.maxU(), fullWindow.maxV()));
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), fullWindow.minV(), fullWindow.maxU(), midV));
        windows.add(new VoxelSpotProjector.CanonicalRect(fullWindow.minU(), midV, fullWindow.maxU(), fullWindow.maxV()));
        return windows;
    }

    private static int checkClippedPatch(
            List<String> failures,
            String label,
            Patch full,
            Patch clipped,
            VoxelSpotProjector.CanonicalRect clippedWindow
    ) {
        if (clipped == null) {
            return 0;
        }

        double fullWorldArea = worldArea(full);
        double clippedWorldArea = worldArea(clipped);
        double fullTextureArea = textureArea(full);
        double clippedTextureArea = textureArea(clipped);
        double clippedWindowArea = (clippedWindow.maxU() - clippedWindow.minU())
                * (clippedWindow.maxV() - clippedWindow.minV());

        if (clippedWorldArea <= EPSILON || clippedTextureArea <= EPSILON) {
            failures.add(label + " clipped patch has no positive area.");
        }

        if (clippedWorldArea >= fullWorldArea - EPSILON) {
            failures.add(label + " clipped patch still occupies the full world side area.");
        }

        if (clippedTextureArea >= fullTextureArea - EPSILON) {
            failures.add(label + " clipped patch still occupies the full texture area.");
        }

        if (clippedTextureArea > clippedWindowArea + EPSILON) {
            failures.add(label + " clipped patch texture area exceeds its canonical clipping window.");
        }

        return 1;
    }

    private static SideCheck checkUSide(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldU,
            double fixedULocal,
            int tileV,
            double entryRadius,
            double exitRadius
    ) {
        CrossSection entry = uSideCrossSection(entryRadius, boundaryWorldU, tileV);
        CrossSection exit = uSideCrossSection(exitRadius, boundaryWorldU, tileV);

        if (entry == null || exit == null) {
            return SideCheck.invalid();
        }

        LocalPoint p0 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, fixedULocal, entry.minLocal());
        LocalPoint p1 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, fixedULocal, entry.maxLocal());
        LocalPoint p2 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, fixedULocal, exit.maxLocal());
        LocalPoint p3 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, fixedULocal, exit.minLocal());
        TexturePoint t0 = new TexturePoint(entry.axisCanonical(), renderTextureV(entry.minCanonical()));
        TexturePoint t1 = new TexturePoint(entry.axisCanonical(), renderTextureV(entry.maxCanonical()));
        TexturePoint t2 = new TexturePoint(exit.axisCanonical(), renderTextureV(exit.maxCanonical()));
        TexturePoint t3 = new TexturePoint(exit.axisCanonical(), renderTextureV(exit.minCanonical()));
        return checkPatch("u-side/" + travelDirection.getSerializedName() + "/" + sideFace.getSerializedName(),
                sideFace, p0, t0, p1, t1, p2, t2, p3, t3);
    }

    private static SideCheck checkVSide(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            Direction sideFace,
            double boundaryWorldV,
            double fixedVLocal,
            int tileU,
            double entryRadius,
            double exitRadius
    ) {
        CrossSection entry = vSideCrossSection(entryRadius, boundaryWorldV, tileU);
        CrossSection exit = vSideCrossSection(exitRadius, boundaryWorldV, tileU);

        if (entry == null || exit == null) {
            return SideCheck.invalid();
        }

        LocalPoint p0 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, entry.minLocal(), fixedVLocal);
        LocalPoint p1 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 0.0D, entry.maxLocal(), fixedVLocal);
        LocalPoint p2 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, exit.maxLocal(), fixedVLocal);
        LocalPoint p3 = SpotProjectionPatch.localPoint(travelDirection, uDirection, vDirection, 1.0D, exit.minLocal(), fixedVLocal);
        TexturePoint t0 = new TexturePoint(entry.minCanonical(), renderTextureV(entry.axisCanonical()));
        TexturePoint t1 = new TexturePoint(entry.maxCanonical(), renderTextureV(entry.axisCanonical()));
        TexturePoint t2 = new TexturePoint(exit.maxCanonical(), renderTextureV(exit.axisCanonical()));
        TexturePoint t3 = new TexturePoint(exit.minCanonical(), renderTextureV(exit.axisCanonical()));
        return checkPatch("v-side/" + travelDirection.getSerializedName() + "/" + sideFace.getSerializedName(),
                sideFace, p0, t0, p1, t1, p2, t2, p3, t3);
    }

    private static SideCheck checkPatch(
            String label,
            Direction sideFace,
            LocalPoint p0,
            TexturePoint t0,
            LocalPoint p1,
            TexturePoint t1,
            LocalPoint p2,
            TexturePoint t2,
            LocalPoint p3,
            TexturePoint t3
    ) {
        double rawDot = SpotProjectionPatch.faceNormalDot(sideFace, p0, p1, p2, p3);
        Patch patch = SpotProjectionPatch.oriented(sideFace, p0, t0, p1, t1, p2, t2, p3, t3);

        if (patch == null) {
            return new SideCheck(label, true, rawDot, Double.NaN, false, false);
        }

        double orientedDot = SpotProjectionPatch.faceNormalDot(sideFace, patch.p0(), patch.p1(), patch.p2(), patch.p3());
        boolean onFace = SpotProjectionPatch.liesOnFace(sideFace, patch.p0(), patch.p1(), patch.p2(), patch.p3());
        boolean textureInUnitSquare = textureInUnitSquare(patch.t0())
                && textureInUnitSquare(patch.t1())
                && textureInUnitSquare(patch.t2())
                && textureInUnitSquare(patch.t3());
        return new SideCheck(label, true, rawDot, orientedDot, onFace, textureInUnitSquare);
    }

    private static CrossSection uSideCrossSection(double radius, double boundaryWorldU, int tileV) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldU) > radius) {
            return null;
        }

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldU);
        double tileMinV = tileV - 0.5D;
        double tileMaxV = tileV + 0.5D;
        double hitMinV = Math.max(tileMinV, -perpendicularLimit);
        double hitMaxV = Math.min(tileMaxV, perpendicularLimit);

        if (hitMaxV - hitMinV <= EPSILON) {
            return null;
        }

        return new CrossSection(
                clamp01(hitMinV - tileMinV),
                clamp01(hitMaxV - tileMinV),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxV))
        );
    }

    private static CrossSection vSideCrossSection(double radius, double boundaryWorldV, int tileU) {
        if (!Double.isFinite(radius) || radius <= 0.0D || Math.abs(boundaryWorldV) > radius) {
            return null;
        }

        double perpendicularLimit = perpendicularLimit(radius, boundaryWorldV);
        double tileMinU = tileU - 0.5D;
        double tileMaxU = tileU + 0.5D;
        double hitMinU = Math.max(tileMinU, -perpendicularLimit);
        double hitMaxU = Math.min(tileMaxU, perpendicularLimit);

        if (hitMaxU - hitMinU <= EPSILON) {
            return null;
        }

        return new CrossSection(
                clamp01(hitMinU - tileMinU),
                clamp01(hitMaxU - tileMinU),
                clamp01(canonicalAtWorldCoordinate(radius, boundaryWorldV)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMinU)),
                clamp01(canonicalAtWorldCoordinate(radius, hitMaxU))
        );
    }

    private static double perpendicularLimit(double radius, double fixedCoordinate) {
        double remaining = radius * radius - fixedCoordinate * fixedCoordinate;
        return remaining <= 0.0D ? 0.0D : Math.sqrt(remaining);
    }

    private static double canonicalAtWorldCoordinate(double radius, double worldCoordinate) {
        return (worldCoordinate + radius) / (radius * 2.0D);
    }

    private static double renderTextureV(double canonicalV) {
        return 1.0D - clamp01(canonicalV);
    }

    private static boolean textureInUnitSquare(TexturePoint texturePoint) {
        return texturePoint.u() >= 0.0D
                && texturePoint.u() <= 1.0D
                && texturePoint.v() >= 0.0D
                && texturePoint.v() <= 1.0D;
    }

    private static double worldArea(Patch patch) {
        return triangleArea(patch.p0(), patch.p1(), patch.p2())
                + triangleArea(patch.p0(), patch.p2(), patch.p3());
    }

    private static double textureArea(Patch patch) {
        return Math.abs(
                patch.t0().u() * patch.t1().v() - patch.t1().u() * patch.t0().v()
                        + patch.t1().u() * patch.t2().v() - patch.t2().u() * patch.t1().v()
                        + patch.t2().u() * patch.t3().v() - patch.t3().u() * patch.t2().v()
                        + patch.t3().u() * patch.t0().v() - patch.t0().u() * patch.t3().v()
        ) * 0.5D;
    }

    private static double triangleArea(LocalPoint a, LocalPoint b, LocalPoint c) {
        double abX = b.x() - a.x();
        double abY = b.y() - a.y();
        double abZ = b.z() - a.z();
        double acX = c.x() - a.x();
        double acY = c.y() - a.y();
        double acZ = c.z() - a.z();
        double crossX = abY * acZ - abZ * acY;
        double crossY = abZ * acX - abX * acZ;
        double crossZ = abX * acY - abY * acX;
        return Math.sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * 0.5D;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private record CrossSection(
            double minLocal,
            double maxLocal,
            double axisCanonical,
            double minCanonical,
            double maxCanonical
    ) {
    }

    private record SideCheck(
            String label,
            boolean valid,
            double rawDot,
            double orientedDot,
            boolean onFace,
            boolean textureInUnitSquare
    ) {
        private static SideCheck invalid() {
            return new SideCheck("", false, 0.0D, 0.0D, true, true);
        }
    }

    record ProofResult(
            int checkedPatches,
            int rawNegativePatches,
            int clippedGeometryChecks,
            int frontOrderChecks,
            int canonicalRegionChecks,
            int sameDepthIndexChecks,
            int cuboidSweepChecks,
            int polygonSweepChecks,
            int analyticSideTravelChecks,
            int remainingBoundedScanChecks,
            int snapshotCaptureChecks,
            Map<Direction, Integer> patchCounts,
            List<String> failures
    ) {
        ProofResult {
            patchCounts = Map.copyOf(patchCounts);
            failures = List.copyOf(failures);
        }

        String format() {
            return String.format(
                    Locale.ROOT,
                    "spot_projection_formal_proof checked=%d negative_winding=%d clipped_geometry=%d front_order=%d canonical_region=%d same_depth_index=%d cuboid_sweep=%d polygon_sweep=%d analytic_side_travel=%d remaining_scan=%d snapshot_capture=%d counts=%s",
                    checkedPatches,
                    rawNegativePatches,
                    clippedGeometryChecks,
                    frontOrderChecks,
                    canonicalRegionChecks,
                    sameDepthIndexChecks,
                    cuboidSweepChecks,
                    polygonSweepChecks,
                    analyticSideTravelChecks,
                    remainingBoundedScanChecks,
                    snapshotCaptureChecks,
                    patchCounts
            );
        }
    }

    private SpotProjectionFormalProof() {
    }
}
