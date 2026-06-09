package io.github.yoglappland.spectralization.optics.medium;

public record OpticalMediumResponse(
        double refractiveIndex,
        double relativeImpedance,
        double attenuationPerBlock
) {
    public OpticalMediumResponse {
        if (!Double.isFinite(refractiveIndex) || refractiveIndex <= 0.0) {
            throw new IllegalArgumentException("Refractive index must be finite and positive");
        }

        if (!Double.isFinite(relativeImpedance) || relativeImpedance <= 0.0) {
            throw new IllegalArgumentException("Relative impedance must be finite and positive");
        }

        if (!Double.isFinite(attenuationPerBlock) || attenuationPerBlock < 0.0 || attenuationPerBlock > 1.0) {
            throw new IllegalArgumentException("Attenuation per block must be finite and between 0 and 1");
        }
    }

    public static OpticalMediumResponse nonMagnetic(double refractiveIndex, double attenuationPerBlock) {
        return new OpticalMediumResponse(refractiveIndex, 1.0 / refractiveIndex, attenuationPerBlock);
    }

    public static OpticalMediumResponse lerp(
            OpticalMediumResponse left,
            OpticalMediumResponse right,
            double factor
    ) {
        return new OpticalMediumResponse(
                lerp(left.refractiveIndex, right.refractiveIndex, factor),
                lerp(left.relativeImpedance, right.relativeImpedance, factor),
                lerp(left.attenuationPerBlock, right.attenuationPerBlock, factor)
        );
    }

    private static double lerp(double left, double right, double factor) {
        return left + (right - left) * factor;
    }
}
