package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record OpticalMaterialSample(FrequencyKey frequency, OpticalMaterialResponse response) {
    public OpticalMaterialSample {
        Objects.requireNonNull(frequency, "frequency");
        Objects.requireNonNull(response, "response");
    }
}
