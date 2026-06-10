package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Comparator;
import java.util.Objects;

public record SpectralPowerLane(FrequencyKey frequency, CoherenceKind coherence) {
    public static final Comparator<SpectralPowerLane> COMPARATOR = Comparator
            .comparing((SpectralPowerLane lane) -> lane.coherence().ordinal())
            .thenComparing(lane -> lane.frequency().region().ordinal())
            .thenComparing(lane -> lane.frequency().bin());

    public SpectralPowerLane {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(coherence, "coherence");
    }
}
