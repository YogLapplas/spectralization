package io.github.yoglappland.spectralization.optics.medium;

import io.github.yoglappland.spectralization.optics.FrequencyKey;
import java.util.Objects;

public record OpticalMediumSample(FrequencyKey frequency, OpticalMediumResponse response) {
    public OpticalMediumSample {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(response, "response");
    }
}
