package io.github.yoglappland.spectralization.optics.geometry;

import java.util.Objects;

public record PhaseSpaceMapSignature(long a, long b, long c, long d) {
    private static final double SCALE = 1_000_000.0D;

    public static final PhaseSpaceMapSignature IDENTITY = of(PhaseSpaceMap.IDENTITY);

    public static PhaseSpaceMapSignature of(PhaseSpaceMap map) {
        Objects.requireNonNull(map, "map");
        return new PhaseSpaceMapSignature(
                quantize(map.a()),
                quantize(map.b()),
                quantize(map.c()),
                quantize(map.d())
        );
    }

    public PhaseSpaceMap toMap() {
        return new PhaseSpaceMap(unquantize(a), unquantize(b), unquantize(c), unquantize(d));
    }

    private static long quantize(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Phase-space map coefficient must be finite");
        }

        return Math.round(value * SCALE);
    }

    private static double unquantize(long value) {
        return (double) value / SCALE;
    }
}
