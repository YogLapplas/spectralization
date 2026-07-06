package io.github.yoglappland.spectralization.optics;

import java.util.Objects;

public record FrequencyKey(SpectralRegion region, int bin) {
    public static final FrequencyKey DEBUG_VISIBLE = new FrequencyKey(SpectralRegion.VISIBLE, 16);

    public FrequencyKey {
        Objects.requireNonNull(region, "region");

        if (bin < 0) {
            throw new IllegalArgumentException("Frequency bin must not be negative");
        }

        if (bin >= region.defaultBins()) {
            throw new IllegalArgumentException(
                    "Frequency bin " + bin + " is outside " + region.id() + " bin count " + region.defaultBins()
            );
        }
    }

    public static FrequencyKey visible(int bin) {
        return new FrequencyKey(SpectralRegion.VISIBLE, bin);
    }

    public static FrequencyKey clamped(SpectralRegion region, int bin) {
        Objects.requireNonNull(region, "region");
        return new FrequencyKey(region, Math.max(0, Math.min(bin, region.defaultBins() - 1)));
    }

    public String id() {
        return region.id() + ":" + bin;
    }
}
