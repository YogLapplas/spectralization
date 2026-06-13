package io.github.yoglappland.spectralization.optics.fiber;

public record FiberNodeProfile(
        int tier,
        double maxSegmentLength,
        double maxPower,
        int maxConnections
) {
    public static final FiberNodeProfile BASIC_INTERFACE = new FiberNodeProfile(1, 24.0, 64.0, 8);
    public static final FiberNodeProfile BASIC_RELAY = new FiberNodeProfile(1, 24.0, 64.0, 8);

    public FiberNodeProfile {
        if (tier < 1) {
            throw new IllegalArgumentException("Fiber node tier must be positive");
        }

        if (!Double.isFinite(maxSegmentLength) || maxSegmentLength <= 0.0) {
            throw new IllegalArgumentException("Fiber segment length must be finite and positive");
        }

        if (!Double.isFinite(maxPower) || maxPower <= 0.0) {
            throw new IllegalArgumentException("Fiber power capacity must be finite and positive");
        }

        if (maxConnections < 1) {
            throw new IllegalArgumentException("Fiber connection capacity must be positive");
        }
    }
}
