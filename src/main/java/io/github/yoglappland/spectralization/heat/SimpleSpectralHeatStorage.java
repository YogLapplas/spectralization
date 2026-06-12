package io.github.yoglappland.spectralization.heat;

import java.util.Objects;

public class SimpleSpectralHeatStorage implements SpectralHeatStorage {
    private final double heatCapacity;
    private final double ambientTemperature;
    private final double maxTemperature;
    private final Runnable changedListener;
    private double heatStored;

    public SimpleSpectralHeatStorage(
            double heatCapacity,
            double ambientTemperature,
            double maxTemperature,
            Runnable changedListener
    ) {
        if (!Double.isFinite(heatCapacity) || heatCapacity <= 0.0) {
            throw new IllegalArgumentException("Heat capacity must be finite and positive");
        }

        if (!Double.isFinite(ambientTemperature) || !Double.isFinite(maxTemperature)
                || maxTemperature <= ambientTemperature) {
            throw new IllegalArgumentException("Temperature bounds must be finite and ordered");
        }

        this.heatCapacity = heatCapacity;
        this.ambientTemperature = ambientTemperature;
        this.maxTemperature = maxTemperature;
        this.changedListener = Objects.requireNonNull(changedListener, "changedListener");
    }

    @Override
    public double heatStored() {
        return heatStored;
    }

    @Override
    public double maxHeatStored() {
        return (maxTemperature - ambientTemperature) * heatCapacity;
    }

    @Override
    public double heatCapacity() {
        return heatCapacity;
    }

    @Override
    public double ambientTemperature() {
        return ambientTemperature;
    }

    @Override
    public double temperature() {
        return ambientTemperature + heatStored / heatCapacity;
    }

    @Override
    public double maxTemperature() {
        return maxTemperature;
    }

    @Override
    public double insertHeat(double heat, boolean simulate) {
        if (!Double.isFinite(heat) || heat <= 0.0) {
            return 0.0;
        }

        double accepted = Math.min(heat, maxHeatStored() - heatStored);

        if (!simulate && accepted > 0.0) {
            heatStored += accepted;
            changedListener.run();
        }

        return accepted;
    }

    @Override
    public double extractHeat(double heat, boolean simulate) {
        if (!Double.isFinite(heat) || heat <= 0.0) {
            return 0.0;
        }

        double extracted = Math.min(heat, heatStored);

        if (!simulate && extracted > 0.0) {
            heatStored -= extracted;
            changedListener.run();
        }

        return extracted;
    }

    public void setHeatStored(double heatStored) {
        double clamped = Math.max(0.0, Math.min(maxHeatStored(), heatStored));

        if (Math.abs(this.heatStored - clamped) > 1.0E-9) {
            this.heatStored = clamped;
            changedListener.run();
        }
    }

    public void coolTowardAmbient(double conductance) {
        if (!Double.isFinite(conductance) || conductance <= 0.0 || heatStored <= 0.0) {
            return;
        }

        extractHeat(Math.min(heatStored, conductance * Math.max(0.0, temperature() - ambientTemperature)), false);
    }
}
