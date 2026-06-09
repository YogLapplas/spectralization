package io.github.yoglappland.spectralization.optics.geometry;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;

public final class BeamGeometryOps {
    private static final double MIN_RADIUS = 0.03125;

    public static BeamGeometrySample sample(BeamEnvelope envelope, double power) {
        double area = spotArea(envelope);
        double irradiance = area <= 0.0 ? 0.0 : Math.max(0.0, power) / area;

        return new BeamGeometrySample(envelope, area, irradiance, visualLevel(irradiance));
    }

    public static double spotArea(BeamEnvelope envelope) {
        double radius = Math.max(MIN_RADIUS, envelope.radius());
        return Math.PI * radius * radius;
    }

    public static int visualLevel(double irradiance) {
        if (!Double.isFinite(irradiance) || irradiance <= 0.0) {
            return 0;
        }

        return Math.min(8, 1 + (int) Math.floor(Math.log10(1.0 + irradiance) * 2.0));
    }

    private BeamGeometryOps() {
    }
}
