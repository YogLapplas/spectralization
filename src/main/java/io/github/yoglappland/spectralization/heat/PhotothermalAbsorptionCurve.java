package io.github.yoglappland.spectralization.heat;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import java.util.Map;
import java.util.Objects;

@FunctionalInterface
public interface PhotothermalAbsorptionCurve {
    double absorption(FrequencyKey frequency);

    static PhotothermalAbsorptionCurve constant(double absorption) {
        double clamped = clamp01(absorption);
        return ignored -> clamped;
    }

    static PhotothermalAbsorptionCurve byRegion(double fallback, Map<SpectralRegion, Double> absorptionByRegion) {
        Objects.requireNonNull(absorptionByRegion, "absorptionByRegion");
        double clampedFallback = clamp01(fallback);
        Map<SpectralRegion, Double> copied = absorptionByRegion.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> clamp01(entry.getValue())
                ));

        return frequency -> copied.getOrDefault(frequency.region(), clampedFallback);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value));
    }
}
