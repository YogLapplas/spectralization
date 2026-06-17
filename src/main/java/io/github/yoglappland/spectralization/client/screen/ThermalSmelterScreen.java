package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralBarKind;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.SpectralSlotKind;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ThermalSmelterScreen extends SpectralMachineScreen<ThermalSmelterMenu> {
    private static final int INVENTORY_X = 48;
    private static final int INVENTORY_Y = 124;

    public ThermalSmelterScreen(ThermalSmelterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "thermal_smelter", 256, 216, INVENTORY_X, 110);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawMachineBackground(graphics);
        panel(graphics, "machine_panel", 18, 22, 220, 74);
        panel(graphics, "inventory_panel", 42, 104, 172, 104);
        slot(graphics, "slot_input", 67, 44, SpectralSlotKind.INPUT);
        slot(graphics, "slot_additive", 67, 71, SpectralSlotKind.OPTICAL);
        slot(graphics, "slot_output", 192, 57, SpectralSlotKind.OUTPUT);
        playerInventorySlots(graphics, INVENTORY_X, INVENTORY_Y);
        renderProgressBar(graphics);
        renderHeatBar(graphics);
        renderFlowArrow(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        title(graphics);
        text(graphics, "temperature_text", "Temp: " + data(ThermalSmelterBlockEntity.DATA_TEMPERATURE) + " K", 112, 34, 90);
        text(graphics, "heat_text", "Heat: " + data(ThermalSmelterBlockEntity.DATA_HEAT)
                + "/" + data(ThermalSmelterBlockEntity.DATA_MAX_HEAT), 0, 212, 4);
        text(graphics, "process_text", "Solid output", 112, 78, 90);
        mutedText(graphics, "optical_heat_text", "Optical heat: " + (data(ThermalSmelterBlockEntity.DATA_HEAT_POWER_X100) / 100.0) + " SP/t", 0, 212, 4);
    }

    private void renderProgressBar(GuiGraphics graphics) {
        int required = data(ThermalSmelterBlockEntity.DATA_PROGRESS_REQUIRED);
        int progress = data(ThermalSmelterBlockEntity.DATA_PROGRESS);
        horizontalBar(graphics, "progress_bar", 98, 59, 42, 5, progress, required, SpectralBarKind.PROGRESS);
    }

    private void renderHeatBar(GuiGraphics graphics) {
        int maxHeat = Math.max(1, data(ThermalSmelterBlockEntity.DATA_MAX_HEAT));
        int heat = data(ThermalSmelterBlockEntity.DATA_HEAT);
        verticalBar(graphics, "heat_bar", 36, 36, 10, 54, heat, maxHeat, SpectralBarKind.HEAT);
    }

    private void renderFlowArrow(GuiGraphics graphics) {
        io.github.yoglappland.spectralization.client.gui.SpectralGui.drawRightArrow(
                graphics,
                leftPos + 98,
                topPos + 67,
                50,
                io.github.yoglappland.spectralization.client.gui.SpectralGuiTheme.OPTICAL
        );
    }

    private int data(int index) {
        return menu.getData(index);
    }
}
