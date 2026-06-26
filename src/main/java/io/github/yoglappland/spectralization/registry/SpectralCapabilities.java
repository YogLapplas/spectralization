package io.github.yoglappland.spectralization.registry;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class SpectralCapabilities {
    private SpectralCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.PHOTONIC_GRADIENT_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.PHOTOTHERMAL_GENERATOR.get(),
                (generator, side) -> generator.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.BASIC_LITHOGRAPHY_MACHINE.get(),
                (machine, side) -> machine.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.SOLAR_DOPING_CHAMBER.get(),
                (chamber, side) -> chamber.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.FIBER_DRAWING_MACHINE.get(),
                (machine, side) -> machine.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                SpectralBlockEntities.FIBER_LASER.get(),
                (laser, side) -> laser.getEnergyStorage(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.PHOTOTHERMAL_GENERATOR.get(),
                (generator, side) -> generator.getFuelItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.THERMAL_SMELTER.get(),
                (smelter, side) -> smelter.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.METAMATERIAL_DESIGN_TABLE.get(),
                (table, side) -> table.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.BASIC_LITHOGRAPHY_MACHINE.get(),
                (machine, side) -> machine.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.SOLAR_DOPING_CHAMBER.get(),
                (chamber, side) -> chamber.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.FIBER_DRAWING_MACHINE.get(),
                (machine, side) -> machine.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.HOLOGRAPHIC_STORAGE_SHELL.get(),
                (shell, side) -> shell.getItems(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SpectralBlockEntities.COMPACT_MACHINE_CORE.get(),
                (core, side) -> core.getOutputItems(side)
        );
    }
}
