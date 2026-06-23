package io.github.yoglappland.spectralization.optics.geometry;

import java.util.Objects;

public record BeamProfileTransfer(BeamProfileKey outputProfile, double gain) {
    public static final BeamProfileTransfer IDENTITY =
            new BeamProfileTransfer(BeamProfileKey.DEFAULT_COLLIMATED, 1.0D);

    public BeamProfileTransfer {
        Objects.requireNonNull(outputProfile, "outputProfile");

        if (!Double.isFinite(gain) || gain < 0.0D || gain > 1.0D) {
            throw new IllegalArgumentException("Profile transfer gain must be finite and between 0 and 1");
        }
    }

    public static BeamProfileTransfer of(BeamProfileKey outputProfile, double gain) {
        return new BeamProfileTransfer(outputProfile, BeamGeometryOps.clamp01(gain));
    }
}
