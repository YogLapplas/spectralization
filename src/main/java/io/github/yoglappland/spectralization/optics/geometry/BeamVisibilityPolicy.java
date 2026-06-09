package io.github.yoglappland.spectralization.optics.geometry;

public final class BeamVisibilityPolicy {
    private static final double NATURAL_SCATTER_MIN_IRRADIANCE = 0.5;
    private static final double NATURAL_PLASMA_MIN_IRRADIANCE = 25_000.0;

    public static BeamVisibilityKind naturalVisibility(BeamGeometrySample sample, boolean scatteringMedium) {
        if (sample.irradiance() >= NATURAL_PLASMA_MIN_IRRADIANCE) {
            return BeamVisibilityKind.NATURAL_PLASMA;
        }

        if (scatteringMedium && sample.irradiance() >= NATURAL_SCATTER_MIN_IRRADIANCE) {
            return BeamVisibilityKind.NATURAL_SCATTER;
        }

        return BeamVisibilityKind.HIDDEN;
    }

    public static BeamVisibilityKind hudVisibility(boolean hudActive, BeamGeometrySample sample) {
        if (!hudActive || sample.visualLevel() <= 0) {
            return BeamVisibilityKind.HIDDEN;
        }

        return BeamVisibilityKind.HUD;
    }

    private BeamVisibilityPolicy() {
    }
}
