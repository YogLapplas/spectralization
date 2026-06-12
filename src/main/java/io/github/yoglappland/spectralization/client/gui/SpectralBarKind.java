package io.github.yoglappland.spectralization.client.gui;

public enum SpectralBarKind {
    ENERGY(SpectralGuiTheme.ENERGY),
    HEAT(SpectralGuiTheme.HEAT),
    BURN(SpectralGuiTheme.BURN),
    FLUID(SpectralGuiTheme.FLUID),
    PROGRESS(SpectralGuiTheme.PROGRESS),
    OPTICAL(SpectralGuiTheme.OPTICAL),
    SIGNAL(SpectralGuiTheme.SIGNAL),
    STABLE(SpectralGuiTheme.STABLE);

    private final int fillColor;

    SpectralBarKind(int fillColor) {
        this.fillColor = fillColor;
    }

    public int fillColor() {
        return fillColor;
    }
}
