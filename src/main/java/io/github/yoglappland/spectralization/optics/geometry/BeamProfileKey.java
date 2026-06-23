package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

public record BeamProfileKey(
        int radiusBucket,
        int convergenceBucket,
        int divergenceBucket,
        int qualityBucket,
        int scatterBucket,
        int modeM,
        int modeN,
        boolean guided
) {
    private static final int BUCKETS_PER_OCTAVE = 8;
    private static final int CORRELATION_BUCKETS = 16;
    private static final int MAX_RADIUS_BUCKET = 128;
    private static final int MAX_DIVERGENCE_BUCKET = 160;
    private static final int MAX_QUALITY_BUCKET = 64;
    private static final int MAX_SCATTER_BUCKET = 32;
    private static final double MIN_RADIUS = BeamGeometryOps.MIN_RADIUS;
    private static final double MAX_RADIUS = 16.0D;
    private static final double MIN_DIVERGENCE = 1.0E-5D;
    private static final double MAX_DIVERGENCE = 2.0D;

    public static final BeamProfileKey DEFAULT_COLLIMATED =
            BeamProfileShape.collimated(0.25D).toKey();
    public static final Comparator<BeamProfileKey> COMPARATOR = Comparator
            .comparingInt(BeamProfileKey::radiusBucket)
            .thenComparingInt(BeamProfileKey::convergenceBucket)
            .thenComparingInt(BeamProfileKey::divergenceBucket)
            .thenComparingInt(BeamProfileKey::qualityBucket)
            .thenComparingInt(BeamProfileKey::scatterBucket)
            .thenComparingInt(BeamProfileKey::modeM)
            .thenComparingInt(BeamProfileKey::modeN)
            .thenComparing(BeamProfileKey::guided);

    public BeamProfileKey {
        radiusBucket = clamp(radiusBucket, 0, MAX_RADIUS_BUCKET);
        convergenceBucket = clamp(convergenceBucket, -CORRELATION_BUCKETS, CORRELATION_BUCKETS);
        divergenceBucket = clamp(divergenceBucket, 0, MAX_DIVERGENCE_BUCKET);
        qualityBucket = clamp(qualityBucket, 0, MAX_QUALITY_BUCKET);
        scatterBucket = clamp(scatterBucket, 0, MAX_SCATTER_BUCKET);
        modeM = Math.max(0, modeM);
        modeN = Math.max(0, modeN);
    }

    public static BeamProfileKey fromEnvelope(BeamEnvelope envelope) {
        return BeamProfileShape.fromEnvelope(envelope).toKey();
    }

    public static BeamProfileKey collimated(double radius) {
        return BeamProfileShape.collimated(radius).toKey();
    }

    public static BeamProfileKey conservativeMerge(BeamProfileKey left, BeamProfileKey right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");

        return new BeamProfileKey(
                Math.max(left.radiusBucket, right.radiusBucket),
                Math.max(left.convergenceBucket, right.convergenceBucket),
                Math.max(left.divergenceBucket, right.divergenceBucket),
                Math.max(left.qualityBucket, right.qualityBucket),
                Math.max(left.scatterBucket, right.scatterBucket),
                Math.max(left.modeM, right.modeM),
                Math.max(left.modeN, right.modeN),
                left.guided && right.guided
        );
    }

    public static BeamProfileKey conservativeMerge(Collection<BeamProfileKey> profiles) {
        Objects.requireNonNull(profiles, "profiles");

        BeamProfileKey merged = null;
        for (BeamProfileKey profile : profiles) {
            merged = merged == null ? Objects.requireNonNull(profile, "profile") : conservativeMerge(merged, profile);
        }

        if (merged == null) {
            throw new IllegalArgumentException("At least one profile is required for conservative merge");
        }

        return merged;
    }

    public BeamProfileShape toShape() {
        double radius = radiusFromBucket(radiusBucket);
        double divergence = divergenceFromBucket(divergenceBucket);
        double correlation = (double) convergenceBucket / (double) CORRELATION_BUCKETS;
        double r2 = radius * radius;
        double theta2 = divergence * divergence;
        double rTheta = correlation * Math.sqrt(Math.max(0.0D, r2 * theta2));
        double quality = 1.0D + (double) qualityBucket / 4.0D;
        double scatter = (double) scatterBucket / (double) MAX_SCATTER_BUCKET;
        return new BeamProfileShape(r2, rTheta, theta2, quality, scatter, modeM, modeN, guided);
    }

    public BeamEnvelope toEnvelope() {
        return toShape().toEnvelope();
    }

    static BeamProfileKey fromShape(BeamProfileShape shape) {
        double radius = Math.sqrt(Math.max(0.0D, shape.r2()));
        double divergence = Math.sqrt(Math.max(0.0D, shape.theta2()));
        double denominator = Math.sqrt(Math.max(0.0D, shape.r2() * shape.theta2()));
        double correlation = denominator <= 1.0E-12D ? 0.0D : shape.rTheta() / denominator;

        return new BeamProfileKey(
                logBucketCeil(radius, MIN_RADIUS, MAX_RADIUS_BUCKET),
                (int) Math.round(BeamGeometryOps.clamp01((correlation + 1.0D) * 0.5D) * 2.0D * CORRELATION_BUCKETS)
                        - CORRELATION_BUCKETS,
                divergence <= 0.0D ? 0 : logBucketCeil(divergence, MIN_DIVERGENCE, MAX_DIVERGENCE_BUCKET),
                clamp((int) Math.ceil(Math.max(1.0D, shape.quality()) * 4.0D) - 4, 0, MAX_QUALITY_BUCKET),
                clamp((int) Math.ceil(BeamGeometryOps.clamp01(shape.scatter()) * MAX_SCATTER_BUCKET), 0, MAX_SCATTER_BUCKET),
                shape.modeM(),
                shape.modeN(),
                shape.guided()
        );
    }

    private static int logBucketCeil(double value, double minimum, int maxBucket) {
        if (!Double.isFinite(value) || value <= minimum) {
            return 0;
        }

        double octaves = Math.log(value / minimum) / Math.log(2.0D);
        return clamp((int) Math.ceil(octaves * BUCKETS_PER_OCTAVE), 0, maxBucket);
    }

    private static double radiusFromBucket(int bucket) {
        return Math.min(MAX_RADIUS, MIN_RADIUS * Math.pow(2.0D, (double) bucket / BUCKETS_PER_OCTAVE));
    }

    private static double divergenceFromBucket(int bucket) {
        if (bucket <= 0) {
            return 0.0D;
        }

        return Math.min(MAX_DIVERGENCE, MIN_DIVERGENCE * Math.pow(2.0D, (double) bucket / BUCKETS_PER_OCTAVE));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
