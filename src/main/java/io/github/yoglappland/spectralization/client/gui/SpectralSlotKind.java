package io.github.yoglappland.spectralization.client.gui;

public enum SpectralSlotKind {
    NORMAL(0xFF697168),
    INPUT(0xFF7C8AA0),
    OUTPUT(0xFFA08A62),
    CONTAINER(0xFF5DB7FF),
    FUEL(0xFFFF9F43),
    POWER(0xFF58D68D),
    OPTICAL(0xFF9EFFF1),
    FLUID(0xFF5DB7FF);

    private final int borderColor;

    SpectralSlotKind(int borderColor) {
        this.borderColor = borderColor;
    }

    public int borderColor() {
        return borderColor;
    }
}
