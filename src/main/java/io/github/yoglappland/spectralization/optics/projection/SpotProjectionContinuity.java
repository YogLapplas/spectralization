package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;

public final class SpotProjectionContinuity {
    private static final int PROOF_SCALE = SpotRecord.QUAD_QUANTIZATION_LEVEL;
    private static final int POSITION_EPSILON_UNIT = 2;
    private static final int MIN_SHARED_EDGE_UNIT = 4;
    private static final int TEXTURE_EPSILON_UNIT = 4;

    public static Report inspect(List<SpotRecord> spots, int maxExamples) {
        List<Quad> quads = new ArrayList<>();

        for (SpotRecord spot : spots) {
            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_QUAD
                    || spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_SLICE) {
                quads.add(Quad.from(spot));
            }
        }

        int sharedEdges = 0;
        int mismatchCount = 0;
        int maxTextureGap = 0;
        List<Mismatch> mismatches = new ArrayList<>();

        for (int leftIndex = 0; leftIndex < quads.size(); leftIndex++) {
            Quad left = quads.get(leftIndex);

            for (int rightIndex = leftIndex + 1; rightIndex < quads.size(); rightIndex++) {
                Quad right = quads.get(rightIndex);

                TextureContinuity gridContinuity = left.gridBoundaryContinuity(right);
                if (gridContinuity != null) {
                    sharedEdges++;

                    int textureGap = gridContinuity.textureGap();
                    if (textureGap > TEXTURE_EPSILON_UNIT) {
                        mismatchCount++;
                        maxTextureGap = Math.max(maxTextureGap, textureGap);

                        if (mismatches.size() < maxExamples) {
                            mismatches.add(new Mismatch(
                                    left.spot(),
                                    -1,
                                    right.spot(),
                                    -1,
                                    textureGap,
                                    gridContinuity
                            ));
                        }
                    }
                }

                for (int leftEdgeIndex = 0; leftEdgeIndex < 4; leftEdgeIndex++) {
                    Edge leftEdge = left.edge(leftEdgeIndex);

                    for (int rightEdgeIndex = 0; rightEdgeIndex < 4; rightEdgeIndex++) {
                        Edge rightEdge = right.edge(rightEdgeIndex);

                        TextureContinuity continuity = leftEdge.textureContinuity(rightEdge);

                        if (continuity == null) {
                            continue;
                        }

                        sharedEdges++;

                        int textureGap = continuity.textureGap();
                        if (textureGap > TEXTURE_EPSILON_UNIT) {
                            mismatchCount++;
                            maxTextureGap = Math.max(maxTextureGap, textureGap);

                            if (mismatches.size() < maxExamples) {
                                mismatches.add(new Mismatch(
                                        left.spot(),
                                        leftEdgeIndex,
                                        right.spot(),
                                        rightEdgeIndex,
                                        textureGap,
                                        continuity
                                ));
                            }
                        }
                    }
                }
            }
        }

        return new Report(quads.size(), sharedEdges, mismatchCount, maxTextureGap, mismatches);
    }

    public static Metrics metrics(SpotRecord spot) {
        if (spot.projectionMode() != SpotRecord.ProjectionMode.FOOTPRINT_QUAD
                && spot.projectionMode() != SpotRecord.ProjectionMode.FOOTPRINT_SLICE) {
            return Metrics.EMPTY;
        }

        Quad quad = Quad.from(spot);
        double worldArea = quad.worldArea() / ((double) PROOF_SCALE * PROOF_SCALE);
        double textureArea = quad.textureArea() / ((double) PROOF_SCALE * PROOF_SCALE);
        double stretchRatio = textureArea <= 0.0D ? 0.0D : worldArea / textureArea;
        return new Metrics(textureArea, worldArea, stretchRatio);
    }

    public record Report(
            int surfaceCount,
            int sharedEdges,
            int mismatchCount,
            int maxTextureGap,
            List<Mismatch> mismatches
    ) {
        public Report {
            mismatches = List.copyOf(mismatches);
        }

        public boolean hasMismatch() {
            return mismatchCount > 0;
        }
    }

    public record Mismatch(
            SpotRecord first,
            int firstEdge,
            SpotRecord second,
            int secondEdge,
            int textureGap,
            TextureContinuity continuity
    ) {
        public String format() {
            return "first=" + formatSpot(first) + "/" + formatEdge(firstEdge)
                    + " second=" + formatSpot(second) + "/" + formatEdge(secondEdge)
                    + " texture_gap=" + textureGap
                    + " " + continuity.format();
        }
    }

    public record Metrics(double originalArea, double worldArea, double stretchRatio) {
        private static final Metrics EMPTY = new Metrics(0.0D, 0.0D, 0.0D);
    }

    private record Quad(SpotRecord spot, Vertex p0, Vertex p1, Vertex p2, Vertex p3) {
        private static Quad from(SpotRecord spot) {
            int baseX = spot.pos().getX() * PROOF_SCALE;
            int baseY = spot.pos().getY() * PROOF_SCALE;
            int baseZ = spot.pos().getZ() * PROOF_SCALE;

            if (spot.projectionMode() == SpotRecord.ProjectionMode.FOOTPRINT_SLICE) {
                return fromSlice(spot, baseX, baseY, baseZ);
            }

            return new Quad(
                    spot,
                    new Vertex(
                            baseX + spot.quadX0(),
                            baseY + spot.quadY0(),
                            baseZ + spot.quadZ0(),
                            spot.quadTextureU0(),
                            spot.quadTextureV0()
                    ),
                    new Vertex(
                            baseX + spot.quadX1(),
                            baseY + spot.quadY1(),
                            baseZ + spot.quadZ1(),
                            spot.quadTextureU1(),
                            spot.quadTextureV1()
                    ),
                    new Vertex(
                            baseX + spot.quadX2(),
                            baseY + spot.quadY2(),
                            baseZ + spot.quadZ2(),
                            spot.quadTextureU2(),
                            spot.quadTextureV2()
                    ),
                    new Vertex(
                            baseX + spot.quadX3(),
                            baseY + spot.quadY3(),
                            baseZ + spot.quadZ3(),
                            spot.quadTextureU3(),
                            spot.quadTextureV3()
                    )
            );
        }

        private static Quad fromSlice(SpotRecord spot, int baseX, int baseY, int baseZ) {
            double minU = byteToUnit(spot.clipMinU());
            double minV = byteToUnit(spot.clipMinV());
            double maxU = byteToUnit(spot.clipMaxU());
            double maxV = byteToUnit(spot.clipMaxV());
            SpotSurfaceFrame.LocalCoordinates p0 = SpotSurfaceFrame.surfaceLocal(spot.face(), minU, minV, 0.0D);
            SpotSurfaceFrame.LocalCoordinates p1 = SpotSurfaceFrame.surfaceLocal(spot.face(), minU, maxV, 0.0D);
            SpotSurfaceFrame.LocalCoordinates p2 = SpotSurfaceFrame.surfaceLocal(spot.face(), maxU, maxV, 0.0D);
            SpotSurfaceFrame.LocalCoordinates p3 = SpotSurfaceFrame.surfaceLocal(spot.face(), maxU, minV, 0.0D);

            return new Quad(
                    spot,
                    vertex(baseX, baseY, baseZ, p0, sliceUnitToProof(spot.textureMinU()), sliceUnitToProof(spot.textureMaxV())),
                    vertex(baseX, baseY, baseZ, p1, sliceUnitToProof(spot.textureMinU()), sliceUnitToProof(spot.textureMinV())),
                    vertex(baseX, baseY, baseZ, p2, sliceUnitToProof(spot.textureMaxU()), sliceUnitToProof(spot.textureMinV())),
                    vertex(baseX, baseY, baseZ, p3, sliceUnitToProof(spot.textureMaxU()), sliceUnitToProof(spot.textureMaxV()))
            );
        }

        private Edge edge(int index) {
            return switch (index) {
                case 0 -> new Edge(p0, p1);
                case 1 -> new Edge(p1, p2);
                case 2 -> new Edge(p2, p3);
                case 3 -> new Edge(p3, p0);
                default -> throw new IllegalArgumentException("Quad edge index must be in [0, 3]");
            };
        }

        private long worldArea() {
            return polygonArea3d(p0.x(), p0.y(), p0.z(), p1.x(), p1.y(), p1.z(), p2.x(), p2.y(), p2.z(), p3.x(), p3.y(), p3.z());
        }

        private long textureArea() {
            return polygonArea2d(p0.u(), p0.v(), p1.u(), p1.v(), p2.u(), p2.v(), p3.u(), p3.v());
        }

        private TextureContinuity gridBoundaryContinuity(Quad other) {
            GridBoundary boundary = GridBoundary.between(this, other);

            if (boundary == null) {
                return null;
            }

            BoundarySection first = boundary.section(this);
            BoundarySection second = boundary.section(other);

            if (first == null && second == null) {
                return null;
            }

            if (first == null || second == null) {
                BoundarySection present = first == null ? second : first;
                WorldPoint start = boundary.worldAt(present.minCoord());
                WorldPoint end = boundary.worldAt(present.maxCoord());
                TexturePoint presentStart = present.textureAt(present.minCoord());
                TexturePoint presentEnd = present.textureAt(present.maxCoord());
                TexturePoint missing = new TexturePoint(0.0D, 0.0D);

                return new TextureContinuity(
                        PROOF_SCALE,
                        start,
                        end,
                        first == null ? missing : presentStart,
                        first == null ? missing : presentEnd,
                        second == null ? missing : presentStart,
                        second == null ? missing : presentEnd,
                        "grid_section_missing"
                );
            }

            double sectionGap = Math.max(
                    Math.abs(first.minCoord() - second.minCoord()),
                    Math.abs(first.maxCoord() - second.maxCoord())
            );

            if (sectionGap > POSITION_EPSILON_UNIT) {
                double min = Math.min(first.minCoord(), second.minCoord());
                double max = Math.max(first.maxCoord(), second.maxCoord());
                return new TextureContinuity(
                        Math.max(PROOF_SCALE, (int) Math.round(sectionGap)),
                        boundary.worldAt(min),
                        boundary.worldAt(max),
                        first.textureAt(first.clampCoord(min)),
                        first.textureAt(first.clampCoord(max)),
                        second.textureAt(second.clampCoord(min)),
                        second.textureAt(second.clampCoord(max)),
                        String.format(
                                Locale.ROOT,
                                "grid_section_gap first_s=[%s,%s] second_s=[%s,%s]",
                                formatProofUnit(first.minCoord()),
                                formatProofUnit(first.maxCoord()),
                                formatProofUnit(second.minCoord()),
                                formatProofUnit(second.maxCoord())
                        )
                );
            }

            double min = Math.max(first.minCoord(), second.minCoord());
            double max = Math.min(first.maxCoord(), second.maxCoord());

            if (max - min <= MIN_SHARED_EDGE_UNIT) {
                return null;
            }

            TexturePoint firstStart = first.textureAt(min);
            TexturePoint firstEnd = first.textureAt(max);
            TexturePoint secondStart = second.textureAt(min);
            TexturePoint secondEnd = second.textureAt(max);
            int startGap = firstStart.distance(secondStart);
            int endGap = firstEnd.distance(secondEnd);
            int textureGap = Math.max(startGap, endGap);

            return new TextureContinuity(
                    textureGap,
                    boundary.worldAt(min),
                    boundary.worldAt(max),
                    firstStart,
                    firstEnd,
                    secondStart,
                    secondEnd,
                    "grid_boundary_uv"
            );
        }
    }

    private record Edge(Vertex a, Vertex b) {
        private TextureContinuity textureContinuity(Edge other) {
            EdgeOverlap overlap = worldOverlap(other);

            if (overlap == null) {
                return null;
            }

            TexturePoint firstStart = textureAt(overlap.firstStart());
            TexturePoint firstEnd = textureAt(overlap.firstEnd());
            TexturePoint secondStart = other.textureAt(overlap.secondStart());
            TexturePoint secondEnd = other.textureAt(overlap.secondEnd());
            int startGap = firstStart.distance(secondStart);
            int endGap = firstEnd.distance(secondEnd);
            int textureGap = Math.max(startGap, endGap);

            return new TextureContinuity(
                    textureGap,
                    worldAt(overlap.firstStart()),
                    worldAt(overlap.firstEnd()),
                    firstStart,
                    firstEnd,
                    secondStart,
                    secondEnd
            );
        }

        private EdgeOverlap worldOverlap(Edge other) {
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;

            if (lengthSquared <= (double) POSITION_EPSILON_UNIT * POSITION_EPSILON_UNIT) {
                return null;
            }

            double otherDx = other.b.x() - other.a.x();
            double otherDy = other.b.y() - other.a.y();
            double otherDz = other.b.z() - other.a.z();
            double otherLengthSquared = otherDx * otherDx + otherDy * otherDy + otherDz * otherDz;

            if (otherLengthSquared <= (double) POSITION_EPSILON_UNIT * POSITION_EPSILON_UNIT) {
                return null;
            }

            if (!pointOnLine(other.a(), dx, dy, dz, lengthSquared)
                    || !pointOnLine(other.b(), dx, dy, dz, lengthSquared)) {
                return null;
            }

            double otherStartOnFirst = parameterOf(other.a());
            double otherEndOnFirst = parameterOf(other.b());
            double firstStart = Math.max(0.0D, Math.min(otherStartOnFirst, otherEndOnFirst));
            double firstEnd = Math.min(1.0D, Math.max(otherStartOnFirst, otherEndOnFirst));

            if ((firstEnd - firstStart) * Math.sqrt(lengthSquared) <= MIN_SHARED_EDGE_UNIT) {
                return null;
            }

            WorldPoint overlapStart = worldAt(firstStart);
            WorldPoint overlapEnd = worldAt(firstEnd);
            double secondStart = other.parameterOf(overlapStart);
            double secondEnd = other.parameterOf(overlapEnd);

            if (secondStart < -0.001D || secondStart > 1.001D || secondEnd < -0.001D || secondEnd > 1.001D) {
                return null;
            }

            return new EdgeOverlap(
                    clamp01(firstStart),
                    clamp01(firstEnd),
                    clamp01(secondStart),
                    clamp01(secondEnd)
            );
        }

        private boolean pointOnLine(Vertex point, double dx, double dy, double dz, double lengthSquared) {
            return pointOnLine(point.x(), point.y(), point.z(), dx, dy, dz, lengthSquared);
        }

        private boolean pointOnLine(double x, double y, double z, double dx, double dy, double dz, double lengthSquared) {
            double px = x - a.x();
            double py = y - a.y();
            double pz = z - a.z();
            double cx = py * dz - pz * dy;
            double cy = pz * dx - px * dz;
            double cz = px * dy - py * dx;
            double crossSquared = cx * cx + cy * cy + cz * cz;
            double tolerance = (double) POSITION_EPSILON_UNIT * POSITION_EPSILON_UNIT * lengthSquared;
            return crossSquared <= tolerance;
        }

        private double parameterOf(Vertex point) {
            return parameterOf(point.x(), point.y(), point.z());
        }

        private double parameterOf(WorldPoint point) {
            return parameterOf(point.x(), point.y(), point.z());
        }

        private double parameterOf(double x, double y, double z) {
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;

            if (lengthSquared <= 0.0D) {
                return 0.0D;
            }

            return ((x - a.x()) * dx + (y - a.y()) * dy + (z - a.z()) * dz) / lengthSquared;
        }

        private WorldPoint worldAt(double t) {
            return new WorldPoint(
                    lerp(a.x(), b.x(), t),
                    lerp(a.y(), b.y(), t),
                    lerp(a.z(), b.z(), t)
            );
        }

        private TexturePoint textureAt(double t) {
            return new TexturePoint(
                    lerp(a.u(), b.u(), t),
                    lerp(a.v(), b.v(), t)
            );
        }
    }

    private record Vertex(int x, int y, int z, int u, int v) {
    }

    public record TextureContinuity(
            int textureGap,
            WorldPoint worldStart,
            WorldPoint worldEnd,
            TexturePoint firstStart,
            TexturePoint firstEnd,
            TexturePoint secondStart,
            TexturePoint secondEnd,
            String issue
    ) {
        public TextureContinuity(
                int textureGap,
                WorldPoint worldStart,
                WorldPoint worldEnd,
                TexturePoint firstStart,
                TexturePoint firstEnd,
                TexturePoint secondStart,
                TexturePoint secondEnd
        ) {
            this(textureGap, worldStart, worldEnd, firstStart, firstEnd, secondStart, secondEnd, "edge_uv");
        }

        public TextureContinuity(
                int textureGap,
                WorldPoint worldStart,
                WorldPoint worldEnd,
                TexturePoint firstStart,
                TexturePoint firstEnd,
                TexturePoint secondStart,
                TexturePoint secondEnd,
                String issue
        ) {
            this.textureGap = Math.max(0, textureGap);
            this.worldStart = worldStart;
            this.worldEnd = worldEnd;
            this.firstStart = firstStart;
            this.firstEnd = firstEnd;
            this.secondStart = secondStart;
            this.secondEnd = secondEnd;
            this.issue = issue == null ? "" : issue;
        }

        private String format() {
            return "issue=" + issue
                    + " world=[" + worldStart.formatWorld() + "->" + worldEnd.formatWorld() + "]"
                    + " first_uv=[" + firstStart.formatUv() + "->" + firstEnd.formatUv() + "]"
                    + " second_uv=[" + secondStart.formatUv() + "->" + secondEnd.formatUv() + "]";
        }
    }

    public record WorldPoint(double x, double y, double z) {
        private String formatWorld() {
            return formatProofUnit(x) + "," + formatProofUnit(y) + "," + formatProofUnit(z);
        }
    }

    public record TexturePoint(double u, double v) {
        private int distance(TexturePoint other) {
            return Math.max(
                    (int) Math.round(Math.abs(u - other.u)),
                    (int) Math.round(Math.abs(v - other.v))
            );
        }

        private String formatUv() {
            return formatProofUnit(u) + "," + formatProofUnit(v);
        }
    }

    private record EdgeOverlap(double firstStart, double firstEnd, double secondStart, double secondEnd) {
    }

    private record GridBoundary(
            Direction face,
            Direction.Axis fixedAxis,
            Direction.Axis segmentAxis,
            int fixedCoord,
            int segmentMin,
            int segmentMax,
            int planeCoord
    ) {
        private static GridBoundary between(Quad first, Quad second) {
            Direction face = first.spot().face();

            if (face != second.spot().face()) {
                return null;
            }

            Direction.Axis normalAxis = face.getAxis();
            int firstPlane = planeCoordinate(first.spot(), normalAxis, face);
            int secondPlane = planeCoordinate(second.spot(), normalAxis, face);

            if (Math.abs(firstPlane - secondPlane) > POSITION_EPSILON_UNIT) {
                return null;
            }

            Direction.Axis[] tangentAxes = tangentAxes(normalAxis);
            Direction.Axis firstAxis = tangentAxes[0];
            Direction.Axis secondAxis = tangentAxes[1];
            int firstDelta = blockCoordinate(second.spot(), firstAxis) - blockCoordinate(first.spot(), firstAxis);
            int secondDelta = blockCoordinate(second.spot(), secondAxis) - blockCoordinate(first.spot(), secondAxis);

            if (Math.abs(blockCoordinate(second.spot(), normalAxis) - blockCoordinate(first.spot(), normalAxis)) != 0) {
                return null;
            }

            if (Math.abs(firstDelta) == 1 && secondDelta == 0) {
                return new GridBoundary(
                        face,
                        firstAxis,
                        secondAxis,
                        Math.max(blockCoordinate(first.spot(), firstAxis), blockCoordinate(second.spot(), firstAxis)) * PROOF_SCALE,
                        blockCoordinate(first.spot(), secondAxis) * PROOF_SCALE,
                        (blockCoordinate(first.spot(), secondAxis) + 1) * PROOF_SCALE,
                        firstPlane
                );
            }

            if (Math.abs(secondDelta) == 1 && firstDelta == 0) {
                return new GridBoundary(
                        face,
                        secondAxis,
                        firstAxis,
                        Math.max(blockCoordinate(first.spot(), secondAxis), blockCoordinate(second.spot(), secondAxis)) * PROOF_SCALE,
                        blockCoordinate(first.spot(), firstAxis) * PROOF_SCALE,
                        (blockCoordinate(first.spot(), firstAxis) + 1) * PROOF_SCALE,
                        firstPlane
                );
            }

            return null;
        }

        private BoundarySection section(Quad quad) {
            List<BoundarySample> samples = new ArrayList<>(8);

            addIntersections(samples, quad.p0(), quad.p1());
            addIntersections(samples, quad.p1(), quad.p2());
            addIntersections(samples, quad.p2(), quad.p3());
            addIntersections(samples, quad.p3(), quad.p0());

            if (samples.size() < 2) {
                return null;
            }

            samples.sort((first, second) -> Double.compare(first.coord(), second.coord()));
            List<BoundarySample> unique = new ArrayList<>(samples.size());

            for (BoundarySample sample : samples) {
                if (unique.isEmpty()
                        || Math.abs(sample.coord() - unique.get(unique.size() - 1).coord()) > POSITION_EPSILON_UNIT) {
                    unique.add(sample);
                }
            }

            if (unique.size() < 2) {
                return null;
            }

            BoundarySample min = unique.get(0);
            BoundarySample max = unique.get(unique.size() - 1);

            if (max.coord() - min.coord() <= MIN_SHARED_EDGE_UNIT) {
                return null;
            }

            return new BoundarySection(min, max);
        }

        private void addIntersections(List<BoundarySample> samples, Vertex first, Vertex second) {
            double firstFixed = coordinate(first, fixedAxis);
            double secondFixed = coordinate(second, fixedAxis);
            double firstDelta = firstFixed - fixedCoord;
            double secondDelta = secondFixed - fixedCoord;
            boolean firstOnLine = Math.abs(firstDelta) <= POSITION_EPSILON_UNIT;
            boolean secondOnLine = Math.abs(secondDelta) <= POSITION_EPSILON_UNIT;

            if (firstOnLine && secondOnLine) {
                addSampleIfInside(samples, first, 0.0D, first);
                addSampleIfInside(samples, second, 0.0D, second);
                return;
            }

            double span = secondFixed - firstFixed;

            if (Math.abs(span) <= POSITION_EPSILON_UNIT) {
                return;
            }

            double t = (fixedCoord - firstFixed) / span;

            if (t < -0.001D || t > 1.001D) {
                return;
            }

            t = clamp01(t);
            double segmentCoord = lerp(coordinate(first, segmentAxis), coordinate(second, segmentAxis), t);

            if (segmentCoord < segmentMin - POSITION_EPSILON_UNIT
                    || segmentCoord > segmentMax + POSITION_EPSILON_UNIT) {
                return;
            }

            samples.add(new BoundarySample(
                    Math.max(segmentMin, Math.min(segmentMax, segmentCoord)),
                    new TexturePoint(
                            lerp(first.u(), second.u(), t),
                            lerp(first.v(), second.v(), t)
                    )
            ));
        }

        private void addSampleIfInside(List<BoundarySample> samples, Vertex vertex, double t, Vertex ignored) {
            double segmentCoord = coordinate(vertex, segmentAxis);

            if (segmentCoord < segmentMin - POSITION_EPSILON_UNIT
                    || segmentCoord > segmentMax + POSITION_EPSILON_UNIT) {
                return;
            }

            samples.add(new BoundarySample(
                    Math.max(segmentMin, Math.min(segmentMax, segmentCoord)),
                    new TexturePoint(vertex.u(), vertex.v())
            ));
        }

        private WorldPoint worldAt(double segmentCoord) {
            double x = 0.0D;
            double y = 0.0D;
            double z = 0.0D;

            x = axisValue(Direction.Axis.X, fixedAxis, segmentAxis, fixedCoord, segmentCoord, planeCoord);
            y = axisValue(Direction.Axis.Y, fixedAxis, segmentAxis, fixedCoord, segmentCoord, planeCoord);
            z = axisValue(Direction.Axis.Z, fixedAxis, segmentAxis, fixedCoord, segmentCoord, planeCoord);
            return new WorldPoint(x, y, z);
        }

        private double axisValue(
                Direction.Axis axis,
                Direction.Axis fixedAxis,
                Direction.Axis segmentAxis,
                int fixedCoord,
                double segmentCoord,
                int planeCoord
        ) {
            if (axis == fixedAxis) {
                return fixedCoord;
            }

            if (axis == segmentAxis) {
                return segmentCoord;
            }

            return planeCoord;
        }
    }

    private record BoundarySample(double coord, TexturePoint texture) {
    }

    private record BoundarySection(BoundarySample min, BoundarySample max) {
        private double minCoord() {
            return min.coord();
        }

        private double maxCoord() {
            return max.coord();
        }

        private double clampCoord(double coord) {
            return Math.max(minCoord(), Math.min(maxCoord(), coord));
        }

        private TexturePoint textureAt(double coord) {
            double span = max.coord() - min.coord();

            if (span <= POSITION_EPSILON_UNIT) {
                return min.texture();
            }

            double t = clamp01((coord - min.coord()) / span);
            return new TexturePoint(
                    lerp(min.texture().u(), max.texture().u(), t),
                    lerp(min.texture().v(), max.texture().v(), t)
            );
        }
    }

    private static String formatEdge(int edge) {
        return edge < 0 ? "grid_boundary" : "edge" + edge;
    }

    private static int blockCoordinate(SpotRecord spot, Direction.Axis axis) {
        return switch (axis) {
            case X -> spot.pos().getX();
            case Y -> spot.pos().getY();
            case Z -> spot.pos().getZ();
        };
    }

    private static int planeCoordinate(SpotRecord spot, Direction.Axis axis, Direction face) {
        int coordinate = blockCoordinate(spot, axis);
        return (coordinate + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0)) * PROOF_SCALE;
    }

    private static Direction.Axis[] tangentAxes(Direction.Axis normalAxis) {
        return switch (normalAxis) {
            case X -> new Direction.Axis[]{Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static double coordinate(Vertex vertex, Direction.Axis axis) {
        return switch (axis) {
            case X -> vertex.x();
            case Y -> vertex.y();
            case Z -> vertex.z();
        };
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    private static String formatProofUnit(double value) {
        return String.format(Locale.ROOT, "%.6f", value / PROOF_SCALE);
    }

    private static long polygonArea3d(
            int x0,
            int y0,
            int z0,
            int x1,
            int y1,
            int z1,
            int x2,
            int y2,
            int z2,
            int x3,
            int y3,
            int z3
    ) {
        long ax = x1 - x0;
        long ay = y1 - y0;
        long az = z1 - z0;
        long bx = x2 - x0;
        long by = y2 - y0;
        long bz = z2 - z0;
        long cx = x3 - x0;
        long cy = y3 - y0;
        long cz = z3 - z0;
        double first = crossLength(ax, ay, az, bx, by, bz) * 0.5D;
        double second = crossLength(bx, by, bz, cx, cy, cz) * 0.5D;
        return Math.round(first + second);
    }

    private static double crossLength(long ax, long ay, long az, long bx, long by, long bz) {
        long cx = ay * bz - az * by;
        long cy = az * bx - ax * bz;
        long cz = ax * by - ay * bx;
        return Math.sqrt((double) cx * cx + (double) cy * cy + (double) cz * cz);
    }

    private static long polygonArea2d(
            int x0,
            int y0,
            int x1,
            int y1,
            int x2,
            int y2,
            int x3,
            int y3
    ) {
        long twiceArea = (long) x0 * y1 - (long) y0 * x1
                + (long) x1 * y2 - (long) y1 * x2
                + (long) x2 * y3 - (long) y2 * x3
                + (long) x3 * y0 - (long) y3 * x0;
        return Math.abs(twiceArea) / 2L;
    }

    private static Vertex vertex(
            int baseX,
            int baseY,
            int baseZ,
            SpotSurfaceFrame.LocalCoordinates local,
            int textureU,
            int textureV
    ) {
        return new Vertex(
                baseX + unitToByte(local.x()),
                baseY + unitToByte(local.y()),
                baseZ + unitToByte(local.z()),
                textureU,
                textureV
        );
    }

    private static double byteToUnit(int value) {
        return value / 255.0D;
    }

    private static int unitToByte(double value) {
        return Math.max(0, Math.min(PROOF_SCALE, (int) Math.round(value * PROOF_SCALE)));
    }

    private static int sliceUnitToProof(int value) {
        return Math.max(
                0,
                Math.min(
                        PROOF_SCALE,
                        (int) Math.round(value * (PROOF_SCALE / (double) SpotRecord.SLICE_QUANTIZATION_LEVEL))
                )
        );
    }

    private static String formatSpot(SpotRecord spot) {
        return String.format(
                Locale.ROOT,
                "%d,%d,%d/%s/%s",
                spot.pos().getX(),
                spot.pos().getY(),
                spot.pos().getZ(),
                spot.face().getSerializedName(),
                formatMode(spot.projectionMode())
        );
    }

    private static String formatMode(SpotRecord.ProjectionMode mode) {
        return switch (mode) {
            case CENTERED_QUAD -> "center";
            case FOOTPRINT_SLICE -> "slice";
            case FOOTPRINT_QUAD -> "quad";
            case DEBUG_FACE_CENTER -> "debug";
        };
    }

    private SpotProjectionContinuity() {
    }
}
