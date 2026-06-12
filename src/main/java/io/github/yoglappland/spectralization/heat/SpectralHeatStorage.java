package io.github.yoglappland.spectralization.heat;

public interface SpectralHeatStorage {
    double heatStored();

    double maxHeatStored();

    double heatCapacity();

    double ambientTemperature();

    double temperature();

    double maxTemperature();

    double insertHeat(double heat, boolean simulate);

    double extractHeat(double heat, boolean simulate);

    default boolean isOverheated() {
        return temperature() >= maxTemperature();
    }
}
