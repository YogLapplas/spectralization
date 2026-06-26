package io.github.yoglappland.spectralization.optics.fiber;

public record FiberNodeProfile(
        int tier,
        double maxSegmentLength,
        double maxPower,
        int maxConnections,
        double coreRadius,
        double numericalAperture,
        double transmissionPerBlock,
        double bendTransmissionPerRightAngle
) {
    public static final FiberNodeProfile BASIC_INTERFACE = new FiberNodeProfile(1, 24.0, 64.0, 8, 0.125, 0.04, 1.0, 1.0);
    public static final FiberNodeProfile BASIC_RELAY = new FiberNodeProfile(1, 24.0, 64.0, 8, 0.125, 0.04, 1.0, 1.0);

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

        if (!Double.isFinite(coreRadius) || coreRadius <= 0.0) {
            throw new IllegalArgumentException("Fiber core radius must be finite and positive");
        }

        if (!Double.isFinite(numericalAperture) || numericalAperture <= 0.0) {
            throw new IllegalArgumentException("Fiber numerical aperture must be finite and positive");
        }

        if (!Double.isFinite(transmissionPerBlock) || transmissionPerBlock <= 0.0 || transmissionPerBlock > 1.0) {
            throw new IllegalArgumentException("Fiber transmission per block must be finite and in (0, 1]");
        }

        if (!Double.isFinite(bendTransmissionPerRightAngle)
                || bendTransmissionPerRightAngle <= 0.0
                || bendTransmissionPerRightAngle > 1.0) {
            throw new IllegalArgumentException("Fiber bend transmission must be finite and in (0, 1]");
        }
    }
}
