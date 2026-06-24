package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.FiberLaserMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class FiberLaserScreen extends AbstractContainerScreen<FiberLaserMenu> {
    private static final int CONTROL_X = 12;
    private static final int CONTROL_Y = 12;
    private static final int CONTROL_WIDTH = 184;
    private static final int CONTROL_HEIGHT = 92;
    private static final int ENERGY_X = 24;
    private static final int ENERGY_Y = 34;
    private static final int ENERGY_WIDTH = 22;
    private static final int ENERGY_HEIGHT = 48;
    private static final int GAIN_X = 160;
    private static final int GAIN_Y = 34;
    private static final int GAIN_WIDTH = 22;
    private static final int GAIN_HEIGHT = 48;
    private static final int SLIDER_X = 62;
    private static final int SLIDER_Y = 63;
    private static final int SLIDER_WIDTH = 82;
    private static final int SLIDER_HEIGHT = 8;
    private static final int CHAMBER_X = 58;
    private static final int CHAMBER_Y = 30;
    private static final int CHAMBER_WIDTH = 90;
    private static final int CHAMBER_HEIGHT = 48;

    private boolean draggingSlider = false;
    private int lastSentPercent = -1;

    public FiberLaserScreen(FiberLaserMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 208;
        imageHeight = 116;
        titleLabelX = 0;
        titleLabelY = imageHeight + 100;
        inventoryLabelY = imageHeight + 100;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        panel(graphics, leftPos + CONTROL_X, topPos + CONTROL_Y, CONTROL_WIDTH, CONTROL_HEIGHT);
        divider(graphics, leftPos + 54, topPos + 27, topPos + 90);
        divider(graphics, leftPos + 154, topPos + 27, topPos + 90);
        drawEnergyBar(graphics);
        drawCenterPanel(graphics);
        drawGainBar(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCenteredText(graphics, "FE", 35, 22, 0.52F, ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredText(graphics, "PUMP", 103, 22, 0.52F, ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredText(graphics, "GAIN", 171, 22, 0.52F, ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredText(graphics, energyStoredText(), 35, 86, 0.42F, ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredText(graphics, menu.actualEnergyPerTick() + "/t", 103, 43, 0.58F, ThermalSmelterUiSkin.TEXT);
        drawCenteredText(graphics, gainText(), 171, 86, 0.50F, menu.active() ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.TEXT_SUB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInSlider(mouseX, mouseY)) {
            draggingSlider = true;
            updateSliderFromMouse(mouseX);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingSlider && button == 0) {
            updateSliderFromMouse(mouseX);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingSlider) {
            draggingSlider = false;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        int x = leftPos + ENERGY_X;
        int y = topPos + ENERGY_Y;
        double ratio = menu.energyStored() / (double) menu.energyCapacity();
        int fillHeight = Math.round((float) ((ENERGY_HEIGHT - 4) * Mth.clamp(ratio, 0.0D, 1.0D)));

        insetPanel(graphics, x - 2, y - 2, ENERGY_WIDTH + 4, ENERGY_HEIGHT + 4, ThermalSmelterUiSkin.CHAMBER_BG);
        graphics.fill(x, y, x + ENERGY_WIDTH, y + ENERGY_HEIGHT, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 78));
        graphics.fill(x + 2, y + ENERGY_HEIGHT - 2 - fillHeight, x + ENERGY_WIDTH - 2, y + ENERGY_HEIGHT - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 178));
        graphics.fill(x + 3, y + ENERGY_HEIGHT - 2 - fillHeight, x + ENERGY_WIDTH - 3, Math.min(y + ENERGY_HEIGHT - 2, y + ENERGY_HEIGHT - fillHeight), ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 96));
    }

    private void drawCenterPanel(GuiGraphics graphics) {
        int x = leftPos + CHAMBER_X;
        int y = topPos + CHAMBER_Y;
        int percent = Mth.clamp(Math.round(menu.energyPerTick() * 100.0F / menu.maxEnergyPerTick()), 0, 100);
        int sliderLeft = leftPos + SLIDER_X;
        int sliderTop = topPos + SLIDER_Y;
        int sliderRight = sliderLeft + SLIDER_WIDTH;
        int sliderBottom = sliderTop + SLIDER_HEIGHT;
        int knobX = sliderLeft + Math.round((SLIDER_WIDTH - 1) * percent / 100.0F);

        insetPanel(graphics, x, y, CHAMBER_WIDTH, CHAMBER_HEIGHT, ThermalSmelterUiSkin.CHAMBER_BG);
        graphics.fill(x + 8, y + 10, x + CHAMBER_WIDTH - 8, y + 11, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 96));
        graphics.fill(x + 12, y + 17, x + CHAMBER_WIDTH - 12, y + 18, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 110));
        statusPixels(graphics, x + 24, y + 27);

        graphics.fill(sliderLeft - 1, sliderTop - 1, sliderRight + 1, sliderBottom + 1, ThermalSmelterUiSkin.BORDER);
        graphics.fill(sliderLeft, sliderTop, sliderRight, sliderBottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 96));
        graphics.fill(sliderLeft, sliderTop + 2, knobX, sliderBottom - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.HEAT, 178));
        graphics.fill(knobX - 2, sliderTop - 3, knobX + 3, sliderBottom + 3, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(knobX - 1, sliderTop - 2, knobX + 2, sliderBottom + 2, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private void drawGainBar(GuiGraphics graphics) {
        int x = leftPos + GAIN_X;
        int y = topPos + GAIN_Y;
        double ratio = Mth.clamp((menu.gainX1000() - 1000) / 4000.0D, 0.0D, 1.0D);
        int fillHeight = Math.round((float) ((GAIN_HEIGHT - 4) * ratio));

        insetPanel(graphics, x - 2, y - 2, GAIN_WIDTH + 4, GAIN_HEIGHT + 4, ThermalSmelterUiSkin.CHAMBER_BG);
        graphics.fill(x, y, x + GAIN_WIDTH, y + GAIN_HEIGHT, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 78));
        graphics.fill(x + 2, y + GAIN_HEIGHT - 2 - fillHeight, x + GAIN_WIDTH - 2, y + GAIN_HEIGHT - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 190));
        graphics.fill(x + 3, y + GAIN_HEIGHT - 2 - fillHeight, x + GAIN_WIDTH - 3, Math.min(y + GAIN_HEIGHT - 2, y + GAIN_HEIGHT - fillHeight), ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 104));
    }

    private void statusPixels(GuiGraphics graphics, int x, int y) {
        int active = Math.max(0, Math.min(5, Math.round((menu.gainX1000() - 1000) / 800.0F)));

        for (int index = 0; index < 5; index++) {
            int color = index < active ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.EMPTY;
            graphics.fill(x + index * 9, y, x + index * 9 + 6, y + 6, ThermalSmelterUiSkin.withAlpha(color, index < active ? 190 : 72));
            outline(graphics, x + index * 9, y, 6, 6, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 130));
        }
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 108, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void divider(GuiGraphics graphics, int x, int top, int bottom) {
        graphics.fill(x, top, x + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 118));
        graphics.fill(x + 1, top, x + 2, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 94));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private boolean isInSlider(double mouseX, double mouseY) {
        int left = leftPos + SLIDER_X - 4;
        int top = topPos + SLIDER_Y - 5;
        int right = leftPos + SLIDER_X + SLIDER_WIDTH + 4;
        int bottom = topPos + SLIDER_Y + SLIDER_HEIGHT + 5;
        return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
    }

    private void updateSliderFromMouse(double mouseX) {
        int left = leftPos + SLIDER_X;
        int percent = Mth.clamp(Math.round((float) ((mouseX - left) * 100.0 / SLIDER_WIDTH)), 0, 100);

        if (percent == lastSentPercent) {
            return;
        }

        lastSentPercent = percent;

        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, percent);
        }
    }

    private void drawCenteredText(GuiGraphics graphics, String text, int centerX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(centerX - textWidth * scale / 2.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private String gainText() {
        return String.format(Locale.ROOT, "%.2fx", menu.gainX1000() / 1000.0D);
    }

    private String energyStoredText() {
        int kilo = Math.round(menu.energyStored() / 1000.0F);
        return kilo + "k";
    }
}
