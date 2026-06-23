package io.github.yoglappland.spectralization.optics.geometry;

import java.util.Objects;

public record PhaseSpaceMap(double a, double b, double c, double d) {
    public static final PhaseSpaceMap IDENTITY = new PhaseSpaceMap(1.0D, 0.0D, 0.0D, 1.0D);

    public PhaseSpaceMap {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(c) || !Double.isFinite(d)) {
            throw new IllegalArgumentException("Phase-space map coefficients must be finite");
        }
    }

    public static PhaseSpaceMap freeSpace(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0D) {
            throw new IllegalArgumentException("Free-space propagation distance must be finite and non-negative");
        }

        return new PhaseSpaceMap(1.0D, distance, 0.0D, 1.0D);
    }

    public static PhaseSpaceMap thinLens(double focalLength) {
        if (!Double.isFinite(focalLength) || focalLength <= 0.0D) {
            throw new IllegalArgumentException("Thin lens focal length must be finite and positive");
        }

        return new PhaseSpaceMap(1.0D, 0.0D, -1.0D / focalLength, 1.0D);
    }

    public PhaseSpaceMap then(PhaseSpaceMap next) {
        Objects.requireNonNull(next, "next");

        return new PhaseSpaceMap(
                next.a * a + next.b * c,
                next.a * b + next.b * d,
                next.c * a + next.d * c,
                next.c * b + next.d * d
        );
    }

    public BeamProfileShape apply(BeamProfileShape shape) {
        Objects.requireNonNull(shape, "shape");

        double outputR2 = a * a * shape.r2()
                + 2.0D * a * b * shape.rTheta()
                + b * b * shape.theta2();
        double outputRTheta = a * c * shape.r2()
                + (a * d + b * c) * shape.rTheta()
                + b * d * shape.theta2();
        double outputTheta2 = c * c * shape.r2()
                + 2.0D * c * d * shape.rTheta()
                + d * d * shape.theta2();

        return new BeamProfileShape(
                Math.max(0.0D, outputR2),
                outputRTheta,
                Math.max(0.0D, outputTheta2),
                shape.quality(),
                shape.scatter(),
                shape.modeM(),
                shape.modeN(),
                shape.guided()
        );
    }

    public PhaseSpaceMapSignature signature() {
        return PhaseSpaceMapSignature.of(this);
    }

    public double determinant() {
        return a * d - b * c;
    }

    public double trace() {
        return a + d;
    }
}
