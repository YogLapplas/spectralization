package io.github.yoglappland.spectralization.energy;

import net.neoforged.neoforge.energy.EnergyStorage;

public class SpectralEnergyStorage extends EnergyStorage {
    private final Runnable changeListener;

    public SpectralEnergyStorage(int capacity, int maxReceive, int maxExtract, Runnable changeListener) {
        super(capacity, maxReceive, maxExtract);
        this.changeListener = changeListener;
    }

    public int addEnergy(int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }

        int received = Math.min(capacity - energy, amount);

        if (!simulate && received > 0) {
            energy += received;
            onChanged();
        }

        return received;
    }

    public void setEnergyStored(int energy) {
        int clamped = Math.max(0, Math.min(capacity, energy));

        if (this.energy != clamped) {
            this.energy = clamped;
            onChanged();
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = super.receiveEnergy(maxReceive, simulate);

        if (!simulate && received > 0) {
            onChanged();
        }

        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = super.extractEnergy(maxExtract, simulate);

        if (!simulate && extracted > 0) {
            onChanged();
        }

        return extracted;
    }

    private void onChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
