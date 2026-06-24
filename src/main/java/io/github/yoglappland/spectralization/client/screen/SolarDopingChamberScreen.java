package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.SolarDopingPieChart;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberLayout;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class SolarDopingChamberScreen extends SpectralMachineScreen<SolarDopingChamberMenu> {
    private static final int ENERGY_COLOR = 0xFF68A7FF;
    private static final int HEIGHT_COLOR = 0xFFFFB24A;
    private static final int PROBABILITY_COLOR = 0xFF9FE7DF;
    private static final int PARTICLE_COLORS[] = {
            0xFFA9F3FF,
            0xFFFFB35C,
            0xFFC49BFF,
            0xFFFF7A9A,
            0xFFD7F68A
    };

    public SolarDopingChamberScreen(SolarDopingChamberMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "solar_doping_chamber",
                SolarDopingChamberLayout.IMAGE_WIDTH,
                SolarDopingChamberLayout.IMAGE_HEIGHT,
                SolarDopingChamberLayout.INVENTORY_LABEL_X,
                SolarDopingChamberLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawMachinePanels(graphics, partialTick, mouseX, mouseY);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawMachinePanels(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawPanel(graphics, leftPos + SolarDopingChamberLayout.PROCESS_X, topPos + SolarDopingChamberLayout.PROCESS_Y,
                SolarDopingChamberLayout.PROCESS_WIDTH, SolarDopingChamberLayout.PROCESS_HEIGHT);
        drawPanel(graphics, leftPos + SolarDopingChamberLayout.LEFT_PANEL_X, topPos + SolarDopingChamberLayout.SIDE_PANEL_Y,
                SolarDopingChamberLayout.SIDE_PANEL_WIDTH, SolarDopingChamberLayout.SIDE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + SolarDopingChamberLayout.RIGHT_PANEL_X, topPos + SolarDopingChamberLayout.SIDE_PANEL_Y,
                SolarDopingChamberLayout.SIDE_PANEL_WIDTH, SolarDopingChamberLayout.SIDE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + SolarDopingChamberLayout.CHAMBER_X, topPos + SolarDopingChamberLayout.CHAMBER_Y,
                SolarDopingChamberLayout.CHAMBER_WIDTH, SolarDopingChamberLayout.CHAMBER_HEIGHT);
        insetPanel(graphics, leftPos + SolarDopingChamberLayout.CHAMBER_INNER_X,
                topPos + SolarDopingChamberLayout.CHAMBER_INNER_Y,
                SolarDopingChamberLayout.CHAMBER_INNER_WIDTH,
                SolarDopingChamberLayout.CHAMBER_INNER_HEIGHT,
                ThermalSmelterUiSkin.CHAMBER_BG);
        subtleRegion(graphics, leftPos + SolarDopingChamberLayout.MAIN_PANEL_X,
                topPos + SolarDopingChamberLayout.MAIN_PANEL_Y,
                SolarDopingChamberLayout.MAIN_PANEL_WIDTH,
                SolarDopingChamberLayout.MAIN_PANEL_HEIGHT);

        drawMainDivider(graphics);
        drawParticles(graphics, partialTick);
        drawEnergyBar(graphics);
        drawHeightBar(graphics);
        drawProbabilityPie(graphics);
        drawStatusStrip(graphics);
        drawSlots(graphics);
    }

    private void drawMainDivider(GuiGraphics graphics) {
        int x = leftPos + SolarDopingChamberLayout.HEIGHT_DIVIDER_X;
        int top = topPos + SolarDopingChamberLayout.MAIN_PANEL_Y + 7;
        int bottom = topPos + SolarDopingChamberLayout.MAIN_PANEL_Y + SolarDopingChamberLayout.MAIN_PANEL_HEIGHT - 7;
        int dividerY = topPos + SolarDopingChamberLayout.WORK_DIVIDER_Y;
        int left = leftPos + SolarDopingChamberLayout.WORK_REGION_X + 5;
        int right = leftPos + SolarDopingChamberLayout.HEIGHT_DIVIDER_X - 5;

        graphics.fill(x, top, x + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 124));
        graphics.fill(x + 1, top, x + 2, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 58));
        graphics.fill(left, dividerY, right, dividerY + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
        graphics.fill(left, dividerY + 1, right, dividerY + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 38));
    }

    private void drawParticles(GuiGraphics graphics, float partialTick) {
        int state = data(SolarDopingChamberBlockEntity.DATA_STATE);
        boolean active = state == SolarDopingChamberBlockEntity.STATE_RUNNING || state == SolarDopingChamberBlockEntity.STATE_READY;
        int alpha = active ? 118 : 42;
        float time = ((minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0) + partialTick) * 0.035F;
        int left = leftPos + SolarDopingChamberLayout.PARTICLE_AREA_X;
        int top = topPos + SolarDopingChamberLayout.PARTICLE_AREA_Y;
        int width = SolarDopingChamberLayout.PARTICLE_AREA_WIDTH;
        int height = SolarDopingChamberLayout.PARTICLE_AREA_HEIGHT;

        for (int index = 0; index < 14; index++) {
            float phase = fract(time + index * 0.137F);
            int x = left + Math.floorMod(index * 17 + 3, Math.max(1, width));
            int y = top + Math.round(phase * Math.max(0, height - 1));
            int color = ThermalSmelterUiSkin.withAlpha(PARTICLE_COLORS[index % PARTICLE_COLORS.length], alpha);
            int size = active && index % 4 == 0 ? 2 : 1;
            graphics.fill(x, y, Math.min(left + width, x + size), Math.min(top + height, y + size), color);
        }
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        int x = leftPos + SolarDopingChamberLayout.ENERGY_BAR_X;
        int y = topPos + SolarDopingChamberLayout.ENERGY_BAR_Y;
        drawVerticalGauge(
                graphics,
                x,
                y,
                SolarDopingChamberLayout.BAR_WIDTH,
                SolarDopingChamberLayout.BAR_HEIGHT,
                data(SolarDopingChamberBlockEntity.DATA_ENERGY),
                data(SolarDopingChamberBlockEntity.DATA_ENERGY_MAX),
                ENERGY_COLOR
        );
        drawCenteredText(graphics, font, "E", x + SolarDopingChamberLayout.BAR_WIDTH / 2, y - 9, 0.50F,
                ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawHeightBar(GuiGraphics graphics) {
        int x = leftPos + SolarDopingChamberLayout.HEIGHT_BAR_X;
        int y = topPos + SolarDopingChamberLayout.HEIGHT_BAR_Y;
        drawVerticalGauge(
                graphics,
                x,
                y,
                SolarDopingChamberLayout.BAR_WIDTH,
                SolarDopingChamberLayout.BAR_HEIGHT,
                data(SolarDopingChamberBlockEntity.DATA_HEIGHT_PPM),
                1_000_000,
                HEIGHT_COLOR
        );
        drawCenteredText(graphics, font, "Y", x + SolarDopingChamberLayout.BAR_WIDTH / 2, y - 9, 0.50F,
                ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawProbabilityPie(GuiGraphics graphics) {
        int accent = data(SolarDopingChamberBlockEntity.DATA_RECIPE_COLOR);
        int centerX = leftPos + SolarDopingChamberLayout.PIE_CENTER_X;
        int centerY = topPos + SolarDopingChamberLayout.PIE_CENTER_Y;
        int radius = SolarDopingChamberLayout.PIE_RADIUS;
        int innerRadius = SolarDopingChamberLayout.PIE_INNER_RADIUS;
        int activeColor = accent == 0 ? PROBABILITY_COLOR : accent;
        SolarDopingPieChart.draw(
                graphics,
                centerX,
                centerY,
                radius,
                innerRadius,
                activeRecipe().orElse(null),
                activeColor,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 82)
        );

        outline(graphics, centerX - radius - 1, centerY - radius - 1, radius * 2 + 3, radius * 2 + 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 100));
        drawCenteredText(graphics, font, "P", centerX, topPos + SolarDopingChamberLayout.PIE_LABEL_Y, 0.52F,
                ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawStatusStrip(GuiGraphics graphics) {
        int x = leftPos + SolarDopingChamberLayout.STATUS_STRIP_X;
        int y = topPos + SolarDopingChamberLayout.STATUS_STRIP_Y;
        int width = SolarDopingChamberLayout.STATUS_STRIP_WIDTH;
        int height = SolarDopingChamberLayout.STATUS_STRIP_HEIGHT;
        int color = stateColor(data(SolarDopingChamberBlockEntity.DATA_STATE));

        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(color, 120));
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(color, 190));
    }

    private void drawSlots(GuiGraphics graphics) {
        slotFrame(graphics, leftPos + SolarDopingChamberLayout.FILTER_SLOT_X,
                topPos + SolarDopingChamberLayout.FILTER_SLOT_Y,
                HEIGHT_COLOR,
                menu.getSlot(SolarDopingChamberBlockEntity.SLOT_FILTER).hasItem());
        slotFrame(graphics, leftPos + SolarDopingChamberLayout.PROCESS_SLOT_X,
                topPos + SolarDopingChamberLayout.PROCESS_SLOT_Y,
                data(SolarDopingChamberBlockEntity.DATA_RECIPE_COLOR),
                menu.getSlot(SolarDopingChamberBlockEntity.SLOT_PROCESS).hasItem());
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        drawPanel(graphics,
                leftPos + SolarDopingChamberLayout.PLAYER_INVENTORY_PANEL_X,
                topPos + SolarDopingChamberLayout.PLAYER_INVENTORY_PANEL_Y,
                SolarDopingChamberLayout.PLAYER_INVENTORY_PANEL_WIDTH,
                SolarDopingChamberLayout.PLAYER_INVENTORY_PANEL_HEIGHT);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + SolarDopingChamberLayout.PLAYER_INVENTORY_X + column * SolarDopingChamberLayout.SLOT_SIZE,
                        topPos + SolarDopingChamberLayout.PLAYER_INVENTORY_Y + row * SolarDopingChamberLayout.SLOT_SIZE
                );
            }
        }

        int hotbarY = SolarDopingChamberLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(leftPos + SolarDopingChamberLayout.PLAYER_INVENTORY_X, topPos + hotbarY - 4,
                leftPos + SolarDopingChamberLayout.PLAYER_INVENTORY_X + 9 * SolarDopingChamberLayout.SLOT_SIZE,
                topPos + hotbarY - 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88));
        for (int column = 0; column < 9; column++) {
            ceramicSlot(graphics, leftPos + SolarDopingChamberLayout.PLAYER_INVENTORY_X
                    + column * SolarDopingChamberLayout.SLOT_SIZE, topPos + hotbarY);
        }
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
                SolarDopingChamberLayout.ENERGY_BAR_X,
                SolarDopingChamberLayout.ENERGY_BAR_Y,
                SolarDopingChamberLayout.BAR_WIDTH,
                SolarDopingChamberLayout.BAR_HEIGHT)) {
            return List.of(Component.translatable(
                    "screen.spectralization.solar_doping_chamber.tooltip.energy",
                    data(SolarDopingChamberBlockEntity.DATA_ENERGY),
                    data(SolarDopingChamberBlockEntity.DATA_ENERGY_MAX),
                    SolarDopingChamberBlockEntity.ENERGY_PER_TICK * 20
            ));
        }

        if (insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.HEIGHT_BAR_X,
                SolarDopingChamberLayout.HEIGHT_BAR_Y,
                SolarDopingChamberLayout.BAR_WIDTH,
                SolarDopingChamberLayout.BAR_HEIGHT)) {
            return List.of(Component.translatable(
                    "screen.spectralization.solar_doping_chamber.tooltip.height",
                    data(SolarDopingChamberBlockEntity.DATA_HEIGHT_MULTIPLIER_PPM) / 1000.0
            ));
        }

        if (insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.PIE_CENTER_X - SolarDopingChamberLayout.PIE_RADIUS - 2,
                SolarDopingChamberLayout.PIE_CENTER_Y - SolarDopingChamberLayout.PIE_RADIUS - 2,
                SolarDopingChamberLayout.PIE_RADIUS * 2 + 4,
                SolarDopingChamberLayout.PIE_RADIUS * 2 + 4)) {
            List<Component> tooltip = new ArrayList<>();
            activeRecipe().ifPresent(recipe -> {
                if (recipe.hasRandomResults()) {
                    tooltip.add(Component.translatable(
                            "screen.spectralization.solar_doping_chamber.tooltip.output_ratios"
                    ));
                    tooltip.addAll(SolarDopingPieChart.ratioTooltipLines(recipe));
                }
            });
            tooltip.add(Component.translatable(
                    "screen.spectralization.solar_doping_chamber.tooltip.probability",
                    data(SolarDopingChamberBlockEntity.DATA_CHANCE_PPM) / 10000.0
            ));
            tooltip.add(Component.translatable(
                    "screen.spectralization.solar_doping_chamber.tooltip.time",
                    secondsText(data(SolarDopingChamberBlockEntity.DATA_EXPECTED_TICKS))
            ));
            return tooltip;
        }

        if (insideSlot(mouseX, mouseY, SolarDopingChamberLayout.FILTER_SLOT_X, SolarDopingChamberLayout.FILTER_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.solar_doping_chamber.tooltip.filter"));
        }

        if (insideSlot(mouseX, mouseY, SolarDopingChamberLayout.PROCESS_SLOT_X, SolarDopingChamberLayout.PROCESS_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.solar_doping_chamber.tooltip.process"));
        }

        if (insideRecipeClickRegion(mouseX, mouseY)) {
            return List.of(Component.translatable("screen.spectralization.solar_doping_chamber.tooltip.recipe"));
        }

        if (insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.STATUS_STRIP_X,
                SolarDopingChamberLayout.STATUS_STRIP_Y,
                SolarDopingChamberLayout.STATUS_STRIP_WIDTH,
                SolarDopingChamberLayout.STATUS_STRIP_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.solar_doping_chamber.state."
                    + stateId(data(SolarDopingChamberBlockEntity.DATA_STATE))));
        }

        return List.of();
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private Optional<SolarDopingRecipe> activeRecipe() {
        return SolarDopingRecipe.find(
                menu.getSlot(SolarDopingChamberBlockEntity.SLOT_PROCESS).getItem(),
                menu.getSlot(SolarDopingChamberBlockEntity.SLOT_FILTER).getItem()
        );
    }

    private boolean insideSlot(int mouseX, int mouseY, int x, int y) {
        return insideLocal(mouseX, mouseY, x, y, SolarDopingChamberLayout.SLOT_SIZE, SolarDopingChamberLayout.SLOT_SIZE);
    }

    private boolean insideRecipeClickRegion(int mouseX, int mouseY) {
        return insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_X,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_HEIGHT)
                || insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_X,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_HEIGHT)
                || insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_X,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_HEIGHT)
                || insideLocal(mouseX, mouseY,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_X,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_HEIGHT);
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + SolarDopingChamberLayout.MACHINE_HEIGHT,
                ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + SolarDopingChamberLayout.MACHINE_HEIGHT + 1,
                leftPos + imageWidth - 8, topPos + SolarDopingChamberLayout.MACHINE_HEIGHT + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120));
    }

    private static void drawVerticalGauge(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int value,
            int max,
            int color
    ) {
        int fillHeight = max <= 0 ? 0 : Math.round(height * Math.max(0, Math.min(value, max)) / (float) max);
        insetPanel(graphics, x - 1, y - 1, width + 2, height + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 90));
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 82));
        graphics.fill(x, y + height - fillHeight, x + width, y + height,
                ThermalSmelterUiSkin.withAlpha(color, 128 + Math.round(70 * fillHeight / (float) Math.max(1, height))));
        graphics.fill(x + 2, y + height - fillHeight, x + width - 1, y + height,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 46));
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 40));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void subtleRegion(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 32));
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 58));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 54));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 48));
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + SolarDopingChamberLayout.SLOT_SIZE - 2, y + 2,
                    x + SolarDopingChamberLayout.SLOT_SIZE - 1, y + SolarDopingChamberLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + SolarDopingChamberLayout.SLOT_SIZE - 2,
                    x + SolarDopingChamberLayout.SLOT_SIZE - 2, y + SolarDopingChamberLayout.SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, SolarDopingChamberLayout.SLOT_SIZE, SolarDopingChamberLayout.SLOT_SIZE,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SolarDopingChamberLayout.SLOT_SIZE, y + SolarDopingChamberLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, SolarDopingChamberLayout.SLOT_SIZE, SolarDopingChamberLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + SolarDopingChamberLayout.SLOT_SIZE - 1, y + 2,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + SolarDopingChamberLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + SolarDopingChamberLayout.SLOT_SIZE - 2,
                x + SolarDopingChamberLayout.SLOT_SIZE - 1, y + SolarDopingChamberLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + SolarDopingChamberLayout.SLOT_SIZE - 2, y + 1,
                x + SolarDopingChamberLayout.SLOT_SIZE - 1, y + SolarDopingChamberLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + SolarDopingChamberLayout.SLOT_SIZE - 2,
                y + SolarDopingChamberLayout.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
    }

    private static int stateColor(int state) {
        return switch (state) {
            case SolarDopingChamberBlockEntity.STATE_READY -> ThermalSmelterUiSkin.STATUS_READY;
            case SolarDopingChamberBlockEntity.STATE_RUNNING -> ThermalSmelterUiSkin.STATUS_PENDING;
            case SolarDopingChamberBlockEntity.STATE_COMPLETE -> ThermalSmelterUiSkin.OPTICAL;
            case SolarDopingChamberBlockEntity.STATE_NO_POWER,
                    SolarDopingChamberBlockEntity.STATE_INVALID -> ThermalSmelterUiSkin.STATUS_INVALID;
            default -> ThermalSmelterUiSkin.STATUS_EMPTY;
        };
    }

    private static String stateId(int state) {
        return switch (state) {
            case SolarDopingChamberBlockEntity.STATE_READY -> "ready";
            case SolarDopingChamberBlockEntity.STATE_RUNNING -> "running";
            case SolarDopingChamberBlockEntity.STATE_NO_POWER -> "no_power";
            case SolarDopingChamberBlockEntity.STATE_COMPLETE -> "complete";
            case SolarDopingChamberBlockEntity.STATE_INVALID -> "invalid";
            default -> "empty";
        };
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static String secondsText(int ticks) {
        double seconds = Math.max(0, ticks) / 20.0;
        if (seconds >= 10.0) {
            return String.valueOf(Math.round(seconds));
        }

        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static void drawCenteredText(GuiGraphics graphics, Font font, String text, int centerX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(centerX - textWidth * scale / 2.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
