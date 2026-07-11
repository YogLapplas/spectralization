package io.github.yoglappland.spectralization.command;

import java.util.Locale;

public enum SpotTestMode {
    QUICK,
    PARTIAL_GEOMETRY,
    PERFORMANCE,
    FULL_SUITE;

    private static final SpotTestMode[] VALUES = values();

    public SpotTestMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "item.spectralization.spot_test.mode." + serializedName();
    }

    public static SpotTestMode byName(String name) {
        if (name != null) {
            for (SpotTestMode mode : VALUES) {
                if (mode.serializedName().equals(name)) {
                    return mode;
                }
            }
        }
        return QUICK;
    }
}
