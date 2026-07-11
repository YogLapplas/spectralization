package io.github.yoglappland.spectralization.optics.validation;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.BeamModel;
import io.github.yoglappland.spectralization.optics.geometry.BeamGeometryOps;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileShape;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;

public final class BeamProfileQuantizationVerifier {
    private static final double EPSILON = 1.0E-9D;

    public static void main(String[] args) {
        verifyHalfBlockRadiusRoundTrip();
        verifySubBucketFreeSpaceDoesNotInflateRadius();
        verifyUnderfilledApertureDoesNotInflateRadius();
        verifyRadiusOnlyPropagationMatchesFullEnvelope();
        verifyOffsetRadiusSharesDepthBoundariesExactly();
    }

    private static void verifyHalfBlockRadiusRoundTrip() {
        BeamEnvelope envelope = BeamEnvelope.collimated(0.5D);
        BeamEnvelope encoded = BeamProfileKey.fromEnvelope(envelope).toEnvelope();

        expectClose(0.5D, encoded.radius(), "half-block radius profile round-trip");
    }

    private static void verifySubBucketFreeSpaceDoesNotInflateRadius() {
        BeamEnvelope envelope = BeamEnvelope.collimated(0.5D);
        BeamEnvelope encoded = BeamProfileShape.fromEnvelope(envelope)
                .propagate(1.0D)
                .toKey()
                .toEnvelope();

        if (encoded.radius() > 0.501D) {
            throw new IllegalStateException(
                    "Sub-bucket passive propagation inflated a half-block radius to " + encoded.radius()
            );
        }
    }

    private static void verifyUnderfilledApertureDoesNotInflateRadius() {
        BeamProfileTransfer transfer = BeamProfileShape.fromEnvelope(BeamEnvelope.collimated(0.5D))
                .apertureClip(1.0D);
        BeamEnvelope encoded = transfer.outputProfile().toEnvelope();

        expectClose(1.0D, transfer.gain(), "underfilled aperture gain");
        expectClose(0.5D, encoded.radius(), "underfilled aperture radius");
    }

    private static void verifyRadiusOnlyPropagationMatchesFullEnvelope() {
        BeamEnvelope[] envelopes = {
                BeamEnvelope.PLANE_WAVE,
                BeamEnvelope.collimated(0.5D),
                new BeamEnvelope(BeamModel.DIVERGING, 0.5D, 0.08D, -2.0D, 1.4D, 0.8D, 0.15D, 1, 0),
                new BeamEnvelope(BeamModel.FOCUSED, 0.75D, 0.06D, 4.0D, 1.2D, 0.65D, 0.05D, 0, 2)
        };
        double[] distances = {0.0D, 0.03125D, 0.5D, 1.0D, 8.0D, 32.0D};

        for (BeamEnvelope envelope : envelopes) {
            BeamGeometryOps.RadiusPropagation prepared = BeamGeometryOps.prepareRadiusPropagation(envelope);
            for (double distance : distances) {
                double fullRadius = BeamGeometryOps.propagate(envelope, distance).radius();
                expectClose(
                        fullRadius,
                        BeamGeometryOps.propagatedRadius(envelope, distance),
                        "radius-only propagation model=" + envelope.model() + " distance=" + distance
                );
                expectClose(
                        fullRadius,
                        prepared.radiusAt(distance),
                        "prepared radius propagation model=" + envelope.model() + " distance=" + distance
                );
            }
        }
    }

    private static void verifyOffsetRadiusSharesDepthBoundariesExactly() {
        BeamEnvelope[] envelopes = {
                BeamEnvelope.PLANE_WAVE,
                BeamEnvelope.collimated(0.5D),
                new BeamEnvelope(BeamModel.DIVERGING, 0.5D, 0.08D, -2.0D, 1.4D, 0.8D, 0.15D, 1, 0),
                new BeamEnvelope(BeamModel.FOCUSED, 0.75D, 0.06D, 4.0D, 1.2D, 0.65D, 0.05D, 0, 2)
        };

        for (BeamEnvelope envelope : envelopes) {
            BeamGeometryOps.RadiusPropagation global = BeamGeometryOps.prepareRadiusPropagation(envelope);
            for (int depth = 0; depth < 32; depth++) {
                double previousExit = global.offset(depth).radiusAt(1.0D);
                double nextEntry = global.offset(depth + 1.0D).radiusAt(0.0D);
                if (Double.doubleToLongBits(previousExit) != Double.doubleToLongBits(nextEntry)) {
                    throw new IllegalStateException(
                            "offset radius propagation disagrees at shared depth boundary model="
                                    + envelope.model() + " depth=" + (depth + 1)
                                    + " previous_exit=" + previousExit + " next_entry=" + nextEntry
                    );
                }
            }
        }
    }

    private static void expectClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new IllegalStateException(label + " expected " + expected + " but got " + actual);
        }
    }

    private BeamProfileQuantizationVerifier() {
        throw new AssertionError();
    }
}
