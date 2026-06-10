package io.github.yoglappland.spectralization.optics.cache;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Map;
import java.util.Objects;

public record SpectralReadoutSample(
        Map<FrequencyKey, Double> powerByFrequency,
        boolean reliable,
        long step
) {
    public SpectralReadoutSample {
        Objects.requireNonNull(powerByFrequency, "powerByFrequency");
        powerByFrequency = powerByFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()) && entry.getValue() > 0.0)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        Double::sum
                ));
    }

    public static SpectralReadoutSample zero(boolean reliable, long step) {
        return new SpectralReadoutSample(Map.of(), reliable, step);
    }

    public double totalPower() {
        double power = 0.0;

        for (double componentPower : powerByFrequency.values()) {
            power += componentPower;
        }

        return power;
    }
}
