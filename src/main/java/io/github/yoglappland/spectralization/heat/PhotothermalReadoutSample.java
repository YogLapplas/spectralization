package io.github.yoglappland.spectralization.heat;

import io.github.yoglappland.spectralization.optics.BeamEnvelope;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Map;
import java.util.Objects;

public record PhotothermalReadoutSample(
        double power,
        double coherentPower,
        double strayPower,
        BeamEnvelope envelope,
        Map<FrequencyKey, Double> powerByFrequency,
        boolean reliable,
        long step
) {
    public PhotothermalReadoutSample {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(powerByFrequency, "powerByFrequency");
        requireNonNegative(power, "power");
        requireNonNegative(coherentPower, "coherent power");
        requireNonNegative(strayPower, "stray power");
        powerByFrequency = powerByFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()) && entry.getValue() > 0.0)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        Double::sum
                ));
    }

    public static PhotothermalReadoutSample zero(boolean reliable, long step) {
        return new PhotothermalReadoutSample(
                0.0,
                0.0,
                0.0,
                BeamEnvelope.DEFAULT_COLLIMATED,
                Map.of(),
                reliable,
                step
        );
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Photothermal readout " + label + " must be finite and non-negative");
        }
    }
}
