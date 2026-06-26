package io.github.yoglappland.spectralization.optics.metasurface;

import net.minecraft.util.Mth;

public record MetamaterialVector(int x, int y, int z) {
    public static final int MIN_VALUE = -7;
    public static final int MAX_VALUE = 8;
    public static final int VALUE_COUNT = MAX_VALUE - MIN_VALUE + 1;

    public MetamaterialVector {
        x = clamp(x);
        y = clamp(y);
        z = clamp(z);
    }

    public int channelIndex() {
        return (x - MIN_VALUE) * VALUE_COUNT * VALUE_COUNT
                + (y - MIN_VALUE) * VALUE_COUNT
                + (z - MIN_VALUE);
    }

    public static MetamaterialVector fromChannelIndex(int channelIndex) {
        int normalized = Math.floorMod(channelIndex, VALUE_COUNT * VALUE_COUNT * VALUE_COUNT);
        int x = normalized / (VALUE_COUNT * VALUE_COUNT) + MIN_VALUE;
        int y = (normalized / VALUE_COUNT) % VALUE_COUNT + MIN_VALUE;
        int z = normalized % VALUE_COUNT + MIN_VALUE;
        return new MetamaterialVector(x, y, z);
    }

    public static int clamp(int value) {
        return Mth.clamp(value, MIN_VALUE, MAX_VALUE);
    }
}
