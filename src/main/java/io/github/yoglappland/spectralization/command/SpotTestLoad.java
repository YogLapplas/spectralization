package io.github.yoglappland.spectralization.command;

import java.util.Locale;

public enum SpotTestLoad {
    LIGHTWEIGHT,
    STRESS;

    public SpotTestLoad toggle() {
        return this == LIGHTWEIGHT ? STRESS : LIGHTWEIGHT;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "item.spectralization.spot_test.load." + serializedName();
    }

    public static SpotTestLoad byName(String name) {
        if (name != null) {
            for (SpotTestLoad load : values()) {
                if (load.serializedName().equals(name)) {
                    return load;
                }
            }
        }
        return LIGHTWEIGHT;
    }
}
