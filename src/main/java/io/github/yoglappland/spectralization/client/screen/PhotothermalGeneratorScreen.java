package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.PhotothermalGeneratorBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralBarKind;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.SpectralSlotKind;
import io.github.yoglappland.spectralization.menu.PhotothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PhotothermalGeneratorScreen extends SpectralMachineScreen<PhotothermalGeneratorMenu> {
    private static final int FUEL_SLOT_X = 67;
    private static final int FUEL_SLOT_Y = 55;
    private static final int INVENTORY_X = 48;
    private static final int INVENTORY_Y = 112;

    public PhotothermalGeneratorScreen(PhotothermalGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "photothermal_generator", 256, 204, INVENTORY_X, 98);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 74);
        panel(graphics, "inventory_panel", 42, 104, 172, 90);
        slot(graphics, "slot_fuel", FUEL_SLOT_X, FUEL_SLOT_Y, SpectralSlotKind.FUEL);
        playerInventorySlots(graphics, INVENTORY_X, INVENTORY_Y);
        renderBurnBar(graphics);
        renderEnergyBar(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        mutedText(graphics, "fuel_text", "Fuel: " + data(PhotothermalGeneratorBlockEntity.DATA_FUEL_COUNT), 0, 200, 4);
        text(graphics, "energy_text", "Energy: " + data(PhotothermalGeneratorBlockEntity.DATA_ENERGY)
                + "/" + data(PhotothermalGeneratorBlockEntity.DATA_CAPACITY) + " FE", 112, 34, 118);
        text(graphics, "burn_text", "Burn: " + data(PhotothermalGeneratorBlockEntity.DATA_BURN_REMAINING)
                + "/" + data(PhotothermalGeneratorBlockEntity.DATA_BURN_DURATION) + " ticks", 112, 52, 118);
        text(graphics, "output_text", "Output: " + data(PhotothermalGeneratorBlockEntity.DATA_OUTPUT) + " FE/t", 112, 70, 118);
    }

    private void renderBurnBar(GuiGraphics graphics) {
        int duration = data(PhotothermalGeneratorBlockEntity.DATA_BURN_DURATION);
        int remaining = data(PhotothermalGeneratorBlockEntity.DATA_BURN_REMAINING);
        verticalBar(graphics, "burn_bar", 36, 36, 10, 54, remaining, duration, SpectralBarKind.BURN);
    }

    private void renderEnergyBar(GuiGraphics graphics) {
        int capacity = Math.max(1, data(PhotothermalGeneratorBlockEntity.DATA_CAPACITY));
        int energy = data(PhotothermalGeneratorBlockEntity.DATA_ENERGY);
        horizontalBar(graphics, "energy_bar", 112, 88, 118, 5, energy, capacity, SpectralBarKind.ENERGY);
    }

    private int data(int index) {
        return menu.getData(index);
    }
}
