package io.github.yoglappland.spectralization.client.gui;

import io.github.yoglappland.spectralization.menu.ThermalSmelterLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class ThermalSmelterUiSkin {
    public static final int GUI_BG = 0xFFEDEAE3;
    public static final int MACHINE_BG = 0xFFE3E0D9;
    public static final int PANEL = 0xFFD4D1C8;
    public static final int PANEL_HIGHLIGHT = 0xFFF7F3EA;
    public static final int PANEL_SHADOW = 0xFF9B9890;
    public static final int SLOT_BG = 0xFFD0CDC4;
    public static final int SLOT_HIGHLIGHT = 0xFFF8F5EC;
    public static final int SLOT_SHADOW = 0xFF8F8B83;
    public static final int BORDER = 0xFF6A706B;
    public static final int STRONG_BORDER = 0xFF242C28;
    public static final int TEXT = 0xFF2A3430;
    public static final int TEXT_SUB = 0xFF6A706B;
    public static final int HEAT = 0xFFFF7A3D;
    public static final int HEAT_DARK = 0xFF9B351D;
    public static final int HEAT_MID = 0xFFE85D27;
    public static final int HEAT_LIGHT = 0xFFFFB35C;
    public static final int HEAT_PALE = 0xFFFFD58C;
    public static final int PROGRESS = 0xFFF2B84B;
    public static final int OPTICAL = 0xFF42C7C7;
    public static final int CHAMBER_BG = 0xFFE8E2D7;
    public static final int CHAMBER_SHADOW = 0xFFB8AFA3;
    public static final int EMPTY = 0xFF8C8A84;
    public static final int STATUS_READY = 0xFF55B96B;
    public static final int STATUS_PENDING = 0xFFF2B84B;
    public static final int STATUS_INVALID = 0xFFE35D52;
    public static final int STATUS_EMPTY = 0xFF697168;

    private static final int SLOT_SIZE = ThermalSmelterLayout.SLOT_SIZE;

    private ThermalSmelterUiSkin() {
    }

    public static void drawScreenShell(GuiGraphics graphics, int left, int top, int width, int height) {
        graphics.fill(left, top, left + width, top + height, GUI_BG);
        graphics.fill(left, top, left + width, top + ThermalSmelterLayout.MACHINE_HEIGHT, MACHINE_BG);
        outline(graphics, left, top, width, height, STRONG_BORDER);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 3, PANEL_HIGHLIGHT);
        graphics.fill(left + 1, top + 1, left + 3, top + height - 1, PANEL_HIGHLIGHT);
        graphics.fill(left + 1, top + height - 3, left + width - 1, top + height - 1, PANEL_SHADOW);
        graphics.fill(left + width - 3, top + 1, left + width - 1, top + height - 1, PANEL_SHADOW);
        graphics.fill(left + 2, top + 2, left + width - 2, top + 3, withAlpha(PANEL_HIGHLIGHT, 170));
        graphics.fill(left + 2, top + 2, left + 3, top + height - 2, withAlpha(PANEL_HIGHLIGHT, 170));
        graphics.fill(left + 8, top + ThermalSmelterLayout.MACHINE_HEIGHT + 1, left + width - 8, top + ThermalSmelterLayout.MACHINE_HEIGHT + 2, withAlpha(BORDER, 128));
        graphics.fill(left + 8, top + 113, left + width - 8, top + 114, withAlpha(PANEL_HIGHLIGHT, 130));
    }

    public static void drawProcess(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        panel(graphics, left, top, ThermalSmelterLayout.PROCESS_WIDTH, ThermalSmelterLayout.PROCESS_HEIGHT);
        panel(
                graphics,
                left + ThermalSmelterLayout.PROCESS_LEFT_PANEL_X,
                top + ThermalSmelterLayout.PROCESS_SIDE_PANEL_Y,
                ThermalSmelterLayout.PROCESS_SIDE_PANEL_WIDTH,
                ThermalSmelterLayout.PROCESS_SIDE_PANEL_HEIGHT
        );
        panel(
                graphics,
                left + ThermalSmelterLayout.PROCESS_RIGHT_PANEL_X,
                top + ThermalSmelterLayout.PROCESS_SIDE_PANEL_Y,
                ThermalSmelterLayout.PROCESS_SIDE_PANEL_WIDTH,
                ThermalSmelterLayout.PROCESS_SIDE_PANEL_HEIGHT
        );
        drawProcessLabels(graphics, font, left, top, view);

        chamber(graphics, font, left, top, view);

        slotFrame(graphics, left + ThermalSmelterLayout.PROCESS_INPUT_X, top + ThermalSmelterLayout.PROCESS_INPUT_Y, HEAT, true);
        slotFrame(
                graphics,
                left + ThermalSmelterLayout.PROCESS_ADDITIVE_X,
                top + ThermalSmelterLayout.PROCESS_ADDITIVE_Y,
                view.additiveActive() ? OPTICAL : EMPTY,
                view.additiveActive()
        );
        slotFrame(graphics, left + ThermalSmelterLayout.PROCESS_OUTPUT_X, top + ThermalSmelterLayout.PROCESS_OUTPUT_Y, PROGRESS, true);
        slotFrame(
                graphics,
                left + ThermalSmelterLayout.PROCESS_OUTPUT_SECOND_X,
                top + ThermalSmelterLayout.PROCESS_OUTPUT_SECOND_Y,
                PROGRESS,
                true
        );

        if (view.state() == ProcessState.OUTPUT_BLOCKED) {
            blockedOverlay(
                    graphics,
                    left + ThermalSmelterLayout.PROCESS_OUTPUT_X,
                    top + ThermalSmelterLayout.PROCESS_OUTPUT_Y
            );
            blockedOverlay(
                    graphics,
                    left + ThermalSmelterLayout.PROCESS_OUTPUT_SECOND_X,
                    top + ThermalSmelterLayout.PROCESS_OUTPUT_SECOND_Y
            );
        } else if (view.state() == ProcessState.INVALID) {
            invalidOverlay(
                    graphics,
                    left + ThermalSmelterLayout.PROCESS_INPUT_X,
                    top + ThermalSmelterLayout.PROCESS_INPUT_Y
            );
        }
    }

    public static void drawPlayerInventory(GuiGraphics graphics, Font font, int left, int top) {
        panel(graphics, left + 42, top + 116, 172, 90);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        left + ThermalSmelterLayout.PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        top + ThermalSmelterLayout.PLAYER_INVENTORY_Y + row * SLOT_SIZE
                );
            }
        }

        int hotbarY = ThermalSmelterLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(left + ThermalSmelterLayout.PLAYER_INVENTORY_X, top + hotbarY - 4, left + ThermalSmelterLayout.PLAYER_INVENTORY_X + 9 * SLOT_SIZE, top + hotbarY - 3, withAlpha(BORDER, 88));
        for (int column = 0; column < 9; column++) {
            ceramicSlot(graphics, left + ThermalSmelterLayout.PLAYER_INVENTORY_X + column * SLOT_SIZE, top + hotbarY);
        }
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static int withAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    private static void drawProcessLabels(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        drawCenteredText(graphics, font, "IN", left + ThermalSmelterLayout.PROCESS_INPUT_X + SLOT_SIZE / 2, top + 20, 0.64F, TEXT_SUB);
        drawCenteredText(graphics, font, "OUT", left + ThermalSmelterLayout.PROCESS_OUTPUT_X + SLOT_SIZE / 2, top + 20, 0.64F, TEXT_SUB);
    }

    private static void chamber(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        int x = left + ThermalSmelterLayout.PROCESS_CHAMBER_X;
        int y = top + ThermalSmelterLayout.PROCESS_CHAMBER_Y;
        int width = ThermalSmelterLayout.PROCESS_CHAMBER_WIDTH;
        int height = ThermalSmelterLayout.PROCESS_CHAMBER_HEIGHT;

        panel(graphics, x, y, width, height);
        insetPanel(
                graphics,
                left + ThermalSmelterLayout.PROCESS_INNER_X,
                top + ThermalSmelterLayout.PROCESS_INNER_Y,
                ThermalSmelterLayout.PROCESS_INNER_WIDTH,
                ThermalSmelterLayout.PROCESS_INNER_HEIGHT,
                CHAMBER_BG
        );

        graphics.fill(left + ThermalSmelterLayout.PROCESS_WORK_X1, top + ThermalSmelterLayout.PROCESS_WORK_Y1, left + ThermalSmelterLayout.PROCESS_DIVIDER_LEFT_X, top + ThermalSmelterLayout.PROCESS_WORK_Y2, withAlpha(PANEL_SHADOW, 34));
        graphics.fill(left + ThermalSmelterLayout.PROCESS_DIVIDER_LEFT_X + 2, top + ThermalSmelterLayout.PROCESS_WORK_Y1, left + ThermalSmelterLayout.PROCESS_DIVIDER_RIGHT_X - 2, top + ThermalSmelterLayout.PROCESS_WORK_Y2, withAlpha(PANEL_HIGHLIGHT, 54));
        graphics.fill(left + ThermalSmelterLayout.PROCESS_DIVIDER_RIGHT_X + 2, top + ThermalSmelterLayout.PROCESS_WORK_Y1, left + ThermalSmelterLayout.PROCESS_WORK_X2, top + ThermalSmelterLayout.PROCESS_WORK_Y2, withAlpha(PANEL_HIGHLIGHT, 42));
        chamberDividers(graphics, left, top);

        spMeter(graphics, font, left, top, view);
        processStages(graphics, font, left, top, view);
        centralReadout(graphics, font, left, top, view);
        parallelGrid(graphics, font, left, top, view);
        progressSegments(graphics, left, top, view);
    }

    private static void chamberDividers(GuiGraphics graphics, int left, int top) {
        int topY = top + ThermalSmelterLayout.PROCESS_DIVIDER_TOP_Y;
        int bottomY = top + ThermalSmelterLayout.PROCESS_DIVIDER_BOTTOM_Y;
        int leftDivider = left + ThermalSmelterLayout.PROCESS_DIVIDER_LEFT_X;
        int rightDivider = left + ThermalSmelterLayout.PROCESS_DIVIDER_RIGHT_X;

        graphics.fill(leftDivider, topY, leftDivider + 1, bottomY, withAlpha(BORDER, 170));
        graphics.fill(leftDivider + 1, topY, leftDivider + 2, bottomY, withAlpha(PANEL_HIGHLIGHT, 90));
        graphics.fill(rightDivider, topY, rightDivider + 1, bottomY, withAlpha(BORDER, 170));
        graphics.fill(rightDivider + 1, topY, rightDivider + 2, bottomY, withAlpha(PANEL_HIGHLIGHT, 90));
        graphics.fill(left + ThermalSmelterLayout.PROCESS_DIVIDER_LEFT_X + 3, top + ThermalSmelterLayout.PROCESS_HORIZONTAL_DIVIDER_Y, left + ThermalSmelterLayout.PROCESS_DIVIDER_RIGHT_X - 2, top + ThermalSmelterLayout.PROCESS_HORIZONTAL_DIVIDER_Y + 1, withAlpha(BORDER, 130));
    }

    private static void spMeter(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        int x = left + ThermalSmelterLayout.PROCESS_SP_BAR_X;
        int y = top + ThermalSmelterLayout.PROCESS_SP_BAR_Y;
        int width = ThermalSmelterLayout.PROCESS_SP_BAR_WIDTH;
        int height = ThermalSmelterLayout.PROCESS_SP_BAR_HEIGHT;
        double spRatio = clamp01(view.opticalRatio());
        int fillHeight = (int) Math.round(height * spRatio);

        drawCenteredText(graphics, font, "SP", x + width / 2, top + 23, 0.50F, TEXT_SUB);
        insetPanel(graphics, x - 1, y - 1, width + 2, height + 2, withAlpha(CHAMBER_SHADOW, 90));
        graphics.fill(x, y, x + width, y + height, withAlpha(PANEL_SHADOW, 80));
        graphics.fill(x, y + height - fillHeight, x + width, y + height, withAlpha(OPTICAL, (int) Math.round(118 + spRatio * 118)));
        graphics.fill(x + 2, y + height - fillHeight, x + width - 1, y + height, withAlpha(HEAT_LIGHT, (int) Math.round(45 + spRatio * 70)));
    }

    private static void processStages(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        boolean hasInput = view.state() != ProcessState.EMPTY;
        boolean heating = view.state() == ProcessState.READY || view.state() == ProcessState.PENDING || view.progressRatio() > 0.0;
        boolean complete = view.progressRatio() > 0.85 || view.state() == ProcessState.OUTPUT_BLOCKED;

        pixelBlock(
                graphics,
                left + ThermalSmelterLayout.PROCESS_STAGE_X1,
                top + ThermalSmelterLayout.PROCESS_STAGE_Y,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                view.state() == ProcessState.INVALID ? STATUS_INVALID : HEAT,
                hasInput
        );
        pixelBlock(
                graphics,
                left + ThermalSmelterLayout.PROCESS_STAGE_X2,
                top + ThermalSmelterLayout.PROCESS_STAGE_Y,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                HEAT_LIGHT,
                heating
        );
        pixelBlock(
                graphics,
                left + ThermalSmelterLayout.PROCESS_STAGE_X3,
                top + ThermalSmelterLayout.PROCESS_STAGE_Y,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE,
                view.state() == ProcessState.OUTPUT_BLOCKED ? STATUS_INVALID : PROGRESS,
                complete
        );
    }

    private static void centralReadout(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        int x = left + ThermalSmelterLayout.PROCESS_TEMP_BOX_X;
        int y = top + ThermalSmelterLayout.PROCESS_TEMP_BOX_Y;
        int width = ThermalSmelterLayout.PROCESS_TEMP_BOX_WIDTH;
        double heatRatio = clamp01(view.heatRatio());

        if (view.highTemperature()) {
            graphics.fill(x + 4, y + 3, x + width - 4, y + 32, withAlpha(HEAT, 34));
        }

        drawCenteredText(graphics, font, "TEMP", left + ThermalSmelterLayout.PROCESS_MAIN_CENTER_X, top + ThermalSmelterLayout.PROCESS_TEMP_LABEL_Y, 0.50F, TEXT_SUB);
        drawCenteredText(
                graphics,
                font,
                view.temperature() + "K",
                left + ThermalSmelterLayout.PROCESS_MAIN_CENTER_X,
                top + ThermalSmelterLayout.PROCESS_TEMP_VALUE_Y,
                0.86F,
                heatRatio > 0.05 ? HEAT : TEXT_SUB
        );
        staticRecipeArrow(
                graphics,
                left + ThermalSmelterLayout.PROCESS_RECIPE_CLICK_X,
                top + ThermalSmelterLayout.PROCESS_RECIPE_CLICK_Y,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_WIDTH,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_HEIGHT
        );

        if (view.state() == ProcessState.INVALID) {
            graphics.fill(x + width / 2 - 5, y + 15, x + width / 2 + 5, y + 24, withAlpha(STATUS_INVALID, 150));
        } else if (view.state() == ProcessState.OUTPUT_BLOCKED) {
            graphics.fill(x + width - 16, y + 24, x + width - 7, y + 31, withAlpha(STATUS_INVALID, 155));
        }
    }

    private static void parallelGrid(GuiGraphics graphics, Font font, int left, int top, ProcessView view) {
        int activeCells = Math.max(1, Math.min(4, view.parallelCount()));
        int cellIndex = 0;
        drawCenteredText(graphics, font, "PAR", left + ThermalSmelterLayout.PROCESS_PARALLEL_CENTER_X, top + ThermalSmelterLayout.PROCESS_PARALLEL_LABEL_Y, 0.44F, TEXT_SUB);
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 2; column++) {
                int x = left + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_X
                        + column * (ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_GAP);
                int y = top + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_Y
                        + row * (ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_GAP);
                boolean active = cellIndex < activeCells;
                int alpha = active ? 205 : 38;
                graphics.fill(x, y, x + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE, y + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE, withAlpha(PROGRESS, alpha));
                outline(
                        graphics,
                        x,
                        y,
                        ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE,
                        ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE,
                        active ? withAlpha(STRONG_BORDER, 160) : withAlpha(BORDER, 112)
                );
                cellIndex++;
            }
        }

        int stateColor = stateColor(view.state());
        graphics.fill(
                left + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_X,
                top + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_Y,
                left + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_X + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_WIDTH,
                top + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_Y + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_HEIGHT,
                withAlpha(stateColor, 122)
        );
        outline(
                graphics,
                left + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_X,
                top + ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_Y,
                ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_WIDTH,
                ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_HEIGHT,
                withAlpha(stateColor, 188)
        );
        drawCenteredText(graphics, font, "STAT", left + ThermalSmelterLayout.PROCESS_PARALLEL_CENTER_X, top + ThermalSmelterLayout.PROCESS_STATUS_LABEL_Y, 0.40F, TEXT_SUB);
        drawCenteredText(graphics, font, stateCode(view.state()), left + ThermalSmelterLayout.PROCESS_PARALLEL_CENTER_X, top + ThermalSmelterLayout.PROCESS_STATUS_LABEL_Y + 8, 0.40F, TEXT_SUB);
    }

    private static void progressSegments(GuiGraphics graphics, int left, int top, ProcessView view) {
        int activeSegments = (int) Math.round(clamp01(view.progressRatio()) * ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENTS);
        for (int segment = 0; segment < ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENTS; segment++) {
            int x = left + ThermalSmelterLayout.PROCESS_PROGRESS_X
                    + segment * (ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_WIDTH + ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_GAP);
            boolean active = segment < activeSegments;
            pixelBlock(
                    graphics,
                    x,
                    top + ThermalSmelterLayout.PROCESS_PROGRESS_Y,
                    ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_WIDTH,
                    ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_HEIGHT,
                    segment < activeSegments - 2 ? HEAT : PROGRESS,
                    active
            );
        }
    }

    private static void pixelBlock(GuiGraphics graphics, int x, int y, int width, int height, int color, boolean active) {
        graphics.fill(x, y, x + width, y + height, active ? withAlpha(color, 212) : withAlpha(EMPTY, 72));
        outline(graphics, x, y, width, height, active ? withAlpha(STRONG_BORDER, 185) : withAlpha(BORDER, 112));
        if (active && width > 3 && height > 3) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, withAlpha(HEAT_PALE, 116));
            graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, withAlpha(HEAT_DARK, 92));
        }
    }

    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        outline(graphics, x, y, width, height, STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, PANEL_SHADOW);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, withAlpha(STRONG_BORDER, 40));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, PANEL_HIGHLIGHT);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, PANEL_HIGHLIGHT);
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + SLOT_SIZE - 2, y + 2, x + SLOT_SIZE - 1, y + SLOT_SIZE - 2, withAlpha(color, 155));
            graphics.fill(x + 2, y + SLOT_SIZE - 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 1, withAlpha(color, 90));
        } else {
            outline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, withAlpha(EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BG);
        outline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, BORDER);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + 2, SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + SLOT_SIZE - 1, SLOT_SHADOW);
        graphics.fill(x + 1, y + SLOT_SIZE - 2, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_HIGHLIGHT);
        graphics.fill(x + SLOT_SIZE - 2, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, withAlpha(SLOT_HIGHLIGHT, 36));
    }

    private static void staticRecipeArrow(GuiGraphics graphics, int x, int y, int width, int height) {
        pixelArrowRight(graphics, x, y, width, height, withAlpha(STRONG_BORDER, 145));
        graphics.fill(x + 2, y + 2, x + width - 11, y + 3, withAlpha(PANEL_HIGHLIGHT, 70));
    }

    private static void pixelArrowRight(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int centerY = y + height / 2;
        int maxHalfHeight = Math.max(1, height / 2);
        int shaftHeight = Math.max(1, height / 3);

        if (shaftHeight % 2 == 0) {
            shaftHeight++;
        }

        int shaftTop = centerY - shaftHeight / 2;
        int shaftBottom = shaftTop + shaftHeight;
        int headWidth = Math.min(9, Math.max(5, width / 5));
        int headLeft = x + width - headWidth;
        int tipX = x + width - 1;

        graphics.fill(x, shaftTop, headLeft, shaftBottom, color);
        for (int arrowX = headLeft; arrowX <= tipX; arrowX++) {
            int distanceToTip = tipX - arrowX;
            int halfHeight = Math.round(distanceToTip * maxHalfHeight / (float) Math.max(1, headWidth - 1));
            graphics.fill(arrowX, centerY - halfHeight, arrowX + 1, centerY + halfHeight + 1, color);
        }
    }

    private static void blockedOverlay(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, withAlpha(STATUS_INVALID, 72));
        for (int i = 3; i < SLOT_SIZE - 3; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, STATUS_INVALID);
            graphics.fill(x + SLOT_SIZE - 1 - i, y + i, x + SLOT_SIZE - i, y + i + 1, STATUS_INVALID);
        }
    }

    private static void invalidOverlay(GuiGraphics graphics, int x, int y) {
        graphics.fill(x + SLOT_SIZE - 5, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, STATUS_INVALID);
        graphics.fill(x + SLOT_SIZE - 5, y + SLOT_SIZE - 4, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2, STRONG_BORDER);
    }

    private static void drawCenteredText(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(centerX - textWidth * scale / 2.0F);
        drawText(graphics, font, text, x, y, scale, color);
    }

    private static void drawRightText(GuiGraphics graphics, Font font, String text, int rightX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(rightX - textWidth * scale);
        drawText(graphics, font, text, x, y, scale, color);
    }

    private static void drawText(GuiGraphics graphics, Font font, String text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static String stateCode(ProcessState state) {
        return switch (state) {
            case EMPTY -> "IDLE";
            case READY -> "RDY";
            case PENDING -> "RUN";
            case INVALID -> "ERR";
            case OUTPUT_BLOCKED -> "FULL";
        };
    }

    private static int stateColor(ProcessState state) {
        return switch (state) {
            case EMPTY -> STATUS_EMPTY;
            case READY -> STATUS_READY;
            case PENDING -> STATUS_PENDING;
            case INVALID, OUTPUT_BLOCKED -> STATUS_INVALID;
        };
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    public enum ProcessState {
        EMPTY,
        READY,
        PENDING,
        INVALID,
        OUTPUT_BLOCKED
    }

    public record ProcessView(
            double heatRatio,
            double progressRatio,
            double opticalRatio,
            ProcessState state,
            boolean additiveActive,
            boolean highTemperature,
            int temperature,
            int progressPercent,
            int spPercent,
            int parallelCount
    ) {
    }
}
