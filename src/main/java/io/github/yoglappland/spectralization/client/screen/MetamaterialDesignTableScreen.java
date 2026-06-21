package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.MetamaterialDesignTableBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.MetamaterialDesignTableLayout;
import io.github.yoglappland.spectralization.menu.MetamaterialDesignTableMenu;
import io.github.yoglappland.spectralization.optics.metasurface.MetamaterialVector;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MetamaterialDesignTableScreen extends SpectralMachineScreen<MetamaterialDesignTableMenu> {
    private static final int AXIS_X_COLOR = 0xFFE35D52;
    private static final int AXIS_Y_COLOR = 0xFFF2B84B;
    private static final int AXIS_Z_COLOR = 0xFF42A5D9;
    private static final int RIGHT_INPUT_COLOR = ThermalSmelterUiSkin.OPTICAL;

    public MetamaterialDesignTableScreen(MetamaterialDesignTableMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "metamaterial_design_table",
                MetamaterialDesignTableLayout.IMAGE_WIDTH,
                MetamaterialDesignTableLayout.IMAGE_HEIGHT,
                MetamaterialDesignTableLayout.INVENTORY_LABEL_X,
                MetamaterialDesignTableLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (insideLocal(mouseX, mouseY,
                    MetamaterialDesignTableLayout.MODE_BUTTON_X,
                    MetamaterialDesignTableLayout.MODE_BUTTON_Y,
                    MetamaterialDesignTableLayout.BUTTON_SIZE,
                    MetamaterialDesignTableLayout.BUTTON_SIZE)) {
                click(MetamaterialDesignTableMenu.BUTTON_TOGGLE_MODE);
                return true;
            }

            if (insideLocal(mouseX, mouseY,
                    MetamaterialDesignTableLayout.STANDARD_PREV_X,
                    MetamaterialDesignTableLayout.STANDARD_BUTTON_Y,
                    MetamaterialDesignTableLayout.BUTTON_SIZE,
                    MetamaterialDesignTableLayout.BUTTON_SIZE)) {
                click(MetamaterialDesignTableMenu.BUTTON_STANDARD_PREV);
                return true;
            }

            if (insideLocal(mouseX, mouseY,
                    MetamaterialDesignTableLayout.STANDARD_NEXT_X,
                    MetamaterialDesignTableLayout.STANDARD_BUTTON_Y,
                    MetamaterialDesignTableLayout.BUTTON_SIZE,
                    MetamaterialDesignTableLayout.BUTTON_SIZE)) {
                click(MetamaterialDesignTableMenu.BUTTON_STANDARD_NEXT);
                return true;
            }

            if (insideLocal(mouseX, mouseY,
                    MetamaterialDesignTableLayout.DESIGN_BUTTON_X,
                    MetamaterialDesignTableLayout.DESIGN_BUTTON_Y,
                    MetamaterialDesignTableLayout.DESIGN_BUTTON_WIDTH,
                    MetamaterialDesignTableLayout.DESIGN_BUTTON_HEIGHT)) {
                if (menu.ready()) {
                    click(MetamaterialDesignTableMenu.BUTTON_DESIGN);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawProcess(graphics);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawProcess(GuiGraphics graphics) {
        drawCeramicPanel(
                graphics,
                leftPos + MetamaterialDesignTableLayout.PROCESS_X,
                topPos + MetamaterialDesignTableLayout.PROCESS_Y,
                MetamaterialDesignTableLayout.PROCESS_WIDTH,
                MetamaterialDesignTableLayout.PROCESS_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + MetamaterialDesignTableLayout.LEFT_PANEL_X,
                topPos + MetamaterialDesignTableLayout.SIDE_PANEL_Y,
                MetamaterialDesignTableLayout.SIDE_PANEL_WIDTH,
                MetamaterialDesignTableLayout.SIDE_PANEL_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + MetamaterialDesignTableLayout.RIGHT_PANEL_X,
                topPos + MetamaterialDesignTableLayout.SIDE_PANEL_Y,
                MetamaterialDesignTableLayout.SIDE_PANEL_WIDTH,
                MetamaterialDesignTableLayout.SIDE_PANEL_HEIGHT
        );
        drawSideInputs(graphics);
        drawMainPanel(graphics);
    }

    private void drawSideInputs(GuiGraphics graphics) {
        drawInputRail(graphics, MetamaterialDesignTableLayout.LEFT_PANEL_CENTER_X, AXIS_X_COLOR, AXIS_Y_COLOR, AXIS_Z_COLOR);
        drawInputRail(graphics, MetamaterialDesignTableLayout.RIGHT_PANEL_CENTER_X, RIGHT_INPUT_COLOR, ThermalSmelterUiSkin.PROGRESS, ThermalSmelterUiSkin.HEAT);

        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_X_BUDGET_X), slotY(MetamaterialDesignTableLayout.SLOT_X_BUDGET_Y), AXIS_X_COLOR, hasItem(MetamaterialDesignTableBlockEntity.SLOT_X_BUDGET));
        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_Y_BUDGET_X), slotY(MetamaterialDesignTableLayout.SLOT_Y_BUDGET_Y), AXIS_Y_COLOR, hasItem(MetamaterialDesignTableBlockEntity.SLOT_Y_BUDGET));
        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_Z_BUDGET_X), slotY(MetamaterialDesignTableLayout.SLOT_Z_BUDGET_Y), AXIS_Z_COLOR, hasItem(MetamaterialDesignTableBlockEntity.SLOT_Z_BUDGET));

        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_RIGHT_TOP_X), slotY(MetamaterialDesignTableLayout.SLOT_RIGHT_TOP_Y), RIGHT_INPUT_COLOR, hasItem(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_TOP));
        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_RIGHT_MIDDLE_X), slotY(MetamaterialDesignTableLayout.SLOT_RIGHT_MIDDLE_Y), ThermalSmelterUiSkin.PROGRESS, hasItem(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_MIDDLE));
        slotFrame(graphics, slotX(MetamaterialDesignTableLayout.SLOT_RIGHT_BOTTOM_X), slotY(MetamaterialDesignTableLayout.SLOT_RIGHT_BOTTOM_Y), ThermalSmelterUiSkin.HEAT, hasItem(MetamaterialDesignTableBlockEntity.SLOT_RIGHT_BOTTOM));
    }

    private void drawInputRail(GuiGraphics graphics, int localCenterX, int topColor, int middleColor, int bottomColor) {
        int centerX = leftPos + localCenterX;
        int top = topPos + MetamaterialDesignTableLayout.SLOT_X_BUDGET_Y + MetamaterialDesignTableLayout.SLOT_SIZE - 1;
        int bottom = topPos + MetamaterialDesignTableLayout.SLOT_Z_BUDGET_Y + 1;
        int middleY = topPos + MetamaterialDesignTableLayout.SLOT_Y_BUDGET_Y + MetamaterialDesignTableLayout.SLOT_SIZE / 2;

        graphics.fill(centerX - 1, top, centerX + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
        graphics.fill(centerX, top, centerX + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
        graphics.fill(centerX - 3, top - 1, centerX + 3, top, ThermalSmelterUiSkin.withAlpha(topColor, 165));
        graphics.fill(centerX - 3, middleY, centerX + 3, middleY + 1, ThermalSmelterUiSkin.withAlpha(middleColor, 150));
        graphics.fill(centerX - 3, bottom, centerX + 3, bottom + 1, ThermalSmelterUiSkin.withAlpha(bottomColor, 150));
    }

    private void drawMainPanel(GuiGraphics graphics) {
        int chamberX = leftPos + MetamaterialDesignTableLayout.CHAMBER_X;
        int chamberY = topPos + MetamaterialDesignTableLayout.CHAMBER_Y;

        drawCeramicPanel(graphics, chamberX, chamberY, MetamaterialDesignTableLayout.CHAMBER_WIDTH, MetamaterialDesignTableLayout.CHAMBER_HEIGHT);
        insetPanel(
                graphics,
                leftPos + MetamaterialDesignTableLayout.CHAMBER_INNER_X,
                topPos + MetamaterialDesignTableLayout.CHAMBER_INNER_Y,
                MetamaterialDesignTableLayout.CHAMBER_INNER_WIDTH,
                MetamaterialDesignTableLayout.CHAMBER_INNER_HEIGHT,
                ThermalSmelterUiSkin.CHAMBER_BG
        );
        drawMainRegions(graphics);
        drawOutputRegion(graphics);
        drawAxisBars(graphics);
    }

    private void drawMainRegions(GuiGraphics graphics) {
        subtleRegion(
                graphics,
                leftPos + MetamaterialDesignTableLayout.OUTPUT_ZONE_X,
                topPos + MetamaterialDesignTableLayout.OUTPUT_ZONE_Y,
                MetamaterialDesignTableLayout.OUTPUT_ZONE_WIDTH,
                MetamaterialDesignTableLayout.OUTPUT_ZONE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 40)
        );
        subtleRegion(
                graphics,
                leftPos + MetamaterialDesignTableLayout.BAR_ZONE_X,
                topPos + MetamaterialDesignTableLayout.BAR_ZONE_Y,
                MetamaterialDesignTableLayout.BAR_ZONE_WIDTH,
                MetamaterialDesignTableLayout.BAR_ZONE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 34)
        );
    }

    private void drawOutputRegion(GuiGraphics graphics) {
        int outputX = slotX(MetamaterialDesignTableLayout.SLOT_OUTPUT_X);
        int outputY = slotY(MetamaterialDesignTableLayout.SLOT_OUTPUT_Y);
        int centerX = outputX + MetamaterialDesignTableLayout.SLOT_SIZE / 2;
        int stateColor = outputBlocked() ? ThermalSmelterUiSkin.STATUS_INVALID
                : menu.ready() ? ThermalSmelterUiSkin.STATUS_READY
                : ThermalSmelterUiSkin.STATUS_EMPTY;

        slotFrame(graphics, outputX, outputY, stateColor, true);
        graphics.fill(centerX - 15, outputY - 4, centerX + 15, outputY - 3, ThermalSmelterUiSkin.withAlpha(stateColor, 115));
        graphics.fill(centerX - 13, outputY + MetamaterialDesignTableLayout.SLOT_SIZE + 3, centerX + 13, outputY + MetamaterialDesignTableLayout.SLOT_SIZE + 4, ThermalSmelterUiSkin.withAlpha(stateColor, 100));

        if (outputBlocked()) {
            blockedOverlay(graphics, outputX, outputY);
        }

        drawModeButton(graphics);
        drawIconButton(graphics, MetamaterialDesignTableLayout.STANDARD_PREV_X, MetamaterialDesignTableLayout.STANDARD_BUTTON_Y, false);
        drawIconButton(graphics, MetamaterialDesignTableLayout.STANDARD_NEXT_X, MetamaterialDesignTableLayout.STANDARD_BUTTON_Y, true);
        drawDesignActuator(graphics, stateColor);
    }

    private void drawModeButton(GuiGraphics graphics) {
        int x = leftPos + MetamaterialDesignTableLayout.MODE_BUTTON_X;
        int y = topPos + MetamaterialDesignTableLayout.MODE_BUTTON_Y;
        int color = menu.customMode() ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.PROGRESS;

        softButtonShell(graphics, x, y, MetamaterialDesignTableLayout.BUTTON_SIZE, MetamaterialDesignTableLayout.BUTTON_SIZE, ThermalSmelterUiSkin.withAlpha(color, 55));
        drawCenteredText(graphics, font, menu.customMode() ? "C" : "S", x + MetamaterialDesignTableLayout.BUTTON_SIZE / 2, y + 3, 0.56F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawDesignActuator(GuiGraphics graphics, int stateColor) {
        int x = leftPos + MetamaterialDesignTableLayout.DESIGN_BUTTON_X;
        int y = topPos + MetamaterialDesignTableLayout.DESIGN_BUTTON_Y;
        int width = MetamaterialDesignTableLayout.DESIGN_BUTTON_WIDTH;
        int height = MetamaterialDesignTableLayout.DESIGN_BUTTON_HEIGHT;
        int color = menu.ready() ? ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PROGRESS, 180) : ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 96);

        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 70));
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(stateColor, 150));
        pixelArrowRight(graphics, x + 3, y + 2, width - 6, height - 4, color);
    }

    private void drawAxisBars(GuiGraphics graphics) {
        drawAxisBar(
                graphics,
                "X",
                MetamaterialDesignTableLayout.AXIS_ROW_X_Y,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_X),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_X),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_X),
                AXIS_X_COLOR
        );
        drawAxisBar(
                graphics,
                "Y",
                MetamaterialDesignTableLayout.AXIS_ROW_Y_Y,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Y),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Y),
                AXIS_Y_COLOR
        );
        drawAxisBar(
                graphics,
                "Z",
                MetamaterialDesignTableLayout.AXIS_ROW_Z_Y,
                data(MetamaterialDesignTableBlockEntity.DATA_MIN_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_MAX_Z),
                data(MetamaterialDesignTableBlockEntity.DATA_TARGET_Z),
                AXIS_Z_COLOR
        );
    }

    private void drawAxisBar(GuiGraphics graphics, String axis, int localY, int min, int max, int target, int color) {
        int labelX = leftPos + MetamaterialDesignTableLayout.AXIS_LABEL_X;
        int barX = leftPos + MetamaterialDesignTableLayout.AXIS_BAR_X;
        int barY = topPos + localY;
        int width = MetamaterialDesignTableLayout.AXIS_BAR_WIDTH;
        int height = MetamaterialDesignTableLayout.AXIS_BAR_HEIGHT;

        drawCenteredText(graphics, font, axis, labelX + 4, barY + 1, 0.50F, color);
        insetPanel(graphics, barX, barY, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 90));
        graphics.fill(barX + 1, barY + 1, barX + width - 1, barY + height - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 70));

        if (min <= max) {
            int rangeLeft = positionFor(min);
            int rangeRight = positionFor(max);
            graphics.fill(barX + rangeLeft, barY + 2, barX + rangeRight + 1, barY + height - 2, ThermalSmelterUiSkin.withAlpha(color, 165));
            graphics.fill(barX + rangeLeft, barY + 1, barX + rangeRight + 1, barY + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 68));
        }

        if (!menu.customMode()) {
            int marker = positionFor(target);
            int markerColor = data(MetamaterialDesignTableBlockEntity.DATA_TARGET_IN_RANGE) != 0 ? 0xFFF8F5EC : ThermalSmelterUiSkin.STATUS_INVALID;
            graphics.fill(barX + marker, barY - 1, barX + marker + 1, barY + height + 1, markerColor);
        }

        drawRightText(graphics, font, min <= max ? min + "/" + max : "--", leftPos + MetamaterialDesignTableLayout.AXIS_VALUE_X + 12, barY + 1, 0.42F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private int positionFor(int value) {
        int clamped = MetamaterialVector.clamp(value);
        double ratio = (clamped - MetamaterialVector.MIN_VALUE) / (double) (MetamaterialVector.VALUE_COUNT - 1);
        return 1 + (int) Math.round((MetamaterialDesignTableLayout.AXIS_BAR_WIDTH - 3) * ratio);
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        int panelX = leftPos + 42;
        int panelY = topPos + 152;
        drawCeramicPanel(graphics, panelX, panelY, 172, 90);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + MetamaterialDesignTableLayout.PLAYER_INVENTORY_X + column * MetamaterialDesignTableLayout.SLOT_SIZE - 1,
                        topPos + MetamaterialDesignTableLayout.PLAYER_INVENTORY_Y + row * MetamaterialDesignTableLayout.SLOT_SIZE - 1
                );
            }
        }

        int hotbarY = MetamaterialDesignTableLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(leftPos + MetamaterialDesignTableLayout.PLAYER_INVENTORY_X - 1, topPos + hotbarY - 5, leftPos + MetamaterialDesignTableLayout.PLAYER_INVENTORY_X + 9 * MetamaterialDesignTableLayout.SLOT_SIZE - 1, topPos + hotbarY - 4, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88));
        for (int column = 0; column < 9; column++) {
            ceramicSlot(
                    graphics,
                    leftPos + MetamaterialDesignTableLayout.PLAYER_INVENTORY_X + column * MetamaterialDesignTableLayout.SLOT_SIZE - 1,
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
                MetamaterialDesignTableLayout.MODE_BUTTON_X,
                MetamaterialDesignTableLayout.MODE_BUTTON_Y,
                MetamaterialDesignTableLayout.BUTTON_SIZE,
                MetamaterialDesignTableLayout.BUTTON_SIZE)) {
            return List.of(Component.translatable(menu.customMode()
                    ? "screen.spectralization.metamaterial_design_table.mode_custom"
                    : "screen.spectralization.metamaterial_design_table.mode_standard"));
        }

        if (insideLocal(mouseX, mouseY,
                MetamaterialDesignTableLayout.STANDARD_PREV_X,
                MetamaterialDesignTableLayout.STANDARD_BUTTON_Y,
                MetamaterialDesignTableLayout.BUTTON_SIZE,
                MetamaterialDesignTableLayout.BUTTON_SIZE)
                || insideLocal(mouseX, mouseY,
                MetamaterialDesignTableLayout.STANDARD_NEXT_X,
                MetamaterialDesignTableLayout.STANDARD_BUTTON_Y,
                MetamaterialDesignTableLayout.BUTTON_SIZE,
                MetamaterialDesignTableLayout.BUTTON_SIZE)) {
            return List.of(Component.translatable(menu.selectedStandard().translationKey()));
        }

        if (insideLocal(mouseX, mouseY,
                MetamaterialDesignTableLayout.DESIGN_BUTTON_X,
                MetamaterialDesignTableLayout.DESIGN_BUTTON_Y,
                MetamaterialDesignTableLayout.DESIGN_BUTTON_WIDTH,
                MetamaterialDesignTableLayout.DESIGN_BUTTON_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.metamaterial_design_table.design"));
        }

        return List.of();
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private boolean hasItem(int slot) {
        return menu.getSlot(slot).hasItem();
    }

    private boolean outputBlocked() {
        return menu.getSlot(MetamaterialDesignTableBlockEntity.SLOT_OUTPUT).hasItem();
    }

    private int slotX(int localX) {
        return leftPos + localX;
    }

    private int slotY(int localY) {
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
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + MetamaterialDesignTableLayout.MACHINE_HEIGHT, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + MetamaterialDesignTableLayout.MACHINE_HEIGHT + 1, leftPos + imageWidth - 8, topPos + MetamaterialDesignTableLayout.MACHINE_HEIGHT + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 120));
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
            graphics.fill(x + MetamaterialDesignTableLayout.SLOT_SIZE - 2, y + 2, x + MetamaterialDesignTableLayout.SLOT_SIZE - 1, y + MetamaterialDesignTableLayout.SLOT_SIZE - 2, ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + MetamaterialDesignTableLayout.SLOT_SIZE - 2, x + MetamaterialDesignTableLayout.SLOT_SIZE - 2, y + MetamaterialDesignTableLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, MetamaterialDesignTableLayout.SLOT_SIZE, MetamaterialDesignTableLayout.SLOT_SIZE, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + MetamaterialDesignTableLayout.SLOT_SIZE, y + MetamaterialDesignTableLayout.SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, MetamaterialDesignTableLayout.SLOT_SIZE, MetamaterialDesignTableLayout.SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + MetamaterialDesignTableLayout.SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + MetamaterialDesignTableLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + MetamaterialDesignTableLayout.SLOT_SIZE - 2, x + MetamaterialDesignTableLayout.SLOT_SIZE - 1, y + MetamaterialDesignTableLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + MetamaterialDesignTableLayout.SLOT_SIZE - 2, y + 1, x + MetamaterialDesignTableLayout.SLOT_SIZE - 1, y + MetamaterialDesignTableLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + MetamaterialDesignTableLayout.SLOT_SIZE - 2, y + MetamaterialDesignTableLayout.SLOT_SIZE - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
    }

    private static void softButtonShell(GuiGraphics graphics, int x, int y, int width, int height, int fill) {
        graphics.fill(x, y, x + width, y + height, fill);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 84));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
    }

    private void drawIconButton(GuiGraphics graphics, int localX, int localY, boolean right) {
        int x = leftPos + localX;
        int y = topPos + localY;
        int color = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 108);
        softButtonShell(graphics, x, y, MetamaterialDesignTableLayout.BUTTON_SIZE, MetamaterialDesignTableLayout.BUTTON_SIZE, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 24));
        if (right) {
            pixelArrowRight(graphics, x + MetamaterialDesignTableLayout.BUTTON_ARROW_INSET, y + MetamaterialDesignTableLayout.BUTTON_ARROW_INSET, MetamaterialDesignTableLayout.BUTTON_ARROW_SIZE, MetamaterialDesignTableLayout.BUTTON_ARROW_SIZE, color);
        } else {
            pixelArrowLeft(graphics, x + MetamaterialDesignTableLayout.BUTTON_ARROW_INSET, y + MetamaterialDesignTableLayout.BUTTON_ARROW_INSET, MetamaterialDesignTableLayout.BUTTON_ARROW_SIZE, MetamaterialDesignTableLayout.BUTTON_ARROW_SIZE, color);
        }
    }

    private static void blockedOverlay(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + MetamaterialDesignTableLayout.SLOT_SIZE, y + MetamaterialDesignTableLayout.SLOT_SIZE, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STATUS_INVALID, 72));
        for (int i = 3; i < MetamaterialDesignTableLayout.SLOT_SIZE - 3; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
            graphics.fill(x + MetamaterialDesignTableLayout.SLOT_SIZE - 1 - i, y + i, x + MetamaterialDesignTableLayout.SLOT_SIZE - i, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
        }
    }

    private static void pixelArrowRight(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        pixelArrow(graphics, x, y, width, height, color, true);
    }

    private static void pixelArrowLeft(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        pixelArrow(graphics, x, y, width, height, color, false);
    }

    private static void pixelArrow(GuiGraphics graphics, int x, int y, int width, int height, int color, boolean right) {
        int centerY = y + height / 2;
        int maxHalfHeight = Math.max(1, height / 2);
        int shaftHeight = Math.max(1, height / 3);

        if (shaftHeight % 2 == 0) {
            shaftHeight++;
        }

        int shaftTop = centerY - shaftHeight / 2;
        int shaftBottom = shaftTop + shaftHeight;
        int headWidth = Math.min(width, Math.min(9, Math.max(4, width / 5)));
        int shaftStart = right ? 0 : headWidth;
        int shaftEnd = right ? width - headWidth : width;

        if (shaftStart < shaftEnd) {
            graphics.fill(x + shaftStart, shaftTop, x + shaftEnd, shaftBottom, color);
        }

        for (int column = 0; column < headWidth; column++) {
            int drawX = right ? x + width - headWidth + column : x + column;
            int distanceToTip = right ? headWidth - 1 - column : column;
            int halfHeight = Math.round(distanceToTip * maxHalfHeight / (float) Math.max(1, headWidth - 1));
            graphics.fill(drawX, centerY - halfHeight, drawX + 1, centerY + halfHeight + 1, color);
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

    private static void drawRightText(GuiGraphics graphics, Font font, String text, int rightX, int y, float scale, int color) {
        int textWidth = font.width(text);
        int x = Math.round(rightX - textWidth * scale);
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
