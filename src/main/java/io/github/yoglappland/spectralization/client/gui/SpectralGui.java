package io.github.yoglappland.spectralization.client.gui;

import net.minecraft.client.gui.GuiGraphics;

public final class SpectralGui {
    public static final int SLOT_SIZE = 18;

    public static void drawScreen(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, SpectralGuiTheme.SCREEN_OUTER);
        graphics.fill(left + 6, top + 6, left + width - 6, top + height - 6, SpectralGuiTheme.SCREEN_INNER);
        drawScreenGrid(graphics, left + 6, top + 6, width - 12, height - 12);
        graphics.fill(left + 7, top + 7, left + width - 7, top + 8, 0x55747D72);
        graphics.fill(left + 7, top + height - 8, left + width - 7, top + height - 7, 0x88070807);
    }

    public static void drawScreenGrid(GuiGraphics graphics, int left, int top, int width, int height) {
        int right = left + width;
        int bottom = top + height;

        for (int x = 0; x <= width; x += 8) {
            int color = x % 16 == 0 ? 0x225BBF73 : 0x143B8651;
            graphics.fill(left + x, top, left + x + 1, bottom, color);
        }

        for (int y = 0; y <= height; y += 8) {
            int color = y % 16 == 0 ? 0x225BBF73 : 0x143B8651;
            graphics.fill(left, top + y, right, top + y + 1, color);
        }
    }

    public static void drawPanel(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, SpectralGuiTheme.PANEL_BORDER);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, SpectralGuiTheme.PANEL_FILL);
        graphics.fill(left + 2, top + 2, left + width - 2, top + 3, 0x445B635A);
        graphics.fill(left + 2, top + height - 3, left + width - 2, top + height - 2, 0x77070807);
    }

    public static void drawInset(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, SpectralGuiTheme.PANEL_BORDER);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, SpectralGuiTheme.PANEL_INSET);
    }

    public static void drawSlot(GuiGraphics graphics, int left, int top, SpectralSlotKind kind) {
        graphics.fill(left - 1, top - 1, left + 17, top + 17, kind.borderColor());
        graphics.fill(left, top, left + 16, top + 16, SpectralGuiTheme.SLOT_FILL);
        graphics.fill(left, top, left + 16, top + 1, 0x554F574E);
        graphics.fill(left, top + 15, left + 16, top + 16, 0x77000000);
    }

    public static void drawDisabledSlot(GuiGraphics graphics, int left, int top) {
        graphics.fill(left - 1, top - 1, left + 17, top + 17, SpectralGuiTheme.SLOT_BORDER);
        graphics.fill(left, top, left + 16, top + 16, SpectralGuiTheme.SLOT_DISABLED);
    }

    public static void drawPlayerInventorySlots(GuiGraphics graphics, int left, int top) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, left + column * SLOT_SIZE, top + row * SLOT_SIZE, SpectralSlotKind.NORMAL);
            }
        }

        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, left + column * SLOT_SIZE, top + 58, SpectralSlotKind.NORMAL);
        }
    }

    public static void drawHorizontalBar(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        drawInset(graphics, left, top, width, height);
        int filledWidth = (int) Math.round((width - 2) * clampedRatio(value, max));

        if (filledWidth > 0) {
            graphics.fill(left + 1, top + 1, left + 1 + filledWidth, top + height - 1, kind.fillColor());
        }
    }

    public static void drawVerticalBar(
            GuiGraphics graphics,
            int left,
            int top,
            int width,
            int height,
            double value,
            double max,
            SpectralBarKind kind
    ) {
        drawInset(graphics, left, top, width, height);
        int filledHeight = (int) Math.round((height - 2) * clampedRatio(value, max));

        if (filledHeight > 0) {
            int bottom = top + height - 1;
            graphics.fill(left + 1, bottom - filledHeight, left + width - 1, bottom, kind.fillColor());
        }
    }

    public static void drawRightArrow(GuiGraphics graphics, int left, int centerY, int length, int color) {
        int arrowLength = Math.max(8, length);
        int headWidth = Math.min(8, arrowLength);
        int headLeft = left + arrowLength - headWidth;
        int pointX = left + arrowLength;
        graphics.fill(left, centerY - 1, headLeft, centerY + 1, color);

        for (int x = headLeft; x <= pointX; x++) {
            int halfHeight = Math.max(0, (pointX - x) * 4 / headWidth);
            graphics.fill(x, centerY - halfHeight, x + 1, centerY + halfHeight + 1, color);
        }
    }

    public static int tinted(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static double clampedRatio(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value / max));
    }

    private SpectralGui() {
    }
}
