package io.github.yoglappland.spectralization.optics;

import java.util.Objects;
import net.minecraft.core.Direction;

public record PlaneWaveComponent(
        FrequencyKey frequency,
        double power,
        Direction direction,
        CoherenceKind coherence
) {
    public PlaneWaveComponent {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(coherence, "coherence");

        if (!Double.isFinite(power) || power < 0.0) {
            throw new IllegalArgumentException("Beam component power must be finite and non-negative");
        }
    }

    public PlaneWaveComponent withPower(double power) {
        return new PlaneWaveComponent(frequency, power, direction, coherence);
    }

    public PlaneWaveComponent withDirection(Direction direction) {
        return new PlaneWaveComponent(frequency, power, direction, coherence);
    }
}
