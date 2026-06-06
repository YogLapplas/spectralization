package io.github.yoglappland.spectralization.optics;

public record OpticalTransmission(boolean transmits, double powerFactor) {
    public static final OpticalTransmission BLOCKED = new OpticalTransmission(false, 0.0);
    public static final OpticalTransmission FULL = new OpticalTransmission(true, 1.0);

    public OpticalTransmission {
        if (!Double.isFinite(powerFactor) || powerFactor < 0.0) {
            throw new IllegalArgumentException("Optical transmission power factor must be finite and non-negative");
        }
    }
}
