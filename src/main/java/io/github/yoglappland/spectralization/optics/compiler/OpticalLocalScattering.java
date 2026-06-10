package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Map;
import java.util.Objects;

public record OpticalLocalScattering(
        OpticalComponentPort inputPort,
        OpticalComponentPort outputPort,
        double sampleInputPower,
        double sampleOutputPower,
        Map<FrequencyKey, Double> sampleInputPowerByFrequency,
        Map<FrequencyKey, Double> sampleOutputPowerByFrequency
) {
    public OpticalLocalScattering(
            OpticalComponentPort inputPort,
            OpticalComponentPort outputPort,
            double sampleInputPower,
            double sampleOutputPower
    ) {
        this(inputPort, outputPort, sampleInputPower, sampleOutputPower, Map.of(), Map.of());
    }

    public OpticalLocalScattering {
        Objects.requireNonNull(inputPort, "inputPort");
        Objects.requireNonNull(outputPort, "outputPort");
        Objects.requireNonNull(sampleInputPowerByFrequency, "sampleInputPowerByFrequency");
        Objects.requireNonNull(sampleOutputPowerByFrequency, "sampleOutputPowerByFrequency");

        if (!Double.isFinite(sampleInputPower) || sampleInputPower < 0.0) {
            throw new IllegalArgumentException("Local scattering input power must be finite and non-negative");
        }

        if (!Double.isFinite(sampleOutputPower) || sampleOutputPower < 0.0) {
            throw new IllegalArgumentException("Local scattering output power must be finite and non-negative");
        }

        sampleInputPowerByFrequency = Map.copyOf(sampleInputPowerByFrequency);
        sampleOutputPowerByFrequency = Map.copyOf(sampleOutputPowerByFrequency);
    }
}
