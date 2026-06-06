package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record FrequencyKey(SpectralRegion region, int bin) {
    public static final FrequencyKey DEBUG_VISIBLE = new FrequencyKey(SpectralRegion.VISIBLE, 32);

    public FrequencyKey {
        Objects.requireNonNull(region, "region");

        if (bin < 0) {
            throw new IllegalArgumentException("Frequency bin must not be negative");
        }
    }

    public static FrequencyKey visible(int bin) {
        return new FrequencyKey(SpectralRegion.VISIBLE, bin);
    }

    public String id() {
        return region.id() + ":" + bin;
    }
}
