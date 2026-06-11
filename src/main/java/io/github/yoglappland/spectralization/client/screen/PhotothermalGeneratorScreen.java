package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.PhotothermalGeneratorBlockEntity;
import io.github.yoglappland.spectralization.menu.PhotothermalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class PhotothermalGeneratorScreen extends AbstractContainerScreen<PhotothermalGeneratorMenu> {
    private static final int PANEL = 0xDD101216;
    private static final int INNER = 0xDD1B1F27;
    private static final int FRAME = 0xFF303744;
    private static final int SLOT = 0xAA080A0F;
    private static final int SLOT_HIGHLIGHT = 0xFF495161;
    private static final int TEXT = 0xD8DEE9;
    private static final int MUTED_TEXT = 0xAEB8C5;
    private static final int FUEL_SLOT_X = 80;
    private static final int FUEL_SLOT_Y = 44;
    private static final int INVENTORY_X = 48;
    private static final int INVENTORY_Y = 112;

    public PhotothermalGeneratorScreen(PhotothermalGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 204;
        inventoryLabelX = INVENTORY_X;
        inventoryLabelY = 98;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, INNER);
        drawPanel(graphics, 16, 20, 224, 64);
        drawPanel(graphics, INVENTORY_X - 6, INVENTORY_Y - 18, 174, 100);
        drawSlot(graphics, FUEL_SLOT_X, FUEL_SLOT_Y);
        drawInventorySlots(graphics);
        renderBurnBar(graphics);
        renderEnergyBar(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EEF8, false);
        graphics.drawString(font, "Fuel: " + data(PhotothermalGeneratorBlockEntity.DATA_FUEL_COUNT), 68, 65, MUTED_TEXT, false);
        graphics.drawString(font, "Energy: " + data(PhotothermalGeneratorBlockEntity.DATA_ENERGY)
                + "/" + data(PhotothermalGeneratorBlockEntity.DATA_CAPACITY) + " FE", 112, 28, TEXT, false);
        graphics.drawString(font, "Burn: " + data(PhotothermalGeneratorBlockEntity.DATA_BURN_REMAINING)
                + "/" + data(PhotothermalGeneratorBlockEntity.DATA_BURN_DURATION) + " ticks", 112, 46, TEXT, false);
        graphics.drawString(font, "Output: " + data(PhotothermalGeneratorBlockEntity.DATA_OUTPUT) + " FE/t", 112, 64, TEXT, false);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        int left = leftPos + x;
        int top = topPos + y;
        graphics.fill(left, top, left + width, top + height, FRAME);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, 0xCC11151D);
    }

    private void drawInventorySlots(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, INVENTORY_X + column * 18, INVENTORY_Y + row * 18);
            }
        }

        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, INVENTORY_X + column * 18, INVENTORY_Y + 58);
        }
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        int left = leftPos + x - 1;
        int top = topPos + y - 1;
        graphics.fill(left, top, left + 18, top + 18, SLOT_HIGHLIGHT);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT);
    }

    private void renderBurnBar(GuiGraphics graphics) {
        int duration = data(PhotothermalGeneratorBlockEntity.DATA_BURN_DURATION);
        int remaining = data(PhotothermalGeneratorBlockEntity.DATA_BURN_REMAINING);
        int height = duration <= 0 ? 0 : Math.round(34.0F * remaining / duration);
        int left = leftPos + 54;
        int top = topPos + 40;
        int bottom = topPos + 75;

        graphics.fill(left, top, left + 8, bottom, FRAME);

        if (height > 0) {
            graphics.fill(left + 1, bottom - height, left + 7, bottom - 1, 0xFFFF9F43);
        }
    }

    private void renderEnergyBar(GuiGraphics graphics) {
        int capacity = Math.max(1, data(PhotothermalGeneratorBlockEntity.DATA_CAPACITY));
        int energy = data(PhotothermalGeneratorBlockEntity.DATA_ENERGY);
        int width = Math.round(118.0F * energy / capacity);
        int left = leftPos + 112;
        int top = topPos + 76;

        graphics.fill(left, top, left + 118, top + 5, FRAME);

        if (width > 0) {
            graphics.fill(left + 1, top + 1, left + width, top + 4, 0xFF58D68D);
        }
    }

    private int data(int index) {
        return menu.getData(index);
    }
}
