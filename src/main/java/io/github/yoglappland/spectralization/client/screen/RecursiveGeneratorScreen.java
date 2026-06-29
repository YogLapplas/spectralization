package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.RecursiveGeneratorBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.RecursiveGeneratorState;
import io.github.yoglappland.spectralization.menu.RecursiveGeneratorLayout;
import io.github.yoglappland.spectralization.menu.RecursiveGeneratorMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecursiveGeneratorScreen extends SpectralMachineScreen<RecursiveGeneratorMenu> {
    private static final int[] LAYER_COLORS = {
            0xFF4C7A55,
            0xFF437765,
            0xFF40727E,
            0xFF466B8D,
            0xFF536396,
            0xFF675C96,
            0xFF7F558E,
            0xFF974F82,
            0xFFAE4E73,
            0xFFC15163,
            0xFFCD6557,
            0xFFD77951,
            0xFFDD8D4F,
            0xFFE4A154,
            0xFFEAB45E,
            0xFFEFC567
    };

    public RecursiveGeneratorScreen(RecursiveGeneratorMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "recursive_generator",
                RecursiveGeneratorLayout.IMAGE_WIDTH,
                RecursiveGeneratorLayout.IMAGE_HEIGHT,
                RecursiveGeneratorLayout.INVENTORY_LABEL_X,
                RecursiveGeneratorLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderLockedSlotOverlay(graphics, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawMainPanel(graphics);
        drawEnergyBar(graphics);
        slotFrame(
                graphics,
                leftPos + RecursiveGeneratorLayout.INPUT_SLOT_X,
                topPos + RecursiveGeneratorLayout.INPUT_SLOT_Y,
                data(RecursiveGeneratorBlockEntity.DATA_REMAINING_0) > 0
        );
        drawRecursiveCore(graphics, partialTick);
        drawTimeBars(graphics);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void renderMachineTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return;
        }
        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (insideLocal(mouseX, mouseY,
                RecursiveGeneratorLayout.ENERGY_BAR_X - 2,
                RecursiveGeneratorLayout.ENERGY_BAR_Y - 2,
                RecursiveGeneratorLayout.ENERGY_BAR_WIDTH + 4,
                RecursiveGeneratorLayout.ENERGY_BAR_HEIGHT + 4)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(tt("tooltip.energy",
                    data(RecursiveGeneratorBlockEntity.DATA_ENERGY),
                    data(RecursiveGeneratorBlockEntity.DATA_CAPACITY)));
            tooltip.add(tt("tooltip.output", data(RecursiveGeneratorBlockEntity.DATA_OUTPUT)));
            return tooltip;
        }

        if (insideLocal(mouseX, mouseY,
                RecursiveGeneratorLayout.INPUT_SLOT_X,
                RecursiveGeneratorLayout.INPUT_SLOT_Y,
                RecursiveGeneratorLayout.SLOT_SIZE,
                RecursiveGeneratorLayout.SLOT_SIZE)) {
            return List.of(tt("tooltip.input"));
        }

        if (insideLocal(mouseX, mouseY,
                RecursiveGeneratorLayout.CORE_REGION_X,
                RecursiveGeneratorLayout.CORE_REGION_Y,
                RecursiveGeneratorLayout.CORE_REGION_WIDTH,
                RecursiveGeneratorLayout.CORE_REGION_HEIGHT)) {
            return List.of(
                    tt("tooltip.depth", activeLayerCount()),
                    tt("tooltip.output", data(RecursiveGeneratorBlockEntity.DATA_OUTPUT))
            );
        }

        int localX = mouseX - leftPos;
        int localY = mouseY - topPos;
        int activeColumns = activeLayerCount();
        for (int column = 0; column < activeColumns; column++) {
            int layer = layerForChartColumn(column);
            if (layer < 0) {
                continue;
            }
            int x = chartColumnX(column, activeColumns);
            if (ThermalSmelterUiSkin.inside(localX, localY,
                    x - 2,
                    chartPlotTop() - 2,
                    RecursiveGeneratorLayout.TIME_CHART_COLUMN_WIDTH + 4,
                    chartPlotHeight() + 4)) {
                return List.of(tt("tooltip.layer", layer + 1, formatSeconds(remaining(layer))));
            }
        }
        return List.of();
    }

    private void drawMainPanel(GuiGraphics graphics) {
        drawPanel(
                graphics,
                leftPos + RecursiveGeneratorLayout.MAIN_PANEL_X,
                topPos + RecursiveGeneratorLayout.MAIN_PANEL_Y,
                RecursiveGeneratorLayout.MAIN_PANEL_WIDTH,
                RecursiveGeneratorLayout.MAIN_PANEL_HEIGHT
        );

        region(graphics, leftPos + RecursiveGeneratorLayout.LEFT_REGION_X, topPos + RecursiveGeneratorLayout.LEFT_REGION_Y,
                RecursiveGeneratorLayout.LEFT_REGION_WIDTH, RecursiveGeneratorLayout.LEFT_REGION_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 30));
        region(graphics, leftPos + RecursiveGeneratorLayout.CORE_REGION_X, topPos + RecursiveGeneratorLayout.CORE_REGION_Y,
                RecursiveGeneratorLayout.CORE_REGION_WIDTH, RecursiveGeneratorLayout.CORE_REGION_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(0xFF2C2529, 18));
        region(graphics, leftPos + RecursiveGeneratorLayout.TIME_REGION_X, topPos + RecursiveGeneratorLayout.TIME_REGION_Y,
                RecursiveGeneratorLayout.TIME_REGION_WIDTH, RecursiveGeneratorLayout.TIME_REGION_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(0xFFFFB45E, 16));

        int dividerLeft = leftPos + RecursiveGeneratorLayout.CORE_REGION_X - 5;
        int dividerRight = leftPos + RecursiveGeneratorLayout.TIME_REGION_X - 5;
        int y1 = topPos + RecursiveGeneratorLayout.MAIN_PANEL_Y + 8;
        int y2 = topPos + RecursiveGeneratorLayout.MAIN_PANEL_Y + RecursiveGeneratorLayout.MAIN_PANEL_HEIGHT - 8;
        verticalDivider(graphics, dividerLeft, y1, y2);
        verticalDivider(graphics, dividerRight, y1, y2);
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        drawVerticalGauge(
                graphics,
                leftPos + RecursiveGeneratorLayout.ENERGY_BAR_X,
                topPos + RecursiveGeneratorLayout.ENERGY_BAR_Y,
                RecursiveGeneratorLayout.ENERGY_BAR_WIDTH,
                RecursiveGeneratorLayout.ENERGY_BAR_HEIGHT,
                data(RecursiveGeneratorBlockEntity.DATA_ENERGY),
                Math.max(1, data(RecursiveGeneratorBlockEntity.DATA_CAPACITY)),
                ThermalSmelterUiSkin.STATUS_READY,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT
        );
    }

    private void drawRecursiveCore(GuiGraphics graphics, float partialTick) {
        int cx = leftPos + RecursiveGeneratorLayout.CORE_CENTER_X;
        int cy = topPos + RecursiveGeneratorLayout.CORE_CENTER_Y;
        int depth = data(RecursiveGeneratorBlockEntity.DATA_ACTIVE_DEPTH);
        boolean active = data(RecursiveGeneratorBlockEntity.DATA_REMAINING_0) > 0;

        int backSize = RecursiveGeneratorLayout.CORE_SIZE + 8;
        softRect(graphics, cx - backSize / 2, cy - backSize / 2, backSize, backSize,
                ThermalSmelterUiSkin.withAlpha(0xFF11100F, active ? 32 : 18));

        for (int layer = RecursiveGeneratorState.MAX_LAYERS - 1; layer >= 0; layer--) {
            int size = RecursiveGeneratorLayout.CORE_SIZE - layer * 2;
            if (size <= 8) {
                continue;
            }
            boolean layerActive = remaining(layer) > 0;
            int alpha = layerActive ? 130 + Math.min(70, layer * 8) : 38;
            int color = layerColor(layer);
            int offsetX = layerActive ? layerJitter(layer, depth, partialTick, true) : 0;
            int offsetY = layerActive ? layerJitter(layer, depth, partialTick, false) : 0;
            int x = cx - size / 2 + offsetX;
            int y = cy - size / 2 + offsetY;
            outline(graphics, x, y, size, size, ThermalSmelterUiSkin.withAlpha(color, alpha));
            graphics.fill(x + 1, y + 1, x + size - 1, y + 2,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, layerActive ? 58 : 18));
            graphics.fill(x + 1, y + size - 2, x + size - 1, y + size - 1,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, layerActive ? 68 : 22));
        }

        int center = 8;
        graphics.fill(cx - center / 2, cy - center / 2, cx + center / 2, cy + center / 2,
                ThermalSmelterUiSkin.withAlpha(active ? layerColor(depth) : ThermalSmelterUiSkin.EMPTY, active ? 176 : 72));
        outline(graphics, cx - center / 2, cy - center / 2, center, center,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 160));
    }

    private void drawTimeBars(GuiGraphics graphics) {
        int chartX = leftPos + RecursiveGeneratorLayout.TIME_CHART_X;
        int chartY = topPos + RecursiveGeneratorLayout.TIME_CHART_Y;
        int axisX = leftPos + chartAxisX();
        int plotTop = topPos + chartPlotTop();
        int plotBottom = topPos + chartPlotBottom();
        int axisColor = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 138);

        graphics.fill(chartX, chartY, chartX + RecursiveGeneratorLayout.TIME_CHART_WIDTH,
                chartY + RecursiveGeneratorLayout.TIME_CHART_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 46));
        graphics.fill(axisX, plotTop, axisX + 1, plotBottom + 1, axisColor);
        graphics.fill(axisX, plotBottom, chartX + RecursiveGeneratorLayout.TIME_CHART_WIDTH - 2, plotBottom + 1, axisColor);

        for (int tick = 0; tick <= 2; tick++) {
            int y = plotBottom - tick * chartPlotHeight() / 2;
            graphics.fill(axisX - 2, y, axisX + 1, y + 1,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 86));
            if (tick > 0) {
                graphics.fill(axisX + 1, y, chartX + RecursiveGeneratorLayout.TIME_CHART_WIDTH - 3, y + 1,
                        ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 32));
            }
        }

        int activeColumns = activeLayerCount();
        for (int column = 0; column < activeColumns; column++) {
            int layer = layerForChartColumn(column);
            if (layer < 0) {
                continue;
            }
            int x = leftPos + chartColumnX(column, activeColumns);
            int layerRemaining = remaining(layer);
            int fill = Math.round((chartPlotHeight() - 1)
                    * Math.max(0, Math.min(layerRemaining, RecursiveGeneratorState.BAR_SCALE_TICKS))
                    / (float) RecursiveGeneratorState.BAR_SCALE_TICKS);

            if (fill > 0) {
                int color = layerColor(layer);
                int fillTop = plotBottom - fill;
                graphics.fill(x, fillTop, x + RecursiveGeneratorLayout.TIME_CHART_COLUMN_WIDTH, plotBottom,
                        ThermalSmelterUiSkin.withAlpha(color, 184));
                graphics.fill(x, fillTop, x + RecursiveGeneratorLayout.TIME_CHART_COLUMN_WIDTH, fillTop + 1,
                        ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 76));
            }
        }
    }

    private void renderLockedSlotOverlay(GuiGraphics graphics, float partialTick) {
        if (data(RecursiveGeneratorBlockEntity.DATA_INPUT_LOCKED) <= 0) {
            return;
        }
        int x = leftPos + RecursiveGeneratorLayout.INPUT_SLOT_X;
        int y = topPos + RecursiveGeneratorLayout.INPUT_SLOT_Y;
        int elapsed = RecursiveGeneratorState.BAR_SCALE_TICKS - data(RecursiveGeneratorBlockEntity.DATA_REMAINING_0);
        float progress = Math.max(0.0F, Math.min(1.0F, (elapsed + partialTick) / 12.0F));
        int cover = Math.round((RecursiveGeneratorLayout.SLOT_SIZE / 2.0F + 1.0F) * progress);
        int shade = ThermalSmelterUiSkin.withAlpha(0xFF201A18, 210);
        graphics.fill(x, y, x + cover, y + RecursiveGeneratorLayout.SLOT_SIZE, shade);
        graphics.fill(x + RecursiveGeneratorLayout.SLOT_SIZE - cover, y, x + RecursiveGeneratorLayout.SLOT_SIZE, y + RecursiveGeneratorLayout.SLOT_SIZE, shade);
        if (progress >= 1.0F) {
            int seam = x + RecursiveGeneratorLayout.SLOT_SIZE / 2;
            graphics.fill(seam - 1, y + 2, seam, y + RecursiveGeneratorLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 190));
            graphics.fill(seam, y + 2, seam + 1, y + RecursiveGeneratorLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 80));
        }
        outline(graphics, x, y, RecursiveGeneratorLayout.SLOT_SIZE, RecursiveGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.withAlpha(0xFFF0A340, 150));
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        drawPanel(
                graphics,
                leftPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_PANEL_X,
                topPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_PANEL_Y,
                RecursiveGeneratorLayout.PLAYER_INVENTORY_PANEL_WIDTH,
                RecursiveGeneratorLayout.PLAYER_INVENTORY_PANEL_HEIGHT
        );

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_X + column * RecursiveGeneratorLayout.SLOT_SIZE,
                        topPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_Y + row * RecursiveGeneratorLayout.SLOT_SIZE
                );
            }
        }

        int hotbarY = RecursiveGeneratorLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(
                leftPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_X,
                topPos + hotbarY - 4,
                leftPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_X + 9 * RecursiveGeneratorLayout.SLOT_SIZE,
                topPos + hotbarY - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88)
        );

        for (int column = 0; column < 9; column++) {
            ceramicSlot(
                    graphics,
                    leftPos + RecursiveGeneratorLayout.PLAYER_INVENTORY_X + column * RecursiveGeneratorLayout.SLOT_SIZE,
                    topPos + hotbarY
            );
        }
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth,
                topPos + RecursiveGeneratorLayout.MACHINE_HEIGHT, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1,
                topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + RecursiveGeneratorLayout.MACHINE_HEIGHT + 1,
                leftPos + imageWidth - 8, topPos + RecursiveGeneratorLayout.MACHINE_HEIGHT + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 128));
    }

    private static void drawVerticalGauge(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int value,
            int max,
            int color,
            int glintColor
    ) {
        int fillHeight = max <= 0 ? 0 : Math.round(height * Math.max(0, Math.min(value, max)) / (float) max);
        insetPanel(graphics, x - 1, y - 1, width + 2, height + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 92));
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 78));

        if (fillHeight > 0) {
            int fillTop = y + height - fillHeight;
            graphics.fill(x, fillTop, x + width, y + height,
                    ThermalSmelterUiSkin.withAlpha(color, 126 + Math.round(76 * fillHeight / (float) Math.max(1, height))));
            graphics.fill(x + 2, fillTop, x + width - 1, y + height,
                    ThermalSmelterUiSkin.withAlpha(glintColor, 46));
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private static void region(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        softRect(graphics, x, y, width, height, fill);
    }

    private static void softRect(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 76));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 46));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 54));
    }

    private static void verticalDivider(GuiGraphics graphics, int x, int y1, int y2) {
        graphics.fill(x, y1, x + 1, y2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 122));
        graphics.fill(x + 1, y1, x + 2, y2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 78));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, boolean active) {
        ceramicSlot(graphics, x, y);
        int color = active ? 0xFFF0A340 : ThermalSmelterUiSkin.EMPTY;
        outline(graphics, x, y, RecursiveGeneratorLayout.SLOT_SIZE, RecursiveGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.withAlpha(color, active ? 150 : 95));
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + RecursiveGeneratorLayout.SLOT_SIZE, y + RecursiveGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, RecursiveGeneratorLayout.SLOT_SIZE, RecursiveGeneratorLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + RecursiveGeneratorLayout.SLOT_SIZE - 1, y + 2,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + RecursiveGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + RecursiveGeneratorLayout.SLOT_SIZE - 2,
                x + RecursiveGeneratorLayout.SLOT_SIZE - 1, y + RecursiveGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + RecursiveGeneratorLayout.SLOT_SIZE - 2, y + 1,
                x + RecursiveGeneratorLayout.SLOT_SIZE - 1, y + RecursiveGeneratorLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + RecursiveGeneratorLayout.SLOT_SIZE - 2,
                y + RecursiveGeneratorLayout.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 36));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private boolean insideLocal(int mouseX, int mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private int remaining(int layer) {
        return data(RecursiveGeneratorBlockEntity.DATA_REMAINING_0 + layer);
    }

    private int activeLayerCount() {
        int count = 0;
        for (int layer = 0; layer < RecursiveGeneratorState.MAX_LAYERS; layer++) {
            if (remaining(layer) > 0) {
                count++;
            }
        }
        return count;
    }

    private static int layerColor(int layer) {
        return LAYER_COLORS[Math.max(0, Math.min(layer, LAYER_COLORS.length - 1))];
    }

    private static int layerJitter(int layer, int depth, float partialTick, boolean horizontal) {
        long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        double t = gameTime + partialTick;
        double seed = (layer + 1) * 9.731D + depth * 4.217D;
        double rate = horizontal
                ? 0.105D + ((layer * 17 + depth * 5) % 11) * 0.013D + depth * 0.006D
                : 0.088D + ((layer * 23 + depth * 3) % 13) * 0.011D + depth * 0.005D;
        double phase = horizontal ? seed : seed * 1.618D + 0.77D;
        double wobble = Math.sin(t * rate + phase) + 0.47D * Math.sin(t * rate * 2.31D + phase * 0.43D);
        double amplitude = Math.min(3.4D, 0.62D + layer * 0.07D + depth * 0.22D);
        return (int) Math.round(wobble * amplitude * 0.68D);
    }

    private static int chartAxisX() {
        return RecursiveGeneratorLayout.TIME_CHART_X + RecursiveGeneratorLayout.TIME_CHART_AXIS_INSET_X;
    }

    private static int chartPlotTop() {
        return RecursiveGeneratorLayout.TIME_CHART_Y + 2;
    }

    private static int chartPlotBottom() {
        return RecursiveGeneratorLayout.TIME_CHART_Y
                + RecursiveGeneratorLayout.TIME_CHART_HEIGHT
                - RecursiveGeneratorLayout.TIME_CHART_AXIS_INSET_BOTTOM;
    }

    private static int chartPlotHeight() {
        return chartPlotBottom() - chartPlotTop();
    }

    private static int chartBarsLeft() {
        return chartAxisX() + 2;
    }

    private static int chartColumnX(int column, int activeColumns) {
        int width = RecursiveGeneratorLayout.TIME_CHART_COLUMN_WIDTH
                + RecursiveGeneratorLayout.TIME_CHART_COLUMN_GAP;
        return chartBarsLeft() + column * width;
    }

    private int layerForChartColumn(int column) {
        int seen = 0;
        for (int layer = RecursiveGeneratorState.MAX_LAYERS - 1; layer >= 0; layer--) {
            if (remaining(layer) <= 0) {
                continue;
            }
            if (seen == column) {
                return layer;
            }
            seen++;
        }
        return -1;
    }

    private Component tt(String suffix, Object... args) {
        return Component.translatable("screen.spectralization.recursive_generator." + suffix, args);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0);
    }
}
