package io.github.yoglappland.spectralization.microlizer;

public enum MicrolizedMachineFaceColor {
    BLUE("blue", 0xFF5D9DFF),
    CYAN("cyan", 0xFF7CEAD9),
    GREEN("green", 0xFF7FDB8A),
    PURPLE("purple", 0xFFC989FF),
    RED("red", 0xFFFF7066),
    YELLOW("yellow", 0xFFFFD76A);

    private static final MicrolizedMachineFaceColor[] VALUES = values();

    private final String serializedName;
    private final int argb;

    MicrolizedMachineFaceColor(String serializedName, int argb) {
        this.serializedName = serializedName;
        this.argb = argb;
    }

    public String serializedName() {
        return serializedName;
    }

    public int argb() {
        return argb;
    }

    public MicrolizedMachineFaceColor next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static MicrolizedMachineFaceColor byIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return CYAN;
        }

        return VALUES[index];
    }

    public static MicrolizedMachineFaceColor byName(String name, MicrolizedMachineFaceColor fallback) {
        for (MicrolizedMachineFaceColor color : VALUES) {
            if (color.serializedName.equals(name)) {
                return color;
            }
        }

        return fallback;
    }
}
