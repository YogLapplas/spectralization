package io.github.yoglappland.spectralization.optics.projection;

import net.minecraft.core.Direction;

final class SpotProjectionPatch {
    private static final double FACE_EPSILON = 1.0E-6D;

    static Patch oriented(
            Direction face,
            LocalPoint p0,
            TexturePoint t0,
            LocalPoint p1,
            TexturePoint t1,
            LocalPoint p2,
            TexturePoint t2,
            LocalPoint p3,
            TexturePoint t3
    ) {
        if (!liesOnFace(face, p0, p1, p2, p3)) {
            return null;
        }

        double dot = faceNormalDot(face, p0, p1, p2);

        if (Math.abs(dot) <= FACE_EPSILON) {
            return null;
        }

        if (dot >= 0.0D) {
            return new Patch(p0, t0, p1, t1, p2, t2, p3, t3);
        }

        return new Patch(p1, t1, p0, t0, p3, t3, p2, t2);
    }

    static LocalPoint localPoint(
            Direction travelDirection,
            Direction uDirection,
            Direction vDirection,
            double travelLocal,
            double uLocal,
            double vLocal
    ) {
        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;

        switch (travelDirection.getAxis()) {
            case X -> x = SpotSurfaceFrame.axisLocal(travelDirection, travelLocal);
            case Y -> y = SpotSurfaceFrame.axisLocal(travelDirection, travelLocal);
            case Z -> z = SpotSurfaceFrame.axisLocal(travelDirection, travelLocal);
        }

        switch (uDirection.getAxis()) {
            case X -> x = SpotSurfaceFrame.axisLocal(uDirection, uLocal);
            case Y -> y = SpotSurfaceFrame.axisLocal(uDirection, uLocal);
            case Z -> z = SpotSurfaceFrame.axisLocal(uDirection, uLocal);
        }

        switch (vDirection.getAxis()) {
            case X -> x = SpotSurfaceFrame.axisLocal(vDirection, vLocal);
            case Y -> y = SpotSurfaceFrame.axisLocal(vDirection, vLocal);
            case Z -> z = SpotSurfaceFrame.axisLocal(vDirection, vLocal);
        }

        return new LocalPoint(clamp01(x), clamp01(y), clamp01(z));
    }

    static boolean liesOnFace(
            Direction face,
            LocalPoint p0,
            LocalPoint p1,
            LocalPoint p2,
            LocalPoint p3
    ) {
        double expected = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0D : 0.0D;
        return Math.abs(axisCoordinate(p0, face) - expected) <= FACE_EPSILON
                && Math.abs(axisCoordinate(p1, face) - expected) <= FACE_EPSILON
                && Math.abs(axisCoordinate(p2, face) - expected) <= FACE_EPSILON
                && Math.abs(axisCoordinate(p3, face) - expected) <= FACE_EPSILON;
    }

    static double faceNormalDot(Direction face, LocalPoint p0, LocalPoint p1, LocalPoint p2) {
        double ax = p1.x() - p0.x();
        double ay = p1.y() - p0.y();
        double az = p1.z() - p0.z();
        double bx = p2.x() - p1.x();
        double by = p2.y() - p1.y();
        double bz = p2.z() - p1.z();
        double nx = ay * bz - az * by;
        double ny = az * bx - ax * bz;
        double nz = ax * by - ay * bx;
        return nx * face.getStepX() + ny * face.getStepY() + nz * face.getStepZ();
    }

    private static double axisCoordinate(LocalPoint point, Direction face) {
        return switch (face.getAxis()) {
            case X -> point.x();
            case Y -> point.y();
            case Z -> point.z();
        };
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0D;
        }

        return Math.max(0.0D, Math.min(1.0D, value));
    }

    record Patch(
            LocalPoint p0,
            TexturePoint t0,
            LocalPoint p1,
            TexturePoint t1,
            LocalPoint p2,
            TexturePoint t2,
            LocalPoint p3,
            TexturePoint t3
    ) {
    }

    record LocalPoint(double x, double y, double z) {
    }

    record TexturePoint(double u, double v) {
        TexturePoint {
            u = clamp01(u);
            v = clamp01(v);
        }
    }

    private SpotProjectionPatch() {
    }
}
