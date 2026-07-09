package io.github.yoglappland.spectralization.optics.validation;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileShape;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileTransfer;

public final class BeamProfileQuantizationVerifier {
    private static final double EPSILON = 1.0E-9D;

    public static void main(String[] args) {
        verifyHalfBlockRadiusRoundTrip();
        verifySubBucketFreeSpaceDoesNotInflateRadius();
        verifyUnderfilledApertureDoesNotInflateRadius();
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

    private static void expectClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new IllegalStateException(label + " expected " + expected + " but got " + actual);
        }
    }

    private BeamProfileQuantizationVerifier() {
        throw new AssertionError();
    }
}
