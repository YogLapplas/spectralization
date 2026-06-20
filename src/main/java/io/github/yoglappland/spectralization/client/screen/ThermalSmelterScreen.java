package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ThermalSmelterScreen extends SpectralMachineScreen<ThermalSmelterMenu> {
    private static final int GUI_BG = 0xFFEDEAE3;
    private static final int MACHINE_BG = 0xFFE3E0D9;
    private static final int PANEL = 0xFFD4D1C8;
    private static final int SLOT_BG = 0xFFC6C3BA;
    private static final int SLOT_HIGHLIGHT = 0xFFEEECEA;
    private static final int SLOT_SHADOW = 0xFFAEACA4;
    private static final int BORDER = 0xFF6A706B;
    private static final int TEXT = 0xFF2A3430;
    private static final int TEXT_SUB = 0xFF6A706B;
    private static final int HEAT = 0xFFFF7A3D;
    private static final int PROGRESS = 0xFFF2B84B;
    private static final int OPTICAL = 0xFF42C7C7;
    private static final int CHAMBER_BG = 0xFFEEECEA;
    private static final int CHAMBER_FRAME = 0xFFD8D5CE;
    private static final int METER_BG = 0xFFC6C3BA;

    private static final int INVENTORY_X = 48;
    private static final int INVENTORY_Y = 124;
    private static final int HOTBAR_Y = 182;
    private static final int MACHINE_HEIGHT = 108;
    private static final int SLOT_SIZE = 18;

    private static final int SLOT_INPUT_X = 15;
    private static final int SLOT_INPUT_Y = 22;
    private static final int SLOT_ADDITIVE_X = 15;
    private static final int SLOT_ADDITIVE_Y = 44;
    private static final int SLOT_OUTPUT_X = 212;
    private static final int SLOT_OUTPUT_Y = 33;
    private static final int BEAM_Y = 42;

    private static final int METER_X = 38;
    private static final int METER_Y = 18;
    private static final int METER_WIDTH = 8;
    private static final int METER_HEIGHT = 68;

    private static final int CHAMBER_X = 54;
    private static final int CHAMBER_Y = 8;
    private static final int CHAMBER_WIDTH = 132;
    private static final int CHAMBER_HEIGHT = 88;
    private static final int INNER_X = CHAMBER_X + 4;
    private static final int INNER_Y = CHAMBER_Y + 4;
    private static final int INNER_WIDTH = CHAMBER_WIDTH - 8;
    private static final int INNER_HEIGHT = CHAMBER_HEIGHT - 8;
    private static final float TINY_LABEL_SCALE = 0.44F;

    public ThermalSmelterScreen(ThermalSmelterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, "thermal_smelter", 256, 216, INVENTORY_X, 110);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawCeramicBackground(graphics);
        drawMachinePanel(graphics);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        double heatRatio = heatRatio();
        double opticalRatio = opticalRatio();
        int opticalPercent = (int) Math.round(opticalRatio * 100.0);

        drawCenteredText(graphics, "MAT", 24, SLOT_INPUT_Y - 5, TINY_LABEL_SCALE, TEXT_SUB);
        drawCenteredText(graphics, "OPT", 24, SLOT_ADDITIVE_Y - 4, TINY_LABEL_SCALE, TEXT_SUB);
        drawCenteredText(graphics, "INPUT", 28, 90, TINY_LABEL_SCALE, TEXT_SUB);
        drawCenteredText(graphics, "HT", METER_X + METER_WIDTH / 2, METER_Y + METER_HEIGHT + 2, TINY_LABEL_SCALE, TEXT_SUB);
        drawText(graphics, "OPT " + opticalPercent + "%", INNER_X + 18, INNER_Y + 8, TINY_LABEL_SCALE, OPTICAL);
        drawCenteredText(graphics, data(ThermalSmelterBlockEntity.DATA_TEMPERATURE) + " K", INNER_X + INNER_WIDTH / 2, INNER_Y + INNER_HEIGHT - 8, 0.75F, TEXT);
        drawCenteredText(graphics, "OUT", SLOT_OUTPUT_X + 9, SLOT_OUTPUT_Y - 5, TINY_LABEL_SCALE, TEXT_SUB);
        drawCenteredText(graphics, opticalWavelengthLabel(), SLOT_OUTPUT_X + 9, SLOT_OUTPUT_Y + 28, TINY_LABEL_SCALE, OPTICAL);
        drawCenteredText(graphics, "OUTPUT", 228, 90, TINY_LABEL_SCALE, TEXT_SUB);
        drawCenteredText(graphics, "THERMAL SMELTER - SPECTRALIZATION", imageWidth / 2, MACHINE_HEIGHT - 6, 0.5F, TEXT_SUB);
        drawCenteredText(graphics, "Inventory", imageWidth / 2, INVENTORY_Y - 8, 0.65F, TEXT_SUB);
        drawCenteredText(graphics, heatReadoutLabel(heatRatio), 28, 84, TINY_LABEL_SCALE, TEXT_SUB);
    }

    private void drawCeramicBackground(GuiGraphics graphics) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, GUI_BG);
        graphics.fill(left, top, left + imageWidth, top + MACHINE_HEIGHT, MACHINE_BG);
        outline(graphics, left, top, imageWidth, MACHINE_HEIGHT, BORDER);
        graphics.fill(left, top + MACHINE_HEIGHT - 10, left + imageWidth, top + MACHINE_HEIGHT, withAlpha(CHAMBER_FRAME, 118));
        graphics.fill(left + 8, top + MACHINE_HEIGHT + 2, left + imageWidth - 8, top + MACHINE_HEIGHT + 3, withAlpha(BORDER, 128));
    }

    private void drawMachinePanel(GuiGraphics graphics) {
        int left = leftPos;
        int top = topPos;
        double heatRatio = heatRatio();
        double progressRatio = progressRatio();
        double opticalRatio = opticalRatio();

        drawPanelBlock(graphics, left + 6, top + 10, 44, 86);
        drawCeramicSlot(graphics, left + SLOT_INPUT_X, top + SLOT_INPUT_Y);
        drawCeramicSlot(graphics, left + SLOT_ADDITIVE_X, top + SLOT_ADDITIVE_Y);
        drawHeatMeter(graphics, heatRatio);
        drawArrow(graphics, left + 35, left + 52, top + BEAM_Y, progressRatio > 0.08);
        drawArrow(graphics, left + 188, left + 210, top + BEAM_Y, progressRatio > 0.45);

        graphics.fill(left + CHAMBER_X - 4, top + BEAM_Y - 3, left + CHAMBER_X, top + BEAM_Y + 3, withAlpha(OPTICAL, (int) Math.round(224 * opticalRatio)));
        drawChamber(graphics, heatRatio, progressRatio, opticalRatio);

        drawPanelBlock(graphics, left + imageWidth - 50, top + 10, 44, 86);
        drawCeramicSlot(graphics, left + SLOT_OUTPUT_X, top + SLOT_OUTPUT_Y);
    }

    private void drawChamber(GuiGraphics graphics, double heatRatio, double progressRatio, double opticalRatio) {
        int left = leftPos;
        int top = topPos;
        int centerX = left + INNER_X + INNER_WIDTH / 2;
        int centerY = top + INNER_Y + INNER_HEIGHT / 2;

        graphics.fill(left + CHAMBER_X, top + CHAMBER_Y, left + CHAMBER_X + CHAMBER_WIDTH, top + CHAMBER_Y + CHAMBER_HEIGHT, CHAMBER_FRAME);
        outline(graphics, left + CHAMBER_X, top + CHAMBER_Y, CHAMBER_WIDTH, CHAMBER_HEIGHT, BORDER);
        graphics.fill(left + INNER_X, top + INNER_Y, left + INNER_X + INNER_WIDTH, top + INNER_Y + INNER_HEIGHT, CHAMBER_BG);

        for (int index = 0; index < 6; index++) {
            int y = top + INNER_Y + 10 + index * 10;
            int alpha = (int) Math.round(18 + heatRatio * 56 * (index / 5.0));
            graphics.fill(left + INNER_X + 10, y, left + INNER_X + INNER_WIDTH - 10, y + 4, withAlpha(HEAT, alpha));
        }

        graphics.fill(centerX - 18, centerY - 14, centerX + 18, centerY + 14, withAlpha(HEAT, (int) Math.round(56 + heatRatio * 96)));
        graphics.fill(centerX - 9, centerY - 7, centerX + 9, centerY + 7, withAlpha(PROGRESS, (int) Math.round(72 + progressRatio * 88)));
        graphics.fill(left + INNER_X, top + BEAM_Y, left + INNER_X + INNER_WIDTH, top + BEAM_Y + 1, withAlpha(BORDER, 18));

        drawDiamond(graphics, left + INNER_X + 7, top + INNER_Y + 8, 4, withAlpha(OPTICAL, (int) Math.round(230 * opticalRatio)));
        graphics.fill(left + INNER_X, top + INNER_Y + INNER_HEIGHT - 14, left + INNER_X + INNER_WIDTH, top + INNER_Y + INNER_HEIGHT, withAlpha(CHAMBER_FRAME, 178));
        graphics.fill(left + INNER_X, top + INNER_Y + INNER_HEIGHT - 14, left + INNER_X + INNER_WIDTH, top + INNER_Y + INNER_HEIGHT - 13, withAlpha(BORDER, 76));
    }

    private void drawHeatMeter(GuiGraphics graphics, double heatRatio) {
        int left = leftPos + METER_X;
        int top = topPos + METER_Y;
        graphics.fill(left, top, left + METER_WIDTH, top + METER_HEIGHT, METER_BG);
        outline(graphics, left, top, METER_WIDTH, METER_HEIGHT, BORDER);

        int fillHeight = (int) Math.round((METER_HEIGHT - 2) * heatRatio);
        if (fillHeight > 0) {
            graphics.fill(left + 1, top + METER_HEIGHT - 1 - fillHeight, left + METER_WIDTH - 1, top + METER_HEIGHT - 1, HEAT);
        }

        for (int i = 1; i <= 3; i++) {
            int y = top + METER_HEIGHT - (METER_HEIGHT * i / 4);
            graphics.fill(left + METER_WIDTH, y, left + METER_WIDTH + 3, y + 1, TEXT_SUB);
        }
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        int left = leftPos;
        int top = topPos;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawCeramicSlot(graphics, left + INVENTORY_X + column * SLOT_SIZE, top + INVENTORY_Y + row * SLOT_SIZE);
            }
        }

        graphics.fill(left + INVENTORY_X, top + HOTBAR_Y - 4, left + INVENTORY_X + 9 * SLOT_SIZE, top + HOTBAR_Y - 3, withAlpha(BORDER, 88));
        for (int column = 0; column < 9; column++) {
            drawCeramicSlot(graphics, left + INVENTORY_X + column * SLOT_SIZE, top + HOTBAR_Y);
        }
    }

    private void drawPanelBlock(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, PANEL);
        outline(graphics, left, top, width, height, withAlpha(BORDER, 196));
    }

    private void drawCeramicSlot(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + SLOT_SIZE, top + SLOT_SIZE, SLOT_BG);
        outline(graphics, left, top, SLOT_SIZE, SLOT_SIZE, BORDER);
        graphics.fill(left + 1, top + 1, left + SLOT_SIZE - 1, top + 2, SLOT_HIGHLIGHT);
        graphics.fill(left + 1, top + 1, left + 2, top + SLOT_SIZE - 1, SLOT_HIGHLIGHT);
        graphics.fill(left + 1, top + SLOT_SIZE - 2, left + SLOT_SIZE - 1, top + SLOT_SIZE - 1, SLOT_SHADOW);
        graphics.fill(left + SLOT_SIZE - 2, top + 1, left + SLOT_SIZE - 1, top + SLOT_SIZE - 1, SLOT_SHADOW);
    }

    private void drawArrow(GuiGraphics graphics, int x1, int x2, int y, boolean active) {
        int color = active ? PROGRESS : withAlpha(PROGRESS, 72);
        int headLeft = x2 - 5;
        graphics.fill(x1, y - 1, headLeft, y + 1, color);
        for (int x = headLeft; x <= x2; x++) {
            int halfHeight = Math.max(0, (x2 - x) * 3 / 5);
            graphics.fill(x, y - halfHeight, x + 1, y + halfHeight + 1, color);
        }
    }

    private void drawDiamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = radius - Math.abs(y);
            graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private void outline(GuiGraphics graphics, int left, int top, int width, int height, int color) {
        graphics.fill(left, top, left + width, top + 1, color);
        graphics.fill(left, top + height - 1, left + width, top + height, color);
        graphics.fill(left, top, left + 1, top + height, color);
        graphics.fill(left + width - 1, top, left + width, top + height, color);
    }

    private void drawCenteredText(GuiGraphics graphics, String text, int centerX, int y, float scale, int color) {
        int width = font.width(text);
        drawText(graphics, text, Math.round(centerX - width * scale / 2.0F), y, scale, color);
    }

    private void drawText(GuiGraphics graphics, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private String heatReadoutLabel(double heatRatio) {
        return (int) Math.round(heatRatio * 100.0) + "%";
    }

    private String opticalWavelengthLabel() {
        int nm = 380 + (int) Math.round(opticalRatio() * 480.0);
        return nm + "nm";
    }

    private double progressRatio() {
        int required = data(ThermalSmelterBlockEntity.DATA_PROGRESS_REQUIRED);
        int progress = data(ThermalSmelterBlockEntity.DATA_PROGRESS);
        return ratio(progress, required);
    }

    private double heatRatio() {
        int maxHeat = Math.max(1, data(ThermalSmelterBlockEntity.DATA_MAX_HEAT));
        int heat = data(ThermalSmelterBlockEntity.DATA_HEAT);
        return ratio(heat, maxHeat);
    }

    private double opticalRatio() {
        int heatPowerX100 = data(ThermalSmelterBlockEntity.DATA_HEAT_POWER_X100);
        return ratio(heatPowerX100, 1000);
    }

    private double ratio(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value / max));
    }

    private int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private int data(int index) {
        return menu.getData(index);
    }
}
