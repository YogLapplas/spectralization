package io.github.yoglappland.spectralization.optics.geometry;

public record BeamProfileReadoutSample(
        double power,
        double coherentPower,
        double strayPower,
        double radius,
        double divergence,
        double irradiance,
        double beamQuality,
        double scatter,
        int visualLevel,
        boolean reliable,
        long step
) {
    public BeamProfileReadoutSample {
        requireNonNegative(power, "Beam profiler power");
        requireNonNegative(coherentPower, "Beam profiler coherent power");
        requireNonNegative(strayPower, "Beam profiler stray power");
        requireNonNegative(radius, "Beam profiler radius");
        requireNonNegative(divergence, "Beam profiler divergence");
        requireNonNegative(irradiance, "Beam profiler irradiance");
        requireNonNegative(beamQuality, "Beam profiler beam quality");
        requireNonNegative(scatter, "Beam profiler scatter");

        if (visualLevel < 0) {
            throw new IllegalArgumentException("Beam profiler visual level must not be negative");
        }
    }

    public static BeamProfileReadoutSample zero(boolean reliable, long step) {
        return new BeamProfileReadoutSample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0, reliable, step);
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
