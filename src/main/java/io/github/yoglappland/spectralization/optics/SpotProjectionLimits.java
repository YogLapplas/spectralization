package io.github.yoglappland.spectralization.optics;

public final class SpotProjectionLimits {
    public static final int MAX_SPOTS_PER_OWNER = 1 << 15;
    public static final int SPOTS_PER_PAYLOAD_CHUNK = 1 << 11;
    public static final int MAX_PAYLOAD_CHUNKS =
            MAX_SPOTS_PER_OWNER / SPOTS_PER_PAYLOAD_CHUNK;

    private SpotProjectionLimits() {
    }
}
