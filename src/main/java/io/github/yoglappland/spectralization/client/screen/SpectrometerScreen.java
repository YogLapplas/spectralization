package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.SpectrometerBlockEntity;
import io.github.yoglappland.spectralization.menu.SpectrometerMenu;
import io.github.yoglappland.spectralization.optics.SpectralColorMap;
import io.github.yoglappland.spectralization.optics.SpectralRegion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SpectrometerScreen extends AbstractContainerScreen<SpectrometerMenu> {
    private static final int CHART_LEFT = 16;
    private static final int CHART_TOP = 40;
    private static final int CHART_WIDTH = 224;
    private static final int CHART_HEIGHT = 116;
    private static final int CHART_BOTTOM = CHART_TOP + CHART_HEIGHT;

    public SpectrometerScreen(SpectrometerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 256;
        imageHeight = 190;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> click(SpectrometerMenu.BUTTON_REGION_DOWN))
                .bounds(leftPos + 16, topPos + 164, 24, 16)
                .build());
        addRenderableWidget(Button.builder(Component.literal(">"), button -> click(SpectrometerMenu.BUTTON_REGION_UP))
                .bounds(leftPos + 216, topPos + 164, 24, 16)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xDD101216);
        graphics.fill(leftPos + 6, topPos + 6, leftPos + imageWidth - 6, topPos + imageHeight - 6, 0xDD1B1F27);
        renderSpectrumChart(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE7EEF8, false);
        graphics.drawString(font, "Region: " + activeRegion().id(), 12, 22, 0xD8DEE9, false);
        graphics.drawString(font, "Total power: " + data(SpectrometerBlockEntity.DATA_TOTAL_POWER), 122, 22, 0xD8DEE9, false);
        graphics.drawString(font, "Peak bin: " + data(SpectrometerBlockEntity.DATA_PEAK_BIN), 52, 168, 0xAEB8C5, false);
        graphics.drawString(font, reliabilityText(), 154, 168, reliabilityColor(), false);
    }

    private void renderSpectrumChart(GuiGraphics graphics) {
        int left = leftPos + CHART_LEFT;
        int top = topPos + CHART_TOP;
        int right = left + CHART_WIDTH;
        int bottom = top + CHART_HEIGHT;
        int bins = activeRegion().defaultBins();

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF303744);
        graphics.fill(left, top, right, bottom, 0xCC080A0F);

        for (int bin = 0; bin < bins; bin++) {
            int slotLeft = left + bin * CHART_WIDTH / bins;
            int slotRight = left + (bin + 1) * CHART_WIDTH / bins;
            int barWidth = Math.max(1, slotRight - slotLeft - 1);
            int weight = data(SpectrometerBlockEntity.DATA_SPECTRUM_START + bin);
            int barHeight = Math.round(CHART_HEIGHT * weight / 1000.0F);

            if (barHeight <= 0) {
                graphics.fill(slotLeft, bottom - 1, slotLeft + barWidth, bottom, 0xFF2B313A);
                continue;
            }

            graphics.fill(slotLeft, bottom - barHeight, slotLeft + barWidth, bottom, 0xFF000000 | barColor(bin));
        }
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private SpectralRegion activeRegion() {
        SpectralRegion[] regions = SpectralRegion.values();
        int index = Math.max(0, Math.min(data(SpectrometerBlockEntity.DATA_REGION), regions.length - 1));
        return regions[index];
    }

    private int barColor(int bin) {
        return activeRegion() == SpectralRegion.VISIBLE
                ? SpectralColorMap.visibleRgbForBin(bin)
                : 0x8992A3;
    }

    private String reliabilityText() {
        return data(SpectrometerBlockEntity.DATA_RELIABLE) == 1 ? "reliable" : "unstable";
    }

    private int reliabilityColor() {
        return data(SpectrometerBlockEntity.DATA_RELIABLE) == 1 ? 0x7CF3A0 : 0xFFB86C;
    }
}
