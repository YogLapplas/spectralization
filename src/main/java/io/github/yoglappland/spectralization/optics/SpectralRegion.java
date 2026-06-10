package io.github.yoglappland.spectralization.optics;

public enum SpectralRegion {
    RADIO("radio", 16),
    MICROWAVE("microwave", 16),
    THZ("thz", 16),
    INFRARED("infrared", 32),
    VISIBLE("visible", 32),
    ULTRAVIOLET("ultraviolet", 32),
    FAR_ULTRAVIOLET("far_ultraviolet", 16),
    XRAY("xray", 16),
    GAMMA("gamma", 16);

    private final String id;
    private final int defaultBins;

    SpectralRegion(String id, int defaultBins) {
        this.id = id;
        this.defaultBins = defaultBins;
    }

    public String id() {
        return id;
    }

    public int defaultBins() {
        return defaultBins;
    }
}
