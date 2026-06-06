package io.github.yoglappland.spectralization.optics;

public record OpticalMaterialResponse(double transmittance, double reflectance, double absorption) {
    private static final double EPSILON = 1.0E-6;

    public OpticalMaterialResponse {
        validateFraction(transmittance, "transmittance");
        validateFraction(reflectance, "reflectance");
        validateFraction(absorption, "absorption");

        if (transmittance + reflectance + absorption > 1.0 + EPSILON) {
            throw new IllegalArgumentException("Optical response T + R + A must be at most 1");
        }
    }

    public static OpticalMaterialResponse of(double transmittance, double reflectance, double absorption) {
        return new OpticalMaterialResponse(transmittance, reflectance, absorption);
    }

    public static OpticalMaterialResponse lerp(
            OpticalMaterialResponse left,
            OpticalMaterialResponse right,
            double factor
    ) {
        return new OpticalMaterialResponse(
                lerp(left.transmittance, right.transmittance, factor),
                lerp(left.reflectance, right.reflectance, factor),
                lerp(left.absorption, right.absorption, factor)
        );
    }

    private static double lerp(double left, double right, double factor) {
        return left + (right - left) * factor;
    }

    private static void validateFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Optical response " + name + " must be finite and between 0 and 1");
        }
    }
}
