package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.FiberDrawingMachineBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.FiberDrawingParameters;
import io.github.yoglappland.spectralization.menu.FiberDrawingMachineLayout;
import io.github.yoglappland.spectralization.menu.FiberDrawingMachineMenu;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FiberDrawingMachineScreen extends SpectralMachineScreen<FiberDrawingMachineMenu> {
    private static final int ENERGY_COLOR = 0xFF68A7FF;
    private static final int DRAW_COLOR = 0xFF9FE7DF;
    private static final int SINGLE_MODE_COLOR = 0xFF9EA8FF;
    private static final int PROPERTY_COLORS[] = {
            0xFFFFB35C,
            0xFFFF7A9A,
            0xFF9FE7DF
    };

    public FiberDrawingMachineScreen(FiberDrawingMachineMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "fiber_drawing_machine",
                FiberDrawingMachineLayout.IMAGE_WIDTH,
                FiberDrawingMachineLayout.IMAGE_HEIGHT,
                FiberDrawingMachineLayout.INVENTORY_LABEL_X,
                FiberDrawingMachineLayout.INVENTORY_LABEL_Y
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
                FiberDrawingMachineLayout.RULE_BUTTON_X,
                FiberDrawingMachineLayout.RULE_BUTTON_Y,
                FiberDrawingMachineLayout.RULE_BUTTON_WIDTH,
                FiberDrawingMachineLayout.RULE_BUTTON_HEIGHT)) {
            click(FiberDrawingMachineMenu.BUTTON_TOGGLE_MOLD_RULE);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        drawScreenShell(graphics);
        drawMachinePanels(graphics, partialTick);
        drawPlayerInventory(graphics);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawMachinePanels(GuiGraphics graphics, float partialTick) {
        drawPanel(graphics, leftPos + FiberDrawingMachineLayout.MACHINE_X, topPos + FiberDrawingMachineLayout.MACHINE_Y,
                FiberDrawingMachineLayout.MACHINE_WIDTH, FiberDrawingMachineLayout.MACHINE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + FiberDrawingMachineLayout.LEFT_PANEL_X, topPos + FiberDrawingMachineLayout.SIDE_PANEL_Y,
                FiberDrawingMachineLayout.SIDE_PANEL_WIDTH, FiberDrawingMachineLayout.SIDE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + FiberDrawingMachineLayout.RIGHT_PANEL_X, topPos + FiberDrawingMachineLayout.SIDE_PANEL_Y,
                FiberDrawingMachineLayout.SIDE_PANEL_WIDTH, FiberDrawingMachineLayout.SIDE_PANEL_HEIGHT);
        drawPanel(graphics, leftPos + FiberDrawingMachineLayout.MAIN_PANEL_X, topPos + FiberDrawingMachineLayout.MAIN_PANEL_Y,
                FiberDrawingMachineLayout.MAIN_PANEL_WIDTH, FiberDrawingMachineLayout.MAIN_PANEL_HEIGHT);
        insetPanel(graphics, leftPos + FiberDrawingMachineLayout.MAIN_INNER_X,
                topPos + FiberDrawingMachineLayout.MAIN_INNER_Y,
                FiberDrawingMachineLayout.MAIN_INNER_WIDTH,
                FiberDrawingMachineLayout.MAIN_INNER_HEIGHT,
                ThermalSmelterUiSkin.CHAMBER_BG);
        subtleRegion(graphics, leftPos + FiberDrawingMachineLayout.ENERGY_REGION_X,
                topPos + FiberDrawingMachineLayout.ENERGY_REGION_Y,
                FiberDrawingMachineLayout.ENERGY_REGION_WIDTH,
                FiberDrawingMachineLayout.ENERGY_REGION_HEIGHT);
        subtleRegion(graphics, leftPos + FiberDrawingMachineLayout.MOLD_REGION_X,
                topPos + FiberDrawingMachineLayout.MOLD_REGION_Y,
                FiberDrawingMachineLayout.MOLD_REGION_WIDTH,
                FiberDrawingMachineLayout.MOLD_REGION_HEIGHT);
        subtleRegion(graphics, leftPos + FiberDrawingMachineLayout.PROPERTY_REGION_X,
                topPos + FiberDrawingMachineLayout.PROPERTY_REGION_Y,
                FiberDrawingMachineLayout.PROPERTY_REGION_WIDTH,
                FiberDrawingMachineLayout.PROPERTY_REGION_HEIGHT);

        drawMainDivider(graphics);
        drawEnergyBar(graphics);
        drawDrawingPreview(graphics, partialTick);
        drawPropertyBars(graphics);
        drawMoldRuleButton(graphics);
        drawSlots(graphics);
    }

    private void drawMainDivider(GuiGraphics graphics) {
        int top = topPos + FiberDrawingMachineLayout.MAIN_INNER_Y + 6;
        int bottom = topPos + FiberDrawingMachineLayout.MAIN_INNER_Y + FiberDrawingMachineLayout.MAIN_INNER_HEIGHT - 6;

        drawDivider(graphics, leftPos + FiberDrawingMachineLayout.ENERGY_DIVIDER_X, top, bottom);
    }

    private void drawDivider(GuiGraphics graphics, int x, int top, int bottom) {
        graphics.fill(x, top, x + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 132));
        graphics.fill(x + 1, top, x + 2, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 52));
    }

    private void drawEnergyBar(GuiGraphics graphics) {
        int x = leftPos + FiberDrawingMachineLayout.ENERGY_BAR_X;
        int y = topPos + FiberDrawingMachineLayout.ENERGY_BAR_Y;
        drawVerticalGauge(
                graphics,
                x,
                y,
                FiberDrawingMachineLayout.ENERGY_BAR_WIDTH,
                FiberDrawingMachineLayout.ENERGY_BAR_HEIGHT,
                data(FiberDrawingMachineBlockEntity.DATA_ENERGY),
                data(FiberDrawingMachineBlockEntity.DATA_ENERGY_MAX),
                ENERGY_COLOR
        );
        drawCenteredText(graphics, font, "E", x + FiberDrawingMachineLayout.ENERGY_BAR_WIDTH / 2, y - 9, 0.50F,
                ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawDrawingPreview(GuiGraphics graphics, float partialTick) {
        int x = leftPos + FiberDrawingMachineLayout.DRAW_LINE_X;
        int y = topPos + FiberDrawingMachineLayout.DRAW_LINE_Y;
        int width = FiberDrawingMachineLayout.DRAW_LINE_WIDTH;
        int height = FiberDrawingMachineLayout.DRAW_LINE_HEIGHT;
        int centerY = y + height / 2;
        int accent = materialAccent();
        int outputAccent = data(FiberDrawingMachineBlockEntity.DATA_MOLD_KIND) == 2 ? SINGLE_MODE_COLOR : DRAW_COLOR;
        boolean active = isCrafting();
        int leftStart = x + 3;
        int rightEnd = x + width - 3;
        int dieCenter = x + width / 2;
        int taperEnd = dieCenter - 5;
        int dieEnd = dieCenter + 5;
        int leftEnd = taperEnd - 11;

        drawFiberDrawingShape(
                graphics,
                leftStart,
                leftEnd,
                taperEnd,
                dieEnd,
                rightEnd,
                centerY,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 104),
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 104),
                rightEnd + 1
        );
        drawFiberDrawingShape(
                graphics,
                leftStart,
                leftEnd,
                taperEnd,
                dieEnd,
                rightEnd,
                centerY,
                ThermalSmelterUiSkin.withAlpha(accent, active ? 70 : 54),
                ThermalSmelterUiSkin.withAlpha(outputAccent, active ? 88 : 58),
                rightEnd + 1
        );

        if (active) {
            int required = Math.max(1, data(FiberDrawingMachineBlockEntity.DATA_PROGRESS_REQUIRED));
            float progress = Math.max(0.0F, Math.min(1.0F,
                    (data(FiberDrawingMachineBlockEntity.DATA_PROGRESS) + partialTick) / (float) required));
            int progressRight = leftStart + Math.round(progress * Math.max(1, rightEnd - leftStart));
            drawFiberDrawingShape(
                    graphics,
                    leftStart,
                    leftEnd,
                    taperEnd,
                    dieEnd,
                    rightEnd,
                    centerY,
                    ThermalSmelterUiSkin.withAlpha(accent, 178),
                    ThermalSmelterUiSkin.withAlpha(outputAccent, 208),
                    progressRight
            );
            graphics.fill(progressRight, centerY - 7, progressRight + 1, centerY + 8,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 96));
        }

        drawFiberPress(graphics, taperEnd, dieEnd, centerY, outputAccent, active);

        int moldX = leftPos + FiberDrawingMachineLayout.MOLD_INPUT_SLOT_X + FiberDrawingMachineLayout.SLOT_SIZE / 2;
        int outputTop = topPos + FiberDrawingMachineLayout.MOLD_OUTPUT_SLOT_Y - 3;
        int drawBottom = topPos + FiberDrawingMachineLayout.DRAW_REGION_Y + FiberDrawingMachineLayout.DRAW_REGION_HEIGHT - 3;
        graphics.fill(moldX, drawBottom, moldX + 1, outputTop,
                ThermalSmelterUiSkin.withAlpha(outputAccent, menu.ejectMold() ? 98 : 42));
        graphics.fill(moldX + 1, drawBottom, moldX + 2, outputTop,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, menu.ejectMold() ? 34 : 18));
    }

    private void drawPropertyBars(GuiGraphics graphics) {
        int[] values = propertyValues();
        for (int index = 0; index < FiberDrawingMachineLayout.PROPERTY_BAR_COUNT; index++) {
            int x = leftPos + FiberDrawingMachineLayout.PROPERTY_BARS_X;
            int y = topPos + FiberDrawingMachineLayout.PROPERTY_BARS_Y
                    + index * (FiberDrawingMachineLayout.PROPERTY_BAR_HEIGHT + FiberDrawingMachineLayout.PROPERTY_BAR_GAP);
            int value = values[index];
            int fill = Math.round((FiberDrawingMachineLayout.PROPERTY_BAR_WIDTH - 2) * value / 100.0F);
            graphics.fill(x, y, x + FiberDrawingMachineLayout.PROPERTY_BAR_WIDTH, y + FiberDrawingMachineLayout.PROPERTY_BAR_HEIGHT,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 96));
            graphics.fill(x + 1, y + 1, x + 1 + fill, y + FiberDrawingMachineLayout.PROPERTY_BAR_HEIGHT - 1,
                    ThermalSmelterUiSkin.withAlpha(PROPERTY_COLORS[index], value > 0 ? 174 : 44));
            outline(graphics, x, y, FiberDrawingMachineLayout.PROPERTY_BAR_WIDTH, FiberDrawingMachineLayout.PROPERTY_BAR_HEIGHT,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 116));
        }
    }

    private void drawSlots(GuiGraphics graphics) {
        slotFrame(graphics, leftPos + FiberDrawingMachineLayout.MATERIAL_SLOT_X,
                topPos + FiberDrawingMachineLayout.MATERIAL_SLOT_Y,
                materialAccent(),
                menu.getSlot(FiberDrawingMachineBlockEntity.SLOT_MATERIAL_INPUT).hasItem());
        slotFrame(graphics, leftPos + FiberDrawingMachineLayout.OUTPUT_SLOT_X,
                topPos + FiberDrawingMachineLayout.OUTPUT_SLOT_Y,
                DRAW_COLOR,
                menu.getSlot(FiberDrawingMachineBlockEntity.SLOT_OUTPUT).hasItem());
        slotFrame(graphics, leftPos + FiberDrawingMachineLayout.MOLD_INPUT_SLOT_X,
                topPos + FiberDrawingMachineLayout.MOLD_SLOT_Y,
                SINGLE_MODE_COLOR,
                menu.getSlot(FiberDrawingMachineBlockEntity.SLOT_MOLD_INPUT).hasItem());
        slotFrame(graphics, leftPos + FiberDrawingMachineLayout.MOLD_OUTPUT_SLOT_X,
                topPos + FiberDrawingMachineLayout.MOLD_OUTPUT_SLOT_Y,
                SINGLE_MODE_COLOR,
                menu.getSlot(FiberDrawingMachineBlockEntity.SLOT_MOLD_OUTPUT).hasItem());
    }

    private void drawMoldRuleButton(GuiGraphics graphics) {
        int x = leftPos + FiberDrawingMachineLayout.RULE_BUTTON_X;
        int y = topPos + FiberDrawingMachineLayout.RULE_BUTTON_Y;
        boolean eject = menu.ejectMold();
        int color = ThermalSmelterUiSkin.withAlpha(DRAW_COLOR, eject ? 190 : 118);
        int fill = ThermalSmelterUiSkin.withAlpha(DRAW_COLOR, eject ? 52 : 28);

        graphics.fill(x, y,
                x + FiberDrawingMachineLayout.RULE_BUTTON_WIDTH,
                y + FiberDrawingMachineLayout.RULE_BUTTON_HEIGHT,
                fill);
        outline(graphics, x, y,
                FiberDrawingMachineLayout.RULE_BUTTON_WIDTH,
                FiberDrawingMachineLayout.RULE_BUTTON_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(DRAW_COLOR, eject ? 142 : 90));
        graphics.fill(x + 5, y + 4, x + 13, y + 7, color);

        if (eject) {
            graphics.fill(x + 8, y + 7, x + 10, y + 12, color);
            graphics.fill(x + 6, y + 10, x + 12, y + 11, color);
            graphics.fill(x + 7, y + 11, x + 11, y + 12, color);
            graphics.fill(x + 8, y + 12, x + 10, y + 13, color);
        } else {
            graphics.fill(x + 5, y + 11, x + 13, y + 12, color);
            graphics.fill(x + 5, y + 13, x + 13, y + 14, ThermalSmelterUiSkin.withAlpha(color, 138));
        }
    }

    private void drawPlayerInventory(GuiGraphics graphics) {
        drawPanel(graphics,
                leftPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_PANEL_X,
                topPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_PANEL_Y,
                FiberDrawingMachineLayout.PLAYER_INVENTORY_PANEL_WIDTH,
                FiberDrawingMachineLayout.PLAYER_INVENTORY_PANEL_HEIGHT);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                ceramicSlot(
                        graphics,
                        leftPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_X
                                + column * FiberDrawingMachineLayout.SLOT_SIZE,
                        topPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_Y
                                + row * FiberDrawingMachineLayout.SLOT_SIZE
                );
            }
        }

        int hotbarY = FiberDrawingMachineLayout.PLAYER_INVENTORY_Y + 58;
        graphics.fill(leftPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_X, topPos + hotbarY - 4,
                leftPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_X + 9 * FiberDrawingMachineLayout.SLOT_SIZE,
                topPos + hotbarY - 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 88));
        for (int column = 0; column < 9; column++) {
            ceramicSlot(graphics, leftPos + FiberDrawingMachineLayout.PLAYER_INVENTORY_X
                    + column * FiberDrawingMachineLayout.SLOT_SIZE, topPos + hotbarY);
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
                FiberDrawingMachineLayout.ENERGY_BAR_X,
                FiberDrawingMachineLayout.ENERGY_BAR_Y,
                FiberDrawingMachineLayout.ENERGY_BAR_WIDTH,
                FiberDrawingMachineLayout.ENERGY_BAR_HEIGHT)) {
            return List.of(Component.translatable(
                    "screen.spectralization.fiber_drawing_machine.tooltip.energy",
                    data(FiberDrawingMachineBlockEntity.DATA_ENERGY),
                    data(FiberDrawingMachineBlockEntity.DATA_ENERGY_MAX)
            ));
        }

        if (insideSlot(mouseX, mouseY, FiberDrawingMachineLayout.MATERIAL_SLOT_X, FiberDrawingMachineLayout.MATERIAL_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.material"));
        }

        if (insideSlot(mouseX, mouseY, FiberDrawingMachineLayout.OUTPUT_SLOT_X, FiberDrawingMachineLayout.OUTPUT_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.output"));
        }

        if (insideSlot(mouseX, mouseY, FiberDrawingMachineLayout.MOLD_INPUT_SLOT_X, FiberDrawingMachineLayout.MOLD_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.mold_input"));
        }

        if (insideSlot(mouseX, mouseY, FiberDrawingMachineLayout.MOLD_OUTPUT_SLOT_X, FiberDrawingMachineLayout.MOLD_OUTPUT_SLOT_Y)) {
            return List.of(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.mold_output"));
        }

        if (insideLocal(mouseX, mouseY,
                FiberDrawingMachineLayout.RULE_BUTTON_X,
                FiberDrawingMachineLayout.RULE_BUTTON_Y,
                FiberDrawingMachineLayout.RULE_BUTTON_WIDTH,
                FiberDrawingMachineLayout.RULE_BUTTON_HEIGHT)) {
            return List.of(Component.translatable(menu.ejectMold()
                    ? "screen.spectralization.fiber_drawing_machine.tooltip.rule_eject"
                    : "screen.spectralization.fiber_drawing_machine.tooltip.rule_keep"));
        }

        if (insideLocal(mouseX, mouseY,
                FiberDrawingMachineLayout.PROPERTY_REGION_X,
                FiberDrawingMachineLayout.PROPERTY_REGION_Y,
                FiberDrawingMachineLayout.PROPERTY_REGION_WIDTH,
                FiberDrawingMachineLayout.PROPERTY_REGION_HEIGHT)) {
            FiberDrawingParameters parameters = propertyParameters();
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.properties"));
            tooltip.add(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.aperture", parameters.apertureText()));
            tooltip.add(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.capacity", parameters.maxCapacityText()));
            tooltip.add(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.loss", parameters.lossText()));
            return tooltip;
        }

        if (insideLocal(mouseX, mouseY,
                FiberDrawingMachineLayout.RECIPE_CLICK_X,
                FiberDrawingMachineLayout.RECIPE_CLICK_Y,
                FiberDrawingMachineLayout.RECIPE_CLICK_WIDTH,
                FiberDrawingMachineLayout.RECIPE_CLICK_HEIGHT)) {
            return List.of(Component.translatable("screen.spectralization.fiber_drawing_machine.tooltip.recipe"));
        }

        return List.of();
    }

    private int data(int index) {
        return menu.data(index);
    }

    private Optional<LensMaterial> material() {
        return LensMaterial.fromBlank(menu.getSlot(FiberDrawingMachineBlockEntity.SLOT_MATERIAL_INPUT).getItem());
    }

    private int[] propertyValues() {
        return propertyParameters().values();
    }

    private FiberDrawingParameters propertyParameters() {
        LensMaterial material = material().orElse(null);

        if (material == null || data(FiberDrawingMachineBlockEntity.DATA_MOLD_KIND) == 0) {
            return FiberDrawingParameters.empty();
        }

        return FiberDrawingParameters.from(material, data(FiberDrawingMachineBlockEntity.DATA_MOLD_KIND) == 2);
    }

    private boolean isCrafting() {
        return data(FiberDrawingMachineBlockEntity.DATA_STATE) == FiberDrawingMachineBlockEntity.STATE_READY;
    }

    private int materialAccent() {
        return material().map(material -> switch (material) {
            case ORDINARY -> 0xFFC7E8EE;
            case SILVERED -> 0xFFE2E7EC;
            case QUARTZ -> 0xFFA9F3FF;
            case BOROSILICATE -> 0xFF70D7DA;
            case CROWN -> 0xFFA8DFA0;
            case FLINT -> 0xFFFFC46E;
            case HEAVY -> 0xFF7E91B4;
        }).orElse(DRAW_COLOR);
    }

    private boolean insideSlot(int mouseX, int mouseY, int x, int y) {
        return insideLocal(mouseX, mouseY, x, y, FiberDrawingMachineLayout.SLOT_SIZE, FiberDrawingMachineLayout.SLOT_SIZE);
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private void drawScreenShell(GuiGraphics graphics) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, ThermalSmelterUiSkin.GUI_BG);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + FiberDrawingMachineLayout.MACHINE_HEIGHT,
                ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, leftPos, topPos, imageWidth, imageHeight, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + 3, topPos + imageHeight - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(leftPos + 1, topPos + imageHeight - 3, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + imageWidth - 3, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1,
                ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(leftPos + 8, topPos + FiberDrawingMachineLayout.MACHINE_HEIGHT + 1,
                leftPos + imageWidth - 8, topPos + FiberDrawingMachineLayout.MACHINE_HEIGHT + 2,
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
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 30));
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 54));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 50));
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 46));
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        ceramicSlot(graphics, x, y);
        if (active) {
            graphics.fill(x + FiberDrawingMachineLayout.SLOT_SIZE - 2, y + 2,
                    x + FiberDrawingMachineLayout.SLOT_SIZE - 1, y + FiberDrawingMachineLayout.SLOT_SIZE - 2,
                    ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + FiberDrawingMachineLayout.SLOT_SIZE - 2,
                    x + FiberDrawingMachineLayout.SLOT_SIZE - 2, y + FiberDrawingMachineLayout.SLOT_SIZE - 1,
                    ThermalSmelterUiSkin.withAlpha(color, 90));
        } else {
            outline(graphics, x, y, FiberDrawingMachineLayout.SLOT_SIZE, FiberDrawingMachineLayout.SLOT_SIZE,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 95));
        }
    }

    private static void drawFiberDrawingShape(
            GuiGraphics graphics,
            int leftStart,
            int leftEnd,
            int taperEnd,
            int dieEnd,
            int rightEnd,
            int centerY,
            int leftColor,
            int rightColor,
            int clipRight
    ) {
        int wideHalf = 6;
        int narrowHalf = 2;

        fillClipped(graphics, leftStart, centerY - wideHalf, leftEnd, centerY + wideHalf + 1, clipRight, leftColor);

        int taperWidth = Math.max(1, taperEnd - leftEnd);
        for (int offset = 0; offset < taperWidth; offset++) {
            double t = offset / (double) Math.max(1, taperWidth - 1);
            int half = (int) Math.round(wideHalf + (narrowHalf - wideHalf) * t);
            int color = t < 0.55D ? leftColor : rightColor;
            fillClipped(graphics, leftEnd + offset, centerY - half, leftEnd + offset + 1, centerY + half + 1,
                    clipRight, color);
        }

        fillClipped(graphics, taperEnd, centerY - narrowHalf, dieEnd, centerY + narrowHalf + 1, clipRight, rightColor);
        fillClipped(graphics, dieEnd, centerY - narrowHalf, rightEnd, centerY + narrowHalf + 1, clipRight, rightColor);
        fillClipped(graphics, leftStart + 1, centerY - wideHalf + 1, leftEnd - 1, centerY - wideHalf + 2,
                clipRight, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 38));
        fillClipped(graphics, dieEnd, centerY - narrowHalf + 1, rightEnd - 1, centerY - narrowHalf + 2,
                clipRight, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 50));
    }

    private static void drawFiberPress(
            GuiGraphics graphics,
            int x,
            int right,
            int centerY,
            int accent,
            boolean active
    ) {
        int top = centerY - 10;
        int bottom = centerY + 11;
        int mid = (x + right) / 2;

        outline(graphics, x - 1, top, right - x + 2, bottom - top, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 132));
        graphics.fill(x, top + 1, right, centerY - 4, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 92));
        graphics.fill(x, centerY + 5, right, bottom - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 108));
        graphics.fill(x + 1, top + 1, right - 1, top + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 54));
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 82));

        for (int step = 0; step < 4; step++) {
            graphics.fill(mid - 4 + step, centerY - 4 + step, mid + 5 - step, centerY - 3 + step,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 112));
            graphics.fill(mid - 4 + step, centerY + 4 - step, mid + 5 - step, centerY + 5 - step,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 126));
        }

        graphics.fill(x + 2, centerY - 1, right - 2, centerY + 2,
                ThermalSmelterUiSkin.withAlpha(accent, active ? 172 : 72));
    }

    private static void fillClipped(
            GuiGraphics graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int clipRight,
            int color
    ) {
        int right = Math.min(x2, clipRight);
        if (right > x1 && y2 > y1) {
            graphics.fill(x1, y1, right, y2, color);
        }
    }

    private static void ceramicSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + FiberDrawingMachineLayout.SLOT_SIZE, y + FiberDrawingMachineLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, FiberDrawingMachineLayout.SLOT_SIZE, FiberDrawingMachineLayout.SLOT_SIZE,
                ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + FiberDrawingMachineLayout.SLOT_SIZE - 1, y + 2,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + FiberDrawingMachineLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + FiberDrawingMachineLayout.SLOT_SIZE - 2,
                x + FiberDrawingMachineLayout.SLOT_SIZE - 1, y + FiberDrawingMachineLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + FiberDrawingMachineLayout.SLOT_SIZE - 2, y + 1,
                x + FiberDrawingMachineLayout.SLOT_SIZE - 1, y + FiberDrawingMachineLayout.SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + FiberDrawingMachineLayout.SLOT_SIZE - 2,
                y + FiberDrawingMachineLayout.SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
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
