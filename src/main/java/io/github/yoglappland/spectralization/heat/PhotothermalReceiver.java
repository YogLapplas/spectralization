package io.github.yoglappland.spectralization.heat;

public interface PhotothermalReceiver {
    PhotothermalAbsorberProfile photothermalAbsorberProfile();

    PhotothermalCouplingResult photothermalCoupling();

    void receivePhotothermalSample(PhotothermalReadoutSample sample);
}
