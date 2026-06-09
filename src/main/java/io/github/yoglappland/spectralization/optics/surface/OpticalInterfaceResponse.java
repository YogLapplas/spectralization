package io.github.yoglappland.spectralization.optics.surface;

public record OpticalInterfaceResponse(double transmittance, double reflectance, double absorption) {
    private static final double EPSILON = 1.0E-6;

    public OpticalInterfaceResponse {
        validateFraction(transmittance, "transmittance");
        validateFraction(reflectance, "reflectance");
        validateFraction(absorption, "absorption");

        if (transmittance + reflectance + absorption > 1.0 + EPSILON) {
            throw new IllegalArgumentException("Interface response T + R + A must be at most 1");
        }
    }

    public static OpticalInterfaceResponse of(double transmittance, double reflectance, double absorption) {
        return new OpticalInterfaceResponse(transmittance, reflectance, absorption);
    }

    private static void validateFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Interface response " + name + " must be finite and between 0 and 1");
        }
    }
}
