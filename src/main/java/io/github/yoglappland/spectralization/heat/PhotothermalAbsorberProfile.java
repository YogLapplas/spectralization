package io.github.yoglappland.spectralization.heat;

import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.Map;
import java.util.Objects;

public record PhotothermalAbsorberProfile(
        PhotothermalAbsorptionCurve absorptionCurve,
        double heatConversionEfficiency,
        double absorptionRadius,
        double minFullEfficiencyRadius,
        double maxFullEfficiencyRadius,
        double cutoffRadius,
        double maxUniformIrradiance,
        double coherentHotspotPenalty
) {
    public static final PhotothermalAbsorberProfile DEFAULT_MACHINE_FACE = new PhotothermalAbsorberProfile(
            PhotothermalAbsorptionCurve.byRegion(
                    0.25,
                    Map.of(
                            SpectralRegion.INFRARED, 0.70,
                            SpectralRegion.VISIBLE, 0.55,
                            SpectralRegion.ULTRAVIOLET, 0.35
                    )
            ),
            0.70,
            0.45,
            0.45,
            0.45,
            1.35,
            120.0,
            0.75
    );

    public static final PhotothermalAbsorberProfile EARLY_ABSORBING_COATING = new PhotothermalAbsorberProfile(
            PhotothermalAbsorptionCurve.byRegion(
                    0.40,
                    Map.of(
                            SpectralRegion.INFRARED, 0.78,
                            SpectralRegion.VISIBLE, 0.62,
                            SpectralRegion.ULTRAVIOLET, 0.38
                    )
            ),
            0.72,
            0.42,
            0.42,
            0.42,
            1.25,
            55.0,
            1.20
    );

    public PhotothermalAbsorberProfile {
        Objects.requireNonNull(absorptionCurve, "absorptionCurve");
        requireFraction(heatConversionEfficiency, "heat conversion efficiency");
        requirePositive(absorptionRadius, "absorption radius");
        requirePositive(minFullEfficiencyRadius, "minimum full-efficiency radius");
        requirePositive(maxFullEfficiencyRadius, "maximum full-efficiency radius");
        requirePositive(cutoffRadius, "cutoff radius");
        requirePositive(maxUniformIrradiance, "maximum uniform irradiance");

        if (minFullEfficiencyRadius > maxFullEfficiencyRadius) {
            throw new IllegalArgumentException("Minimum full-efficiency radius must not exceed maximum radius");
        }

        if (absorptionRadius < minFullEfficiencyRadius || absorptionRadius > maxFullEfficiencyRadius) {
            throw new IllegalArgumentException("Absorption radius must sit inside the full-efficiency radius range");
        }

        if (maxFullEfficiencyRadius >= cutoffRadius) {
            throw new IllegalArgumentException("Cutoff radius must be greater than maximum full-efficiency radius");
        }

        if (!Double.isFinite(coherentHotspotPenalty) || coherentHotspotPenalty < 0.0) {
            throw new IllegalArgumentException("Coherent hotspot penalty must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("Photothermal " + label + " must be between 0 and 1");
        }
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("Photothermal " + label + " must be finite and positive");
        }
    }
}
