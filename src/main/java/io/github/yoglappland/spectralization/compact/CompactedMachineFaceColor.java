package io.github.yoglappland.spectralization.compact;

public enum CompactedMachineFaceColor {
    BLUE("blue", 0xFF5D9DFF),
    CYAN("cyan", 0xFF7CEAD9),
    GREEN("green", 0xFF7FDB8A),
    PURPLE("purple", 0xFFC989FF),
    RED("red", 0xFFFF7066),
    YELLOW("yellow", 0xFFFFD76A);

    private static final CompactedMachineFaceColor[] VALUES = values();

    private final String serializedName;
    private final int argb;

    CompactedMachineFaceColor(String serializedName, int argb) {
        this.serializedName = serializedName;
        this.argb = argb;
    }

    public String serializedName() {
        return serializedName;
    }

    public int argb() {
        return argb;
    }

    public CompactedMachineFaceColor next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static CompactedMachineFaceColor byIndex(int index) {
        if (index < 0 || index >= VALUES.length) {
            return CYAN;
        }

        return VALUES[index];
    }

    public static CompactedMachineFaceColor byName(String name, CompactedMachineFaceColor fallback) {
        for (CompactedMachineFaceColor color : VALUES) {
            if (color.serializedName.equals(name)) {
                return color;
            }
        }

        return fallback;
    }
}
