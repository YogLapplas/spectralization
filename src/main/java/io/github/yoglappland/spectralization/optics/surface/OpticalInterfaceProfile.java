package io.github.yoglappland.spectralization.optics.surface;

public record OpticalInterfaceProfile(double impedanceMismatchReflectance) {
    public static final OpticalInterfaceProfile MATCHED = new OpticalInterfaceProfile(0.0);

    public OpticalInterfaceProfile {
        if (!Double.isFinite(impedanceMismatchReflectance)
                || impedanceMismatchReflectance < 0.0
                || impedanceMismatchReflectance > 1.0) {
            throw new IllegalArgumentException("Interface reflectance must be finite and between 0 and 1");
        }
    }
}
