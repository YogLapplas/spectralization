package io.github.yoglappland.spectralization.optics.projection;

import io.github.yoglappland.spectralization.optics.SpotRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;

public final class SpotProjectionContinuity {
    private static final int PROOF_SCALE = SpotRecord.QUAD_QUANTIZATION_LEVEL;
    private static final int POSITION_EPSILON_UNIT = 2;
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

                for (int leftEdgeIndex = 0; leftEdgeIndex < 4; leftEdgeIndex++) {
                    Edge leftEdge = left.edge(leftEdgeIndex);

                    for (int rightEdgeIndex = 0; rightEdgeIndex < 4; rightEdgeIndex++) {
                        Edge rightEdge = right.edge(rightEdgeIndex);

                        if (!leftEdge.sameWorldEdge(rightEdge)) {
                            continue;
                        }

                        sharedEdges++;

                        int textureGap = leftEdge.textureGap(rightEdge);
                        if (textureGap > TEXTURE_EPSILON_UNIT) {
                            mismatchCount++;
                            maxTextureGap = Math.max(maxTextureGap, textureGap);

                            if (mismatches.size() < maxExamples) {
                                mismatches.add(new Mismatch(
                                        left.spot(),
                                        leftEdgeIndex,
                                        right.spot(),
                                        rightEdgeIndex,
                                        textureGap
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

    public record Mismatch(SpotRecord first, int firstEdge, SpotRecord second, int secondEdge, int textureGap) {
        public String format() {
            return "first=" + formatSpot(first) + "/edge" + firstEdge
                    + " second=" + formatSpot(second) + "/edge" + secondEdge
                    + " texture_gap=" + textureGap;
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
    }

    private record Edge(Vertex a, Vertex b) {
        private boolean sameWorldEdge(Edge other) {
            return worldClose(a, other.a) && worldClose(b, other.b)
                    || worldClose(a, other.b) && worldClose(b, other.a);
        }

        private boolean sameTextureEdge(Edge other) {
            return textureClose(a, other.a) && textureClose(b, other.b)
                    || textureClose(a, other.b) && textureClose(b, other.a);
        }

        private int textureGap(Edge other) {
            int sameDirectionGap = Math.max(textureDistance(a, other.a), textureDistance(b, other.b));
            int oppositeDirectionGap = Math.max(textureDistance(a, other.b), textureDistance(b, other.a));
            return Math.min(sameDirectionGap, oppositeDirectionGap);
        }
    }

    private record Vertex(int x, int y, int z, int u, int v) {
    }

    private static boolean worldClose(Vertex left, Vertex right) {
        return Math.abs(left.x() - right.x()) <= POSITION_EPSILON_UNIT
                && Math.abs(left.y() - right.y()) <= POSITION_EPSILON_UNIT
                && Math.abs(left.z() - right.z()) <= POSITION_EPSILON_UNIT;
    }

    private static boolean textureClose(Vertex left, Vertex right) {
        return Math.abs(left.u() - right.u()) <= TEXTURE_EPSILON_UNIT
                && Math.abs(left.v() - right.v()) <= TEXTURE_EPSILON_UNIT;
    }

    private static int textureDistance(Vertex left, Vertex right) {
        return Math.max(Math.abs(left.u() - right.u()), Math.abs(left.v() - right.v()));
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
