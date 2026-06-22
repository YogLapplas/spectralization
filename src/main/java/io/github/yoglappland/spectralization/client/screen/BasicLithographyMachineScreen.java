package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineLayout;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class BasicLithographyMachineScreen extends SpectralMachineScreen<BasicLithographyMachineMenu> {
    private static final int ENERGY_COLOR = 0xFF68A7FF;
    private static final int OPTICAL_COLOR = 0xFFFFB24A;
    private static final int TEMPLATE_COLOR = 0xFF9FE7DF;
    private static final int MATERIAL_COLOR = ThermalSmelterUiSkin.PROGRESS;

    public BasicLithographyMachineScreen(BasicLithographyMachineMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "basic_lithography_machine",
                BasicLithographyMachineLayout.IMAGE_WIDTH,
                BasicLithographyMachineLayout.IMAGE_HEIGHT,
                BasicLithographyMachineLayout.INVENTORY_LABEL_X,
                BasicLithographyMachineLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideLocal(mouseX, mouseY,
                BasicLithographyMachineLayout.RULE_BUTTON_X,
                BasicLithographyMachineLayout.RULE_BUTTON_Y,
                BasicLithographyMachineLayout.RULE_BUTTON_WIDTH,
                BasicLithographyMachineLayout.RULE_BUTTON_HEIGHT)) {
            click(BasicLithographyMachineMenu.BUTTON_TOGGLE_TEMPLATE_RULE);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawProcess(graphics, mouseX, mouseY);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawProcess(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCeramicPanel(
                graphics,
                leftPos + BasicLithographyMachineLayout.PROCESS_X,
                topPos + BasicLithographyMachineLayout.PROCESS_Y,
                BasicLithographyMachineLayout.PROCESS_WIDTH,
                BasicLithographyMachineLayout.PROCESS_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + BasicLithographyMachineLayout.LEFT_PANEL_X,
                topPos + BasicLithographyMachineLayout.SIDE_PANEL_Y,
                BasicLithographyMachineLayout.SIDE_PANEL_WIDTH,
                BasicLithographyMachineLayout.SIDE_PANEL_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + BasicLithographyMachineLayout.RIGHT_PANEL_X,
                topPos + BasicLithographyMachineLayout.SIDE_PANEL_Y,
                BasicLithographyMachineLayout.SIDE_PANEL_WIDTH,
                BasicLithographyMachineLayout.SIDE_PANEL_HEIGHT
        );
        drawSideSlots(graphics);
        drawMainPanel(graphics, mouseX, mouseY);
    }

    private void drawSideSlots(GuiGraphics graphics) {
        drawSideRail(graphics, BasicLithographyMachineLayout.LEFT_PANEL_X, MATERIAL_COLOR);
        drawSideRail(graphics, BasicLithographyMachineLayout.RIGHT_PANEL_X, OPTICAL_COLOR);
        drawCenteredText(graphics, font, "IN",
                leftPos + BasicLithographyMachineLayout.LEFT_PANEL_X + BasicLithographyMachineLayout.SIDE_PANEL_WIDTH / 2,
                topPos + BasicLithographyMachineLayout.SIDE_PANEL_Y + 14,
                0.55F,
                ThermalSmelterUiSkin.TEXT_SUB);
        drawCenteredText(graphics, font, "OUT",
                leftPos + BasicLithographyMachineLayout.RIGHT_PANEL_X + BasicLithographyMachineLayout.SIDE_PANEL_WIDTH / 2,
                topPos + BasicLithographyMachineLayout.SIDE_PANEL_Y + 14,
                0.55F,
                ThermalSmelterUiSkin.TEXT_SUB);

        slotFrame(graphics, localX(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_0_X), localY(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_TOP_Y),
                MATERIAL_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_0));
        slotFrame(graphics, localX(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_1_X), localY(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_TOP_Y),
                MATERIAL_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_1));
        slotFrame(graphics, localX(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_0_X), localY(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_BOTTOM_Y),
                MATERIAL_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_2));
        slotFrame(graphics, localX(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_1_X), localY(BasicLithographyMachineLayout.LEFT_ITEM_INPUT_BOTTOM_Y),
                MATERIAL_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_ITEM_INPUT_3));

        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_0,
                BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_0_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_TOP_Y);
        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_1,
                BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_1_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_TOP_Y);
        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_2,
                BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_0_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_BOTTOM_Y);
        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_ITEM_OUTPUT_3,
                BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_1_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_BOTTOM_Y);
    }

    private void drawMainPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawCeramicPanel(
                graphics,
                leftPos + BasicLithographyMachineLayout.CHAMBER_X,
                topPos + BasicLithographyMachineLayout.CHAMBER_Y,
                BasicLithographyMachineLayout.CHAMBER_WIDTH,
                BasicLithographyMachineLayout.CHAMBER_HEIGHT
        );
        insetPanel(
                graphics,
                leftPos + BasicLithographyMachineLayout.CHAMBER_INNER_X,
                topPos + BasicLithographyMachineLayout.CHAMBER_INNER_Y,
                BasicLithographyMachineLayout.CHAMBER_INNER_WIDTH,
                BasicLithographyMachineLayout.CHAMBER_INNER_HEIGHT,
                ThermalSmelterUiSkin.CHAMBER_BG
        );
        subtleRegion(
                graphics,
                leftPos + BasicLithographyMachineLayout.MAIN_PANEL_X,
                topPos + BasicLithographyMachineLayout.MAIN_PANEL_Y,
                BasicLithographyMachineLayout.MAIN_PANEL_WIDTH,
                BasicLithographyMachineLayout.MAIN_PANEL_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 34)
        );

        drawMainDividers(graphics);
        drawCenterProgressArea(graphics);
        drawEnergyBar(graphics);
        drawOpticalBar(graphics);
        drawRecipeArrow(graphics, mouseX, mouseY);
        drawProgressBar(graphics);
        drawRuleButton(graphics);
        drawTemplateSlots(graphics);
    }

    private void drawTemplateSlots(GuiGraphics graphics) {
        slotFrame(graphics, localX(BasicLithographyMachineLayout.TEMPLATE_LEFT_X), localY(BasicLithographyMachineLayout.TEMPLATE_INPUT_Y),
                TEMPLATE_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_A));
        slotFrame(graphics, localX(BasicLithographyMachineLayout.TEMPLATE_RIGHT_X), localY(BasicLithographyMachineLayout.TEMPLATE_INPUT_Y),
                TEMPLATE_COLOR, hasItem(BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_INPUT_B));
        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_OUTPUT_A,
                BasicLithographyMachineLayout.TEMPLATE_LEFT_X, BasicLithographyMachineLayout.TEMPLATE_OUTPUT_Y);
        outputSlotFrame(graphics, BasicLithographyMachineBlockEntity.SLOT_TEMPLATE_OUTPUT_B,
                BasicLithographyMachineLayout.TEMPLATE_RIGHT_X, BasicLithographyMachineLayout.TEMPLATE_OUTPUT_Y);

        int centerX = leftPos + BasicLithographyMachineLayout.IMAGE_WIDTH / 2;
        drawCenteredText(graphics, font, "MASK", centerX, topPos + BasicLithographyMachineLayout.TEMPLATE_INPUT_Y - 8,
                0.46F, ThermalSmelterUiSkin.TEXT_SUB);
        graphics.fill(leftPos + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_X,
                topPos + BasicLithographyMachineLayout.TEMPLATE_INPUT_LINE_Y,
                leftPos + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_X + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_WIDTH,
                topPos + BasicLithographyMachineLayout.TEMPLATE_INPUT_LINE_Y + 1,
                ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 90));
        graphics.fill(leftPos + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_X,
                topPos + BasicLithographyMachineLayout.TEMPLATE_OUTPUT_LINE_Y,
                leftPos + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_X + BasicLithographyMachineLayout.TEMPLATE_CONNECTOR_WIDTH,
                topPos + BasicLithographyMachineLayout.TEMPLATE_OUTPUT_LINE_Y + 1,
                ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 76));
    }

    private void drawMainDividers(GuiGraphics graphics) {
        int top = topPos + BasicLithographyMachineLayout.MAIN_PANEL_Y + 8;
        int bottom = topPos + BasicLithographyMachineLayout.MAIN_PANEL_Y + BasicLithographyMachineLayout.MAIN_PANEL_HEIGHT - 8;
        int leftDivider = leftPos + BasicLithographyMachineLayout.MAIN_LEFT_DIVIDER_X;
        int rightDivider = leftPos + BasicLithographyMachineLayout.MAIN_RIGHT_DIVIDER_X;
        int color = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120);
        int highlight = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 62);

        graphics.fill(leftDivider, top, leftDivider + 1, bottom, color);
        graphics.fill(leftDivider + 1, top, leftDivider + 2, bottom, highlight);
        graphics.fill(rightDivider, top, rightDivider + 1, bottom, color);
        graphics.fill(rightDivider + 1, top, rightDivider + 2, bottom, highlight);
    }

    private void drawCenterProgressArea(GuiGraphics graphics) {
        int left = leftPos + BasicLithographyMachineLayout.MAIN_LEFT_DIVIDER_X + 4;
        int right = leftPos + BasicLithographyMachineLayout.MAIN_RIGHT_DIVIDER_X - 4;
        int y = topPos + BasicLithographyMachineLayout.CENTER_PROGRESS_AREA_Y;

        graphics.fill(left, y, right, y + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
        graphics.fill(left, y + 1, right, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 38));
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        int x = leftPos + BasicLithographyMachineLayout.ENERGY_BAR_X;
        int y = topPos + BasicLithographyMachineLayout.ENERGY_BAR_Y;
        drawVerticalGauge(
                graphics,
                x,
                y,
                BasicLithographyMachineLayout.BAR_WIDTH,
                BasicLithographyMachineLayout.BAR_HEIGHT,
                data(BasicLithographyMachineBlockEntity.DATA_ENERGY),
                data(BasicLithographyMachineBlockEntity.DATA_ENERGY_MAX),
                ENERGY_COLOR
        );
        drawCenteredText(graphics, font, "E", x + BasicLithographyMachineLayout.BAR_WIDTH / 2, y - 8, 0.42F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawOpticalBar(GuiGraphics graphics) {
        int x = leftPos + BasicLithographyMachineLayout.OPTICAL_BAR_X;
        int y = topPos + BasicLithographyMachineLayout.OPTICAL_BAR_Y;
        drawVerticalGauge(
                graphics,
                x,
                y,
                BasicLithographyMachineLayout.BAR_WIDTH,
                BasicLithographyMachineLayout.BAR_HEIGHT,
                data(BasicLithographyMachineBlockEntity.DATA_OPTICAL_POWER),
                data(BasicLithographyMachineBlockEntity.DATA_OPTICAL_POWER_MAX),
                OPTICAL_COLOR
        );
        drawCenteredText(graphics, font, "SP", x + BasicLithographyMachineLayout.BAR_WIDTH / 2, y - 8, 0.40F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawRecipeArrow(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = leftPos + BasicLithographyMachineLayout.RECIPE_ARROW_X;
        int y = topPos + BasicLithographyMachineLayout.RECIPE_ARROW_Y;
        int color = insideLocal(mouseX, mouseY,
                BasicLithographyMachineLayout.RECIPE_ARROW_X,
                BasicLithographyMachineLayout.RECIPE_ARROW_Y,
                BasicLithographyMachineLayout.RECIPE_ARROW_WIDTH,
                BasicLithographyMachineLayout.RECIPE_ARROW_HEIGHT)
                ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PROGRESS, 210)
                : menu.outputBlocked()
                ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STATUS_INVALID, 180)
                : menu.ready()
                ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PROGRESS, 175)
                : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 175);

        pixelArrowRight(graphics, x, y, BasicLithographyMachineLayout.RECIPE_ARROW_WIDTH, BasicLithographyMachineLayout.RECIPE_ARROW_HEIGHT, color);
    }

    private void drawProgressBar(GuiGraphics graphics) {
        int x = leftPos + BasicLithographyMachineLayout.PROGRESS_BAR_X;
        int y = topPos + BasicLithographyMachineLayout.PROGRESS_BAR_Y;
        int progress = data(BasicLithographyMachineBlockEntity.DATA_PROGRESS);
        int required = Math.max(1, data(BasicLithographyMachineBlockEntity.DATA_PROGRESS_REQUIRED));
        int lit = Math.round(BasicLithographyMachineLayout.PROGRESS_SEGMENTS * progress / (float) required);
        int gap = 2;
        int segmentWidth = (BasicLithographyMachineLayout.PROGRESS_BAR_WIDTH
                - gap * (BasicLithographyMachineLayout.PROGRESS_SEGMENTS - 1)) / BasicLithographyMachineLayout.PROGRESS_SEGMENTS;

        graphics.fill(x - 2, y - 2,
                x + BasicLithographyMachineLayout.PROGRESS_BAR_WIDTH + 2,
                y + BasicLithographyMachineLayout.PROGRESS_BAR_HEIGHT + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 76));
        outline(graphics, x - 2, y - 2,
                BasicLithographyMachineLayout.PROGRESS_BAR_WIDTH + 4,
                BasicLithographyMachineLayout.PROGRESS_BAR_HEIGHT + 4,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 96));

        for (int segment = 0; segment < BasicLithographyMachineLayout.PROGRESS_SEGMENTS; segment++) {
            int color = segment < lit
                    ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PROGRESS, 160)
                    : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 86);
            int segmentX = x + segment * (segmentWidth + gap);
            graphics.fill(segmentX, y, segmentX + segmentWidth, y + BasicLithographyMachineLayout.PROGRESS_BAR_HEIGHT, color);
            graphics.fill(segmentX, y, segmentX + segmentWidth, y + 1,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, segment < lit ? 74 : 30));
        }
    }

    private void drawRuleButton(GuiGraphics graphics) {
        int x = leftPos + BasicLithographyMachineLayout.RULE_BUTTON_X;
        int y = topPos + BasicLithographyMachineLayout.RULE_BUTTON_Y;
        int color = menu.moveUsedTemplates() ? ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 190)
                : ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 118);
        int fill = menu.moveUsedTemplates() ? ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 52)
                : ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, 28);

        softButtonShell(
                graphics,
                x,
                y,
                BasicLithographyMachineLayout.RULE_BUTTON_WIDTH,
                BasicLithographyMachineLayout.RULE_BUTTON_HEIGHT,
                fill
        );
        outline(graphics, x, y, BasicLithographyMachineLayout.RULE_BUTTON_WIDTH, BasicLithographyMachineLayout.RULE_BUTTON_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(TEMPLATE_COLOR, menu.moveUsedTemplates() ? 142 : 90));
        graphics.fill(x + 5, y + 4, x + 8, y + 7, color);
        graphics.fill(x + 10, y + 4, x + 13, y + 7, color);

        if (menu.moveUsedTemplates()) {
            pixelArrowDown(graphics, x + 7, y + 8, 5, 5, color);
        } else {
            graphics.fill(x + 5, y + 12, x + 13, y + 13, color);
        }
    }

    private void drawVerticalGauge(GuiGraphics graphics, int x, int y, int width, int height, int value, int max, int color) {
        int clampedMax = Math.max(1, max);
        int fillHeight = Math.round((height - 4) * Math.min(clampedMax, Math.max(0, value)) / (float) clampedMax);

        insetPanel(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 100));
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 90));

        if (fillHeight > 0) {
            int fillTop = y + height - 2 - fillHeight;
            graphics.fill(x + 2, fillTop, x + width - 2, y + height - 2, ThermalSmelterUiSkin.withAlpha(color, 170));
            graphics.fill(x + 3, fillTop, x + width - 3, fillTop + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 82));
        }
    }

    private void drawSideRail(GuiGraphics graphics, int localX, int color) {
        int centerX = leftPos + localX + BasicLithographyMachineLayout.SIDE_PANEL_WIDTH / 2;
        int top = topPos + BasicLithographyMachineLayout.LEFT_ITEM_INPUT_TOP_Y - 7;
        int bottom = topPos + BasicLithographyMachineLayout.LEFT_ITEM_INPUT_BOTTOM_Y + BasicLithographyMachineLayout.SLOT_SIZE + 7;

        graphics.fill(centerX - 17, top, centerX + 17, top + 1, ThermalSmelterUiSkin.withAlpha(color, 78));
        graphics.fill(centerX - 17, bottom, centerX + 17, bottom + 1, ThermalSmelterUiSkin.withAlpha(color, 58));
    }

    private void outputSlotFrame(GuiGraphics graphics, int slot, int localX, int localY) {
        int x = localX(localX);
        int y = localY(localY);
        boolean hasItem = hasItem(slot);
        slotFrame(graphics, x, y, OPTICAL_COLOR, hasItem);

        if (hasItem && menu.outputBlocked()) {
            blockedOverlay(graphics, x, y);
        }
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        int panelX = leftPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_X - 5;
        int panelY = topPos + 152;
        drawCeramicPanel(graphics, panelX, panelY, 172, 90);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_X + column * BasicLithographyMachineLayout.SLOT_SIZE - 1,
                        topPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_Y + row * BasicLithographyMachineLayout.SLOT_SIZE - 1
                );
            }
        }

        int hotbarY = BasicLithographyMachineLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(leftPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_X - 1, topPos + hotbarY - 5,
                leftPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_X + 9 * BasicLithographyMachineLayout.SLOT_SIZE - 1, topPos + hotbarY - 4,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88));

        for (int column = 0; column < 9; column++) {
            ceramicSlot(
                    graphics,
                    leftPos + BasicLithographyMachineLayout.PLAYER_INVENTORY_X + column * BasicLithographyMachineLayout.SLOT_SIZE - 1,
                    topPos + hotbarY - 1
            );
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
                BasicLithographyMachineLayout.RULE_BUTTON_X,
                BasicLithographyMachineLayout.RULE_BUTTON_Y,
                BasicLithographyMachineLayout.RULE_BUTTON_WIDTH,
                BasicLithographyMachineLayout.RULE_BUTTON_HEIGHT)) {
            return List.of(Component.translatable(menu.moveUsedTemplates()
                    ? "screen.spectralization.basic_lithography_machine.tooltip.rule_move"
                    : "screen.spectralization.basic_lithography_machine.tooltip.rule_keep"));
        }

        if (insideLocal(mouseX, mouseY,
                BasicLithographyMachineLayout.RECIPE_ARROW_X,
                BasicLithographyMachineLayout.RECIPE_ARROW_Y,
                BasicLithographyMachineLayout.RECIPE_ARROW_WIDTH,
                BasicLithographyMachineLayout.RECIPE_ARROW_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.basic_lithography_machine.tooltip.recipe"));
        }

        if (insideLocal(mouseX, mouseY,
                BasicLithographyMachineLayout.ENERGY_BAR_X,
                BasicLithographyMachineLayout.ENERGY_BAR_Y,
                BasicLithographyMachineLayout.BAR_WIDTH,
                BasicLithographyMachineLayout.BAR_HEIGHT)) {
            return List.of(Component.translatable(
                    "screen.spectralization.basic_lithography_machine.tooltip.energy",
                    data(BasicLithographyMachineBlockEntity.DATA_ENERGY),
                    data(BasicLithographyMachineBlockEntity.DATA_ENERGY_MAX)
            ));
        }

        if (insideLocal(mouseX, mouseY,
                BasicLithographyMachineLayout.OPTICAL_BAR_X,
                BasicLithographyMachineLayout.OPTICAL_BAR_Y,
                BasicLithographyMachineLayout.BAR_WIDTH,
                BasicLithographyMachineLayout.BAR_HEIGHT)) {
            return List.of(Component.translatable(
                    "screen.spectralization.basic_lithography_machine.tooltip.optical_power",
                    data(BasicLithographyMachineBlockEntity.DATA_OPTICAL_POWER),
                    data(BasicLithographyMachineBlockEntity.DATA_OPTICAL_POWER_MAX)
            ));
        }

        if (insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.TEMPLATE_LEFT_X, BasicLithographyMachineLayout.TEMPLATE_INPUT_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.TEMPLATE_RIGHT_X, BasicLithographyMachineLayout.TEMPLATE_INPUT_Y)) {
            return List.of(Component.translatable("screen.spectralization.basic_lithography_machine.tooltip.template_input"));
        }

        if (insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.TEMPLATE_LEFT_X, BasicLithographyMachineLayout.TEMPLATE_OUTPUT_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.TEMPLATE_RIGHT_X, BasicLithographyMachineLayout.TEMPLATE_OUTPUT_Y)) {
            return List.of(Component.translatable("screen.spectralization.basic_lithography_machine.tooltip.template_output"));
        }

        if (insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_0_X, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_TOP_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_1_X, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_TOP_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_0_X, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_BOTTOM_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_1_X, BasicLithographyMachineLayout.LEFT_ITEM_INPUT_BOTTOM_Y)) {
            return List.of(Component.translatable("screen.spectralization.basic_lithography_machine.tooltip.item_input"));
        }

        if (insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_0_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_TOP_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_1_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_TOP_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_0_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_BOTTOM_Y)
                || insideSlot(mouseX, mouseY, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_1_X, BasicLithographyMachineLayout.RIGHT_ITEM_OUTPUT_BOTTOM_Y)) {
            return List.of(Component.translatable("screen.spectralization.basic_lithography_machine.tooltip.item_output"));
        }

        return List.of();
    }

    private boolean insideSlot(int mouseX, int mouseY, int x, int y) {
        return insideLocal(mouseX, mouseY, x, y, BasicLithographyMachineLayout.SLOT_SIZE, BasicLithographyMachineLayout.SLOT_SIZE);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private boolean hasItem(int slot) {
        return menu.getSlot(slot).hasItem();
    }

    private int localX(int localX) {
        return leftPos + localX;
    }

    private int localY(int localY) {
        return topPos + localY;
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + BasicLithographyMachineLayout.MACHINE_HEIGHT, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + BasicLithographyMachineLayout.MACHINE_HEIGHT + 1,
                leftPos + imageWidth - 8, topPos + BasicLithographyMachineLayout.MACHINE_HEIGHT + 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120));
    }

    private static void drawCeramicPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.PANEL);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 3, y + height - 4, x + width - 3, y + height - 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 40));
    }

    private static void insetPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
    }

    private static void subtleRegion(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 56));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 54));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 46));
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + BasicLithographyMachineLayout.SLOT_SIZE - 2, y + 2,
                    x + BasicLithographyMachineLayout.SLOT_SIZE - 1, y + BasicLithographyMachineLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + BasicLithographyMachineLayout.SLOT_SIZE - 2,
                    x + BasicLithographyMachineLayout.SLOT_SIZE - 2, y + BasicLithographyMachineLayout.SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, BasicLithographyMachineLayout.SLOT_SIZE, BasicLithographyMachineLayout.SLOT_SIZE,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + BasicLithographyMachineLayout.SLOT_SIZE, y + BasicLithographyMachineLayout.SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, BasicLithographyMachineLayout.SLOT_SIZE, BasicLithographyMachineLayout.SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + BasicLithographyMachineLayout.SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + BasicLithographyMachineLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + BasicLithographyMachineLayout.SLOT_SIZE - 2,
                x + BasicLithographyMachineLayout.SLOT_SIZE - 1, y + BasicLithographyMachineLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + BasicLithographyMachineLayout.SLOT_SIZE - 2, y + 1,
                x + BasicLithographyMachineLayout.SLOT_SIZE - 1, y + BasicLithographyMachineLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + BasicLithographyMachineLayout.SLOT_SIZE - 2,
                y + BasicLithographyMachineLayout.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
    }

    private static void softButtonShell(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 84));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
    }

    private static void blockedOverlay(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + BasicLithographyMachineLayout.SLOT_SIZE, y + BasicLithographyMachineLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STATUS_INVALID, 72));
        for (int i = 3; i < BasicLithographyMachineLayout.SLOT_SIZE - 3; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
            graphics.fill(x + BasicLithographyMachineLayout.SLOT_SIZE - 1 - i, y + i,
                    x + BasicLithographyMachineLayout.SLOT_SIZE - i, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
        }
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
        int headWidth = Math.min(width, Math.min(11, Math.max(5, width / 5)));
        int shaftEnd = width - headWidth;

        if (shaftEnd > 0) {
            graphics.fill(x, shaftTop, x + shaftEnd, shaftBottom, color);
        }

        for (int column = 0; column < headWidth; column++) {
            int drawX = x + width - headWidth + column;
            int distanceToTip = headWidth - 1 - column;
            int halfHeight = Math.round(distanceToTip * maxHalfHeight / (float) Math.max(1, headWidth - 1));
            graphics.fill(drawX, centerY - halfHeight, drawX + 1, centerY + halfHeight + 1, color);
        }
    }

    private static void pixelArrowDown(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int centerX = x + width / 2;
        int shaftWidth = Math.max(1, width / 3);
        int shaftLeft = centerX - shaftWidth / 2;
        int headHeight = Math.min(height, 3);

        graphics.fill(shaftLeft, y, shaftLeft + shaftWidth, y + height - headHeight, color);
        for (int row = 0; row < headHeight; row++) {
            int halfWidth = headHeight - row - 1;
            int drawY = y + height - headHeight + row;
            graphics.fill(centerX - halfWidth, drawY, centerX + halfWidth + 1, drawY + 1, color);
        }
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
