package io.github.yoglappland.spectralization.optics.projection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small deterministic convex-polygon kernel for canonical spot-texture geometry.
 * Regions are stored as disjoint convex cells. It is deliberately package-private:
 * projection geometry owns this representation and no gameplay system may consume it.
 */
final class CanonicalPolygonOps {
    static final double EPSILON = 1.0E-9D;
    private static final int MIN_INDEXED_CELLS = 8;
    private static final int MIN_INDEX_BINS = 4;
    private static final int MAX_INDEX_BINS = 16;
    private static final double MERGE_AREA_EPSILON = 1.0E-10D;
    private static final Comparator<Point> POINT_ORDER =
            Comparator.comparingDouble(Point::u).thenComparingDouble(Point::v);
    static final Polygon UNIT_SQUARE = rectangle(0.0D, 0.0D, 1.0D, 1.0D);

    static Polygon rectangle(double minU, double minV, double maxU, double maxV) {
        if (!finite(minU, minV, maxU, maxV)
                || maxU - minU <= EPSILON
                || maxV - minV <= EPSILON) {
            return null;
        }
        return Polygon.create(List.of(
                new Point(minU, minV),
                new Point(maxU, minV),
                new Point(maxU, maxV),
                new Point(minU, maxV)
        ));
    }

    static Polygon clippedConvexHull(List<Point> points) {
        Polygon hull = convexHull(points);
        return hull == null ? null : intersection(hull, UNIT_SQUARE);
    }

    static Polygon polygon(List<Point> points) {
        return Polygon.create(points);
    }

    static Polygon convexHull(List<Point> points) {
        Objects.requireNonNull(points, "points");
        List<Point> sorted = new ArrayList<>(points.size());
        for (Point point : points) {
            if (point != null && Double.isFinite(point.u()) && Double.isFinite(point.v())) {
                sorted.add(point);
            }
        }
        sorted.sort(POINT_ORDER);
        if (sorted.size() < 3) {
            return null;
        }

        List<Point> unique = new ArrayList<>(sorted.size());
        for (Point point : sorted) {
            if (unique.isEmpty() || !samePoint(unique.get(unique.size() - 1), point)) {
                unique.add(point);
            }
        }
        if (unique.size() < 3) {
            return null;
        }

        List<Point> lower = new ArrayList<>(unique.size());
        for (Point point : unique) {
            while (lower.size() >= 2
                    && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= EPSILON) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<Point> upper = new ArrayList<>(unique.size());
        for (int index = unique.size() - 1; index >= 0; index--) {
            Point point = unique.get(index);
            while (upper.size() >= 2
                    && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= EPSILON) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return Polygon.create(lower);
    }

    static Polygon intersection(Polygon subject, Polygon clip) {
        if (subject == null || clip == null || !subject.bounds().intersects(clip.bounds())) {
            return null;
        }
        Polygon current = subject;
        List<Point> clipPoints = clip.vertices();
        for (int index = 0; index < clipPoints.size() && current != null; index++) {
            Point a = clipPoints.get(index);
            Point b = clipPoints.get((index + 1) % clipPoints.size());
            current = clipHalfPlane(current, a, b, true);
        }
        return current;
    }

    static List<Polygon> subtract(Polygon subject, Polygon clip) {
        if (subject == null) {
            return List.of();
        }
        if (clip == null || !subject.bounds().intersects(clip.bounds())) {
            return List.of(subject);
        }

        Polygon inside = subject;
        List<Polygon> outside = new ArrayList<>();
        List<Point> clipPoints = clip.vertices();
        for (int index = 0; index < clipPoints.size() && inside != null; index++) {
            Point a = clipPoints.get(index);
            Point b = clipPoints.get((index + 1) % clipPoints.size());
            HalfPlaneSplit split = splitHalfPlane(inside, a, b);
            if (split.right() != null) {
                outside.add(split.right());
            }
            inside = split.left();
        }
        return outside.isEmpty() ? List.of() : List.copyOf(outside);
    }

    private static HalfPlaneSplit splitHalfPlane(Polygon polygon, Point a, Point b) {
        List<Point> input = polygon.vertices();
        List<Point> leftOutput = new ArrayList<>(input.size() + 2);
        List<Point> rightOutput = new ArrayList<>(input.size() + 2);
        Point previous = input.get(input.size() - 1);
        double previousSide = cross(a, b, previous);
        boolean previousLeft = halfPlaneContains(previousSide, true);
        boolean previousRight = halfPlaneContains(previousSide, false);

        for (Point current : input) {
            double currentSide = cross(a, b, current);
            boolean currentLeft = halfPlaneContains(currentSide, true);
            boolean currentRight = halfPlaneContains(currentSide, false);
            Point crossing = null;
            if (currentLeft != previousLeft || currentRight != previousRight) {
                crossing = lineIntersection(previous, current, previousSide, currentSide);
            }
            if (currentLeft != previousLeft && crossing != null) {
                appendDistinct(leftOutput, crossing);
            }
            if (currentLeft) {
                appendDistinct(leftOutput, current);
            }
            if (currentRight != previousRight && crossing != null) {
                appendDistinct(rightOutput, crossing);
            }
            if (currentRight) {
                appendDistinct(rightOutput, current);
            }
            previous = current;
            previousSide = currentSide;
            previousLeft = currentLeft;
            previousRight = currentRight;
        }
        trimClosingDuplicate(leftOutput);
        trimClosingDuplicate(rightOutput);
        return new HalfPlaneSplit(Polygon.create(leftOutput), Polygon.create(rightOutput));
    }

    private static Polygon clipHalfPlane(Polygon polygon, Point a, Point b, boolean keepLeft) {
        List<Point> input = polygon.vertices();
        List<Point> output = new ArrayList<>(input.size() + 2);
        Point previous = input.get(input.size() - 1);
        double previousSide = cross(a, b, previous);
        boolean previousInside = halfPlaneContains(previousSide, keepLeft);

        for (Point current : input) {
            double currentSide = cross(a, b, current);
            boolean currentInside = halfPlaneContains(currentSide, keepLeft);
            if (currentInside != previousInside) {
                Point crossing = lineIntersection(previous, current, previousSide, currentSide);
                if (crossing != null) {
                    appendDistinct(output, crossing);
                }
            }
            if (currentInside) {
                appendDistinct(output, current);
            }
            previous = current;
            previousSide = currentSide;
            previousInside = currentInside;
        }
        trimClosingDuplicate(output);
        return Polygon.create(output);
    }

    private static void trimClosingDuplicate(List<Point> points) {
        if (points.size() > 1 && samePoint(points.get(0), points.get(points.size() - 1))) {
            points.remove(points.size() - 1);
        }
    }

    private static boolean halfPlaneContains(double signedDistance, boolean keepLeft) {
        return keepLeft ? signedDistance >= -EPSILON : signedDistance <= EPSILON;
    }

    private static Point lineIntersection(Point from, Point to, double fromSide, double toSide) {
        double denominator = fromSide - toSide;
        if (Math.abs(denominator) <= EPSILON) {
            return null;
        }
        double t = Math.max(0.0D, Math.min(1.0D, fromSide / denominator));
        return new Point(
                from.u() + (to.u() - from.u()) * t,
                from.v() + (to.v() - from.v()) * t
        );
    }

    private static void appendDistinct(List<Point> points, Point point) {
        if (points.isEmpty() || !samePoint(points.get(points.size() - 1), point)) {
            points.add(point);
        }
    }

    private static double cross(Point a, Point b, Point c) {
        return (b.u() - a.u()) * (c.v() - a.v()) - (b.v() - a.v()) * (c.u() - a.u());
    }

    private static boolean samePoint(Point first, Point second) {
        return Math.abs(first.u() - second.u()) <= EPSILON
                && Math.abs(first.v() - second.v()) <= EPSILON;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    private record HalfPlaneSplit(Polygon left, Polygon right) {
    }

    record Point(double u, double v) {
        Point {
            if (!Double.isFinite(u) || !Double.isFinite(v)) {
                throw new IllegalArgumentException("Canonical polygon coordinates must be finite");
            }
        }
    }

    record Bounds(double minU, double minV, double maxU, double maxV) {
        boolean intersects(Bounds other) {
            return other != null
                    && Math.min(maxU, other.maxU) - Math.max(minU, other.minU) > EPSILON
                    && Math.min(maxV, other.maxV) - Math.max(minV, other.minV) > EPSILON;
        }

        boolean contains(Bounds other) {
            return other != null
                    && minU <= other.minU + EPSILON
                    && minV <= other.minV + EPSILON
                    && maxU + EPSILON >= other.maxU
                    && maxV + EPSILON >= other.maxV;
        }
    }

    static final class Polygon {
        private final List<Point> vertices;
        private final Bounds bounds;
        private final double area;

        private Polygon(List<Point> vertices, double area) {
            this.vertices = List.copyOf(vertices);
            this.area = area;
            double minU = Double.POSITIVE_INFINITY;
            double minV = Double.POSITIVE_INFINITY;
            double maxU = Double.NEGATIVE_INFINITY;
            double maxV = Double.NEGATIVE_INFINITY;
            for (Point point : vertices) {
                minU = Math.min(minU, point.u());
                minV = Math.min(minV, point.v());
                maxU = Math.max(maxU, point.u());
                maxV = Math.max(maxV, point.v());
            }
            this.bounds = new Bounds(minU, minV, maxU, maxV);
        }

        private static Polygon create(List<Point> rawVertices) {
            if (rawVertices == null || rawVertices.size() < 3) {
                return null;
            }
            List<Point> vertices = new ArrayList<>(rawVertices.size());
            for (Point point : rawVertices) {
                appendDistinct(vertices, point);
            }
            if (vertices.size() > 1 && samePoint(vertices.get(0), vertices.get(vertices.size() - 1))) {
                vertices.remove(vertices.size() - 1);
            }
            if (vertices.size() < 3) {
                return null;
            }

            boolean removed;
            do {
                removed = false;
                for (int index = 0; index < vertices.size() && vertices.size() >= 3; index++) {
                    Point before = vertices.get((index + vertices.size() - 1) % vertices.size());
                    Point current = vertices.get(index);
                    Point after = vertices.get((index + 1) % vertices.size());
                    if (Math.abs(cross(before, current, after)) <= EPSILON) {
                        vertices.remove(index);
                        removed = true;
                        break;
                    }
                }
            } while (removed);
            if (vertices.size() < 3) {
                return null;
            }

            double signedArea = signedArea(vertices);
            if (Math.abs(signedArea) <= EPSILON) {
                return null;
            }
            if (signedArea < 0.0D) {
                List<Point> reversed = new ArrayList<>(vertices.size());
                for (int index = vertices.size() - 1; index >= 0; index--) {
                    reversed.add(vertices.get(index));
                }
                vertices = reversed;
                signedArea = -signedArea;
            }
            return new Polygon(vertices, signedArea);
        }

        List<Point> vertices() {
            return vertices;
        }

        Bounds bounds() {
            return bounds;
        }

        double area() {
            return area;
        }

        boolean contains(Point point) {
            for (int index = 0; index < vertices.size(); index++) {
                Point a = vertices.get(index);
                Point b = vertices.get((index + 1) % vertices.size());
                if (cross(a, b, point) < -EPSILON) {
                    return false;
                }
            }
            return true;
        }

        boolean hasDiagonalEdge() {
            for (int index = 0; index < vertices.size(); index++) {
                Point a = vertices.get(index);
                Point b = vertices.get((index + 1) % vertices.size());
                if (Math.abs(a.u() - b.u()) > EPSILON && Math.abs(a.v() - b.v()) > EPSILON) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Polygon polygon && vertices.equals(polygon.vertices);
        }

        @Override
        public int hashCode() {
            return vertices.hashCode();
        }

        private static double signedArea(List<Point> vertices) {
            double twiceArea = 0.0D;
            for (int index = 0; index < vertices.size(); index++) {
                Point current = vertices.get(index);
                Point next = vertices.get((index + 1) % vertices.size());
                twiceArea += current.u() * next.v() - next.u() * current.v();
            }
            return twiceArea * 0.5D;
        }
    }

    static final class Region {
        private final List<Polygon> cells;
        private final Bounds bounds;
        private final double area;
        private volatile CellIndex cellIndex;

        private Region(List<Polygon> cells) {
            this.cells = List.copyOf(cells);
            double minU = Double.POSITIVE_INFINITY;
            double minV = Double.POSITIVE_INFINITY;
            double maxU = Double.NEGATIVE_INFINITY;
            double maxV = Double.NEGATIVE_INFINITY;
            double totalArea = 0.0D;
            for (Polygon cell : cells) {
                Bounds cellBounds = cell.bounds();
                minU = Math.min(minU, cellBounds.minU());
                minV = Math.min(minV, cellBounds.minV());
                maxU = Math.max(maxU, cellBounds.maxU());
                maxV = Math.max(maxV, cellBounds.maxV());
                totalArea += cell.area();
            }
            this.bounds = cells.isEmpty() ? null : new Bounds(minU, minV, maxU, maxV);
            this.area = Math.max(0.0D, totalArea);
        }

        static Region full() {
            return new Region(List.of(UNIT_SQUARE));
        }

        static Region ofCells(List<Polygon> cells) {
            Objects.requireNonNull(cells, "cells");
            List<Polygon> valid = cells;
            for (int index = 0; index < cells.size(); index++) {
                if (cells.get(index) == null) {
                    valid = new ArrayList<>(cells.size());
                    for (Polygon cell : cells) {
                        if (cell != null) {
                            valid.add(cell);
                        }
                    }
                    break;
                }
            }
            return valid.isEmpty() ? new Region(List.of()) : new Region(valid);
        }

        Region copy() {
            return this;
        }

        boolean isEmpty() {
            return cells.isEmpty();
        }

        int cellCount() {
            return cells.size();
        }

        int vertexCount() {
            int count = 0;
            for (Polygon cell : cells) {
                count += cell.vertices().size();
            }
            return count;
        }

        double area() {
            return area;
        }

        Bounds bounds() {
            return bounds;
        }

        List<Polygon> cells() {
            return cells;
        }

        boolean intersects(Polygon polygon) {
            return intersectsDetailed(polygon).hit();
        }

        List<Polygon> intersect(Polygon polygon) {
            return intersectDetailed(polygon).polygons();
        }

        IntersectionResult intersectDetailed(Polygon polygon) {
            if (polygon == null || bounds == null || !bounds.intersects(polygon.bounds())) {
                return new IntersectionResult(List.of(), QueryStats.empty(), 0, 0);
            }
            CandidateQuery candidates = queryCandidates(polygon.bounds());
            List<Polygon> output = new ArrayList<>();
            int testedCells = 0;
            int testedVertices = 0;
            for (int candidateIndex : candidates.indices()) {
                Polygon cell = cells.get(candidateIndex);
                testedCells++;
                testedVertices += cell.vertices().size();
                Polygon clipped = intersection(cell, polygon);
                if (clipped != null) {
                    output.add(clipped);
                }
            }
            return new IntersectionResult(
                    output,
                    candidates.stats(),
                    testedCells,
                    testedVertices
            );
        }

        IntersectionResult intersectRectangleDetailed(double minU, double minV, double maxU, double maxV) {
            Bounds rectangleBounds = new Bounds(minU, minV, maxU, maxV);
            if (bounds == null || !bounds.intersects(rectangleBounds)) {
                return new IntersectionResult(List.of(), QueryStats.empty(), 0, 0);
            }
            CandidateQuery candidates = queryCandidates(rectangleBounds);
            List<Polygon> output = new ArrayList<>(candidates.indices().length);
            Polygon clip = null;
            int testedCells = 0;
            int testedVertices = 0;
            for (int candidateIndex : candidates.indices()) {
                Polygon cell = cells.get(candidateIndex);
                testedCells++;
                testedVertices += cell.vertices().size();
                if (rectangleBounds.contains(cell.bounds())) {
                    output.add(cell);
                    continue;
                }
                if (clip == null) {
                    clip = rectangle(minU, minV, maxU, maxV);
                }
                Polygon clipped = intersection(cell, clip);
                if (clipped != null) {
                    output.add(clipped);
                }
            }
            return new IntersectionResult(
                    output,
                    candidates.stats(),
                    testedCells,
                    testedVertices
            );
        }

        IntersectionTest intersectsDetailed(Polygon polygon) {
            if (polygon == null || bounds == null || !bounds.intersects(polygon.bounds())) {
                return new IntersectionTest(false, QueryStats.empty(), 0, 0);
            }
            CandidateQuery candidates = queryCandidates(polygon.bounds());
            int testedCells = 0;
            int testedVertices = 0;
            for (int candidateIndex : candidates.indices()) {
                Polygon cell = cells.get(candidateIndex);
                testedCells++;
                testedVertices += cell.vertices().size();
                if (intersection(cell, polygon) != null) {
                    return new IntersectionTest(true, candidates.stats(), testedCells, testedVertices);
                }
            }
            return new IntersectionTest(false, candidates.stats(), testedCells, testedVertices);
        }

        Region subtract(Polygon blocker) {
            return subtractTracked(blocker).region();
        }

        SubtractionResult subtractTracked(Polygon blocker) {
            if (blocker == null || cells.isEmpty() || bounds == null || !bounds.intersects(blocker.bounds())) {
                return new SubtractionResult(this, false, 0, 0);
            }
            List<Polygon> output = new ArrayList<>();
            boolean changed = false;
            int testedCells = 0;
            int testedVertices = 0;
            for (Polygon cell : cells) {
                if (!cell.bounds().intersects(blocker.bounds())) {
                    output.add(cell);
                    continue;
                }
                testedCells++;
                testedVertices += cell.vertices().size();
                List<Polygon> pieces = CanonicalPolygonOps.subtract(cell, blocker);
                if (pieces.size() != 1 || !pieces.get(0).equals(cell)) {
                    changed = true;
                }
                output.addAll(pieces);
            }
            return changed
                    ? new SubtractionResult(ofCells(output), true, testedCells, testedVertices)
                    : new SubtractionResult(this, false, testedCells, testedVertices);
        }

        Region subtractAll(List<Polygon> blockers) {
            Region current = this;
            for (Polygon blocker : blockers) {
                if (current.isEmpty()) {
                    break;
                }
                current = current.subtract(blocker);
            }
            return current;
        }

        BulkSubtractionResult subtractIndexed(List<Polygon> blockers) {
            Objects.requireNonNull(blockers, "blockers");
            List<Polygon> validBlockers = blockers;
            for (int blockerIndex = 0; blockerIndex < blockers.size(); blockerIndex++) {
                if (blockers.get(blockerIndex) == null) {
                    validBlockers = new ArrayList<>(blockers.size());
                    for (Polygon blocker : blockers) {
                        if (blocker != null) {
                            validBlockers.add(blocker);
                        }
                    }
                    break;
                }
            }
            if (cells.isEmpty() || validBlockers.isEmpty()) {
                return BulkSubtractionResult.unchanged(this, validBlockers.size(), cells.size());
            }

            CellIndex blockerIndex = null;
            long indexBuildNanos = 0L;
            if (validBlockers.size() >= MIN_INDEXED_CELLS) {
                long indexBuildStartNanos = System.nanoTime();
                blockerIndex = CellIndex.build(validBlockers);
                indexBuildNanos = Math.max(0L, System.nanoTime() - indexBuildStartNanos);
            }

            long queryStartNanos = System.nanoTime();
            int[][] blockerCandidatesByCell = new int[cells.size()][];
            long bucketVisits = 0L;
            long bucketEntries = 0L;
            long duplicateSkips = 0L;
            long boundsRejects = 0L;
            long candidateCount = 0L;
            int maxCandidates = 0;
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                CandidateQuery query = blockerIndex == null
                        ? linearCandidates(validBlockers, cells.get(cellIndex).bounds())
                        : blockerIndex.query(validBlockers, cells.get(cellIndex).bounds(), false, 0L);
                blockerCandidatesByCell[cellIndex] = query.indices();
                QueryStats queryStats = query.stats();
                bucketVisits += queryStats.bucketVisits();
                bucketEntries += queryStats.bucketEntries();
                duplicateSkips += queryStats.duplicateSkips();
                boundsRejects += queryStats.boundsRejects();
                candidateCount += queryStats.candidates();
                maxCandidates = Math.max(maxCandidates, queryStats.candidates());
            }
            long indexQueryNanos = Math.max(0L, System.nanoTime() - queryStartNanos);

            long subtractionStartNanos = System.nanoTime();
            boolean[] blockerHits = new boolean[validBlockers.size()];
            List<Polygon> output = new ArrayList<>(cells.size() + validBlockers.size());
            ArrayList<Polygon> fragments = new ArrayList<>();
            ArrayList<Polygon> nextFragments = new ArrayList<>();
            boolean changed = false;
            long exactTests = 0L;
            long exactVertices = 0L;
            long changedFragments = 0L;
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                fragments.clear();
                fragments.add(cells.get(cellIndex));
                for (int blockerIndexValue : blockerCandidatesByCell[cellIndex]) {
                    if (fragments.isEmpty()) {
                        break;
                    }
                    Polygon blocker = validBlockers.get(blockerIndexValue);
                    nextFragments.clear();
                    boolean blockerChangedCell = false;
                    for (Polygon fragment : fragments) {
                        if (!fragment.bounds().intersects(blocker.bounds())) {
                            nextFragments.add(fragment);
                            continue;
                        }
                        exactTests++;
                        exactVertices += fragment.vertices().size();
                        List<Polygon> pieces = CanonicalPolygonOps.subtract(fragment, blocker);
                        boolean fragmentChanged = pieces.size() != 1 || !pieces.get(0).equals(fragment);
                        if (fragmentChanged) {
                            blockerChangedCell = true;
                            changed = true;
                            changedFragments++;
                        }
                        nextFragments.addAll(pieces);
                    }
                    if (blockerChangedCell) {
                        blockerHits[blockerIndexValue] = true;
                        ArrayList<Polygon> swap = fragments;
                        fragments = nextFragments;
                        nextFragments = swap;
                    }
                }
                output.addAll(fragments);
            }
            long subtractionNanos = Math.max(0L, System.nanoTime() - subtractionStartNanos);
            int hitCount = 0;
            for (boolean blockerHit : blockerHits) {
                if (blockerHit) {
                    hitCount++;
                }
            }
            Region result = changed ? ofCells(output) : this;
            return new BulkSubtractionResult(
                    result,
                    changed,
                    validBlockers.size(),
                    hitCount,
                    cells.size(),
                    result.cellCount(),
                    blockerIndex != null,
                    indexBuildNanos,
                    indexQueryNanos,
                    cells.size(),
                    blockerIndex == null ? cells.size() : 0,
                    bucketVisits,
                    bucketEntries,
                    duplicateSkips,
                    boundsRejects,
                    candidateCount,
                    maxCandidates,
                    exactTests,
                    exactVertices,
                    changedFragments,
                    subtractionNanos
            );
        }

        CompactionResult compactConvexCells() {
            if (cells.size() < 2) {
                return new CompactionResult(this, cells.size(), cells.size(), 0, 0);
            }
            MergePass pass = mergeConvexNeighborsIncremental(cells);
            List<Polygon> compacted = pass.cells();
            int edgeCandidates = pass.edgeCandidates();
            int merges = pass.merges();
            return merges == 0
                    ? new CompactionResult(this, cells.size(), cells.size(), edgeCandidates, 0)
                    : new CompactionResult(
                            new Region(compacted),
                            cells.size(),
                            compacted.size(),
                            edgeCandidates,
                            merges
                    );
        }

        private CandidateQuery queryCandidates(Bounds queryBounds) {
            if (cells.size() < MIN_INDEXED_CELLS) {
                return linearCandidates(cells, queryBounds);
            }

            CellIndex index = cellIndex;
            boolean built = false;
            long buildNanos = 0L;
            if (index == null) {
                long buildStartNanos = System.nanoTime();
                synchronized (this) {
                    index = cellIndex;
                    if (index == null) {
                        index = CellIndex.build(cells);
                        cellIndex = index;
                        built = true;
                    }
                }
                if (built) {
                    buildNanos = Math.max(0L, System.nanoTime() - buildStartNanos);
                }
            }
            return index.query(cells, queryBounds, built, buildNanos);
        }
    }

    record QueryStats(
            boolean indexed,
            boolean indexBuilt,
            long indexBuildNanos,
            int bucketVisits,
            int bucketEntries,
            int duplicateSkips,
            int boundsRejects,
            int candidates
    ) {
        private static QueryStats empty() {
            return new QueryStats(false, false, 0L, 0, 0, 0, 0, 0);
        }
    }

    record IntersectionResult(
            List<Polygon> polygons,
            QueryStats queryStats,
            int testedCells,
            int testedVertices
    ) {
        IntersectionResult {
            polygons = polygons.isEmpty() ? List.of() : List.copyOf(polygons);
        }
    }

    record IntersectionTest(
            boolean hit,
            QueryStats queryStats,
            int testedCells,
            int testedVertices
    ) {
    }

    record SubtractionResult(Region region, boolean changed, int testedCells, int testedVertices) {
    }

    record BulkSubtractionResult(
            Region region,
            boolean changed,
            int inputBlockers,
            int hitBlockers,
            int sourceCells,
            int outputCells,
            boolean indexed,
            long indexBuildNanos,
            long indexQueryNanos,
            int indexQueries,
            int linearQueries,
            long bucketVisits,
            long bucketEntries,
            long duplicateSkips,
            long boundsRejects,
            long candidates,
            int maxCandidates,
            long exactTests,
            long exactVertices,
            long changedFragments,
            long subtractionNanos
    ) {
        private static BulkSubtractionResult unchanged(Region region, int inputBlockers, int sourceCells) {
            return new BulkSubtractionResult(
                    region,
                    false,
                    inputBlockers,
                    0,
                    sourceCells,
                    sourceCells,
                    false,
                    0L,
                    0L,
                    0,
                    0,
                    0L,
                    0L,
                    0L,
                    0L,
                    0L,
                    0,
                    0L,
                    0L,
                    0L,
                    0L
            );
        }
    }

    record CompactionResult(
            Region region,
            int cellsBefore,
            int cellsAfter,
            int edgeCandidates,
            int merges
    ) {
    }

    private record CandidateQuery(int[] indices, QueryStats stats) {
    }

    private record MergePass(List<Polygon> cells, int edgeCandidates, int merges) {
    }

    private record EdgeOwner(int cellIndex, int version) {
    }

    private record MergeCandidate(
            int firstIndex,
            int firstVersion,
            int secondIndex,
            int secondVersion
    ) {
    }

    private static CandidateQuery linearCandidates(List<Polygon> polygons, Bounds queryBounds) {
        int[] indices = new int[polygons.size()];
        int count = 0;
        int boundsRejects = 0;
        for (int index = 0; index < polygons.size(); index++) {
            if (!polygons.get(index).bounds().intersects(queryBounds)) {
                boundsRejects++;
                continue;
            }
            indices[count++] = index;
        }
        int[] candidates = count == indices.length ? indices : Arrays.copyOf(indices, count);
        return new CandidateQuery(
                candidates,
                new QueryStats(false, false, 0L, 0, polygons.size(), 0, boundsRejects, count)
        );
    }

    private record VertexKey(long u, long v) implements Comparable<VertexKey> {
        private static VertexKey of(Point point) {
            return new VertexKey(Math.round(point.u() / EPSILON), Math.round(point.v() / EPSILON));
        }

        @Override
        public int compareTo(VertexKey other) {
            int byU = Long.compare(u, other.u);
            return byU != 0 ? byU : Long.compare(v, other.v);
        }
    }

    private record EdgeKey(VertexKey first, VertexKey second) {
        private static EdgeKey of(Point first, Point second) {
            VertexKey firstKey = VertexKey.of(first);
            VertexKey secondKey = VertexKey.of(second);
            return firstKey.compareTo(secondKey) <= 0
                    ? new EdgeKey(firstKey, secondKey)
                    : new EdgeKey(secondKey, firstKey);
        }
    }

    private static MergePass mergeConvexNeighborsIncremental(List<Polygon> inputCells) {
        ArrayList<Polygon> mutableCells = new ArrayList<>(inputCells);
        boolean[] active = new boolean[inputCells.size()];
        Arrays.fill(active, true);
        int[] versions = new int[inputCells.size()];
        Map<EdgeKey, EdgeOwner> edgeOwners = new HashMap<>();
        ArrayDeque<MergeCandidate> candidates = new ArrayDeque<>();
        int edgeCandidates = 0;

        for (int cellIndex = 0; cellIndex < mutableCells.size(); cellIndex++) {
            edgeCandidates += registerMergeEdges(
                    mutableCells,
                    active,
                    versions,
                    edgeOwners,
                    candidates,
                    cellIndex
            );
        }

        int merges = 0;
        while (!candidates.isEmpty()) {
            MergeCandidate candidate = candidates.removeFirst();
            if (!active[candidate.firstIndex()]
                    || !active[candidate.secondIndex()]
                    || versions[candidate.firstIndex()] != candidate.firstVersion()
                    || versions[candidate.secondIndex()] != candidate.secondVersion()) {
                continue;
            }

            Polygon merged = convexUnion(
                    mutableCells.get(candidate.firstIndex()),
                    mutableCells.get(candidate.secondIndex())
            );
            if (merged == null) {
                continue;
            }

            mutableCells.set(candidate.firstIndex(), merged);
            active[candidate.secondIndex()] = false;
            versions[candidate.firstIndex()]++;
            merges++;
            edgeCandidates += registerMergeEdges(
                    mutableCells,
                    active,
                    versions,
                    edgeOwners,
                    candidates,
                    candidate.firstIndex()
            );
        }

        if (merges == 0) {
            return new MergePass(inputCells, edgeCandidates, 0);
        }
        List<Polygon> output = new ArrayList<>(inputCells.size() - merges);
        for (int index = 0; index < mutableCells.size(); index++) {
            if (active[index]) {
                output.add(mutableCells.get(index));
            }
        }
        return new MergePass(List.copyOf(output), edgeCandidates, merges);
    }

    private static int registerMergeEdges(
            List<Polygon> cells,
            boolean[] active,
            int[] versions,
            Map<EdgeKey, EdgeOwner> edgeOwners,
            ArrayDeque<MergeCandidate> candidates,
            int cellIndex
    ) {
        int edgeCandidates = 0;
        int version = versions[cellIndex];
        List<Point> vertices = cells.get(cellIndex).vertices();
        for (int edgeIndex = 0; edgeIndex < vertices.size(); edgeIndex++) {
            EdgeKey edge = EdgeKey.of(
                    vertices.get(edgeIndex),
                    vertices.get((edgeIndex + 1) % vertices.size())
            );
            EdgeOwner owner = edgeOwners.get(edge);
            if (owner == null
                    || !active[owner.cellIndex()]
                    || versions[owner.cellIndex()] != owner.version()) {
                edgeOwners.put(edge, new EdgeOwner(cellIndex, version));
                continue;
            }
            if (owner.cellIndex() == cellIndex) {
                if (owner.version() != version) {
                    edgeOwners.put(edge, new EdgeOwner(cellIndex, version));
                }
                continue;
            }

            int firstIndex = Math.min(owner.cellIndex(), cellIndex);
            int secondIndex = Math.max(owner.cellIndex(), cellIndex);
            candidates.addLast(new MergeCandidate(
                    firstIndex,
                    versions[firstIndex],
                    secondIndex,
                    versions[secondIndex]
            ));
            edgeCandidates++;
        }
        return edgeCandidates;
    }

    private static Polygon convexUnion(Polygon first, Polygon second) {
        List<Point> points = new ArrayList<>(first.vertices().size() + second.vertices().size());
        points.addAll(first.vertices());
        points.addAll(second.vertices());
        Polygon hull = convexHull(points);
        if (hull == null) {
            return null;
        }
        double expectedArea = first.area() + second.area();
        double tolerance = MERGE_AREA_EPSILON * Math.max(1.0D, expectedArea);
        return Math.abs(hull.area() - expectedArea) <= tolerance ? hull : null;
    }

    private static final class CellIndex {
        private final int binCount;
        private final int[][] buckets;

        private CellIndex(int binCount, int[][] buckets) {
            this.binCount = binCount;
            this.buckets = buckets;
        }

        private static CellIndex build(List<Polygon> cells) {
            int binCount = Math.max(
                    MIN_INDEX_BINS,
                    Math.min(MAX_INDEX_BINS, (int) Math.ceil(Math.sqrt(cells.size())))
            );
            @SuppressWarnings("unchecked")
            List<Integer>[] mutableBuckets = new List[binCount * binCount];
            for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
                Bounds bounds = cells.get(cellIndex).bounds();
                int minU = minBin(bounds.minU(), binCount);
                int maxU = maxBin(bounds.maxU(), binCount);
                int minV = minBin(bounds.minV(), binCount);
                int maxV = maxBin(bounds.maxV(), binCount);
                for (int v = minV; v <= maxV; v++) {
                    for (int u = minU; u <= maxU; u++) {
                        int bucketIndex = v * binCount + u;
                        List<Integer> bucket = mutableBuckets[bucketIndex];
                        if (bucket == null) {
                            bucket = new ArrayList<>();
                            mutableBuckets[bucketIndex] = bucket;
                        }
                        bucket.add(cellIndex);
                    }
                }
            }
            int[][] buckets = new int[mutableBuckets.length][];
            for (int index = 0; index < mutableBuckets.length; index++) {
                List<Integer> bucket = mutableBuckets[index];
                if (bucket == null || bucket.isEmpty()) {
                    buckets[index] = new int[0];
                    continue;
                }
                int[] entries = new int[bucket.size()];
                for (int entry = 0; entry < entries.length; entry++) {
                    entries[entry] = bucket.get(entry);
                }
                buckets[index] = entries;
            }
            return new CellIndex(binCount, buckets);
        }

        private CandidateQuery query(
                List<Polygon> cells,
                Bounds queryBounds,
                boolean built,
                long buildNanos
        ) {
            int minU = minBin(queryBounds.minU(), binCount);
            int maxU = maxBin(queryBounds.maxU(), binCount);
            int minV = minBin(queryBounds.minV(), binCount);
            int maxV = maxBin(queryBounds.maxV(), binCount);
            int bucketVisits = Math.max(0, maxU - minU + 1) * Math.max(0, maxV - minV + 1);
            int bucketEntries = 0;
            for (int v = minV; v <= maxV; v++) {
                for (int u = minU; u <= maxU; u++) {
                    bucketEntries += buckets[v * binCount + u].length;
                }
            }
            int[] gathered = new int[bucketEntries];
            int cursor = 0;
            for (int v = minV; v <= maxV; v++) {
                for (int u = minU; u <= maxU; u++) {
                    int[] bucket = buckets[v * binCount + u];
                    System.arraycopy(bucket, 0, gathered, cursor, bucket.length);
                    cursor += bucket.length;
                }
            }
            Arrays.sort(gathered);
            int unique = 0;
            int duplicateSkips = 0;
            int boundsRejects = 0;
            int previous = -1;
            for (int cellIndex : gathered) {
                if (cellIndex == previous) {
                    duplicateSkips++;
                    continue;
                }
                previous = cellIndex;
                if (!cells.get(cellIndex).bounds().intersects(queryBounds)) {
                    boundsRejects++;
                    continue;
                }
                gathered[unique++] = cellIndex;
            }
            int[] candidates = unique == gathered.length ? gathered : Arrays.copyOf(gathered, unique);
            return new CandidateQuery(
                    candidates,
                    new QueryStats(
                            true,
                            built,
                            buildNanos,
                            bucketVisits,
                            bucketEntries,
                            duplicateSkips,
                            boundsRejects,
                            candidates.length
                    )
            );
        }

        private static int minBin(double coordinate, int binCount) {
            double clamped = Math.max(0.0D, Math.min(1.0D, coordinate));
            return Math.max(0, Math.min(binCount - 1, (int) Math.floor(clamped * binCount)));
        }

        private static int maxBin(double coordinate, int binCount) {
            double clamped = Math.max(0.0D, Math.min(1.0D, coordinate));
            return Math.max(0, Math.min(binCount - 1, (int) Math.ceil(clamped * binCount) - 1));
        }
    }

    private CanonicalPolygonOps() {
    }
}
