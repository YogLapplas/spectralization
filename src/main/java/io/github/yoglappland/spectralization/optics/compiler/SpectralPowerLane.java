package io.github.yoglappland.spectralization.optics.compiler;

import io.github.yoglappland.spectralization.optics.CoherenceKind;
import io.github.yoglappland.spectralization.optics.FrequencyKey;
import io.github.yoglappland.spectralization.optics.geometry.BeamProfileKey;
import java.util.Comparator;
import java.util.Objects;

public record SpectralPowerLane(FrequencyKey frequency, CoherenceKind coherence, BeamProfileKey profile) {
    public static final Comparator<SpectralPowerLane> COMPARATOR = Comparator
            .comparing((SpectralPowerLane lane) -> lane.coherence().ordinal())
            .thenComparing(lane -> lane.frequency().region().ordinal())
            .thenComparing(lane -> lane.frequency().bin())
            .thenComparing(SpectralPowerLane::profile, BeamProfileKey.COMPARATOR);

    public SpectralPowerLane(FrequencyKey frequency, CoherenceKind coherence) {
        this(frequency, coherence, BeamProfileKey.DEFAULT_COLLIMATED);
    }

    public SpectralPowerLane {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(coherence, "coherence");
        Objects.requireNonNull(profile, "profile");
    }

    public SpectralPowerLane withProfile(BeamProfileKey profile) {
        return new SpectralPowerLane(frequency, coherence, profile);
    }
}
