package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.LensGrindingBenchBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.menu.LensGrindingBenchLayout;
import io.github.yoglappland.spectralization.menu.LensGrindingBenchMenu;
import io.github.yoglappland.spectralization.optics.lens.LensKind;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class LensGrindingBenchScreen extends SpectralMachineScreen<LensGrindingBenchMenu> {
    private static final int COLOR_COARSE = 0xFFFF9F43;
    private static final int COLOR_CLEAR = ThermalSmelterUiSkin.OPTICAL;
    private static final int COLOR_PRECISE = 0xFFE6FFF6;
    private static final int COLOR_TARGET = ThermalSmelterUiSkin.PROGRESS;
    private static final int DETAIL_LINE = ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 72);

    public LensGrindingBenchScreen(LensGrindingBenchMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "lens_grinding_bench",
                LensGrindingBenchLayout.IMAGE_WIDTH,
                LensGrindingBenchLayout.IMAGE_HEIGHT,
                LensGrindingBenchLayout.INVENTORY_LABEL_X,
                LensGrindingBenchLayout.INVENTORY_LABEL_Y
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
                    LensGrindingBenchLayout.KIND_PREV_X,
                    LensGrindingBenchLayout.KIND_BUTTON_Y,
                    LensGrindingBenchLayout.KIND_BUTTON_SIZE,
                    LensGrindingBenchLayout.KIND_BUTTON_SIZE)) {
                click(LensGrindingBenchMenu.BUTTON_KIND_DOWN);
                return true;
            }

            if (insideLocal(mouseX, mouseY,
                    LensGrindingBenchLayout.KIND_NEXT_X,
                    LensGrindingBenchLayout.KIND_BUTTON_Y,
                    LensGrindingBenchLayout.KIND_BUTTON_SIZE,
                    LensGrindingBenchLayout.KIND_BUTTON_SIZE)) {
                click(LensGrindingBenchMenu.BUTTON_KIND_UP);
                return true;
            }

            if (!targetLocked() && insideLocal(mouseX, mouseY,
                    LensGrindingBenchLayout.TARGET_PREV_X,
                    LensGrindingBenchLayout.TARGET_BUTTON_Y,
                    LensGrindingBenchLayout.TARGET_BUTTON_SIZE,
                    LensGrindingBenchLayout.TARGET_BUTTON_SIZE)) {
                click(LensGrindingBenchMenu.BUTTON_TARGET_DOWN);
                return true;
            }

            if (!targetLocked() && insideLocal(mouseX, mouseY,
                    LensGrindingBenchLayout.TARGET_NEXT_X,
                    LensGrindingBenchLayout.TARGET_BUTTON_Y,
                    LensGrindingBenchLayout.TARGET_BUTTON_SIZE,
                    LensGrindingBenchLayout.TARGET_BUTTON_SIZE)) {
                click(LensGrindingBenchMenu.BUTTON_TARGET_UP);
                return true;
            }

            if (insideGrindWheel(mouseX, mouseY)) {
                click(LensGrindingBenchMenu.BUTTON_GRIND);
                return true;
            }

            if (insideGrindButton(mouseX, mouseY)) {
                click(LensGrindingBenchMenu.BUTTON_GRIND);
                return true;
            }

        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!targetLocked() && scrollY != 0.0D && insideTargetControls(mouseX, mouseY)) {
            click(scrollY > 0.0D
                    ? LensGrindingBenchMenu.BUTTON_TARGET_UP
                    : LensGrindingBenchMenu.BUTTON_TARGET_DOWN);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ThermalSmelterUiSkin.drawScreenShell(graphics, leftPos, topPos, imageWidth, imageHeight);
        drawProcess(graphics);
        ThermalSmelterUiSkin.drawPlayerInventory(graphics, font, leftPos, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private void drawProcess(GuiGraphics graphics) {
        drawCeramicPanel(
                graphics,
                leftPos + LensGrindingBenchLayout.PROCESS_X,
                topPos + LensGrindingBenchLayout.PROCESS_Y,
                LensGrindingBenchLayout.PROCESS_WIDTH,
                LensGrindingBenchLayout.PROCESS_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + LensGrindingBenchLayout.LEFT_PANEL_X,
                topPos + LensGrindingBenchLayout.SIDE_PANEL_Y,
                LensGrindingBenchLayout.SIDE_PANEL_WIDTH,
                LensGrindingBenchLayout.SIDE_PANEL_HEIGHT
        );
        drawCeramicPanel(
                graphics,
                leftPos + LensGrindingBenchLayout.RIGHT_PANEL_X,
                topPos + LensGrindingBenchLayout.SIDE_PANEL_Y,
                LensGrindingBenchLayout.SIDE_PANEL_WIDTH,
                LensGrindingBenchLayout.SIDE_PANEL_HEIGHT
        );
        drawInputPanel(graphics);
        drawChamber(graphics);
        drawOutputPanel(graphics);
    }

    private void drawInputPanel(GuiGraphics graphics) {
        drawInputRail(graphics);
        slotFrame(graphics, leftPos + LensGrindingBenchLayout.SLOT_BLANK_X, topPos + LensGrindingBenchLayout.SLOT_BLANK_Y, ThermalSmelterUiSkin.HEAT, hasBlank());
        slotFrame(graphics, leftPos + LensGrindingBenchLayout.SLOT_TOOL_X, topPos + LensGrindingBenchLayout.SLOT_TOOL_Y, ThermalSmelterUiSkin.PROGRESS, hasTool());
        slotFrame(graphics, leftPos + LensGrindingBenchLayout.SLOT_REFERENCE_X, topPos + LensGrindingBenchLayout.SLOT_REFERENCE_Y, ThermalSmelterUiSkin.OPTICAL, hasReference());
    }

    private void drawInputRail(GuiGraphics graphics) {
        int centerX = leftPos + LensGrindingBenchLayout.LEFT_PANEL_CENTER_X;
        int top = topPos + LensGrindingBenchLayout.SLOT_BLANK_Y + LensGrindingBenchLayout.SLOT_SIZE - 1;
        int bottom = topPos + LensGrindingBenchLayout.SLOT_REFERENCE_Y + 1;

        graphics.fill(centerX - 1, top, centerX + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
        graphics.fill(centerX, top, centerX + 1, bottom, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
    }

    private void drawOutputPanel(GuiGraphics graphics) {
        int slotX = leftPos + LensGrindingBenchLayout.SLOT_OUTPUT_X;
        int slotY = topPos + LensGrindingBenchLayout.SLOT_OUTPUT_Y;

        slotFrame(graphics, slotX, slotY, ThermalSmelterUiSkin.PROGRESS, true);
        drawGrindingWheel(graphics);
        drawGrindButton(graphics);

        if (outputBlocked()) {
            blockedOverlay(graphics, slotX, slotY);
        }
    }

    private void drawGrindingWheel(GuiGraphics graphics) {
        int centerX = leftPos + LensGrindingBenchLayout.GRIND_WHEEL_CENTER_X;
        int centerY = topPos + LensGrindingBenchLayout.GRIND_WHEEL_CENTER_Y;
        int radius = LensGrindingBenchLayout.GRIND_WHEEL_RADIUS;
        int stateColor = menu.ready() ? ThermalSmelterUiSkin.STATUS_READY
                : outputBlocked() ? ThermalSmelterUiSkin.STATUS_INVALID
                : ThermalSmelterUiSkin.STATUS_EMPTY;
        int max = Math.max(1, data(LensGrindingBenchBlockEntity.DATA_GRIND_PROGRESS_MAX));
        int progress = Math.max(0, Math.min(max, data(LensGrindingBenchBlockEntity.DATA_GRIND_PROGRESS)));
        double sweep = 360.0D * progress / max;

        drawDisk(graphics, centerX, centerY, radius, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 122));
        drawRing(graphics, centerX, centerY, radius, 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 184));
        drawArcRing(graphics, centerX, centerY, radius, 3, sweep, ThermalSmelterUiSkin.withAlpha(stateColor, menu.ready() ? 230 : 150));
        drawRing(graphics, centerX, centerY, radius - 4, 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 80));
        drawDisk(graphics, centerX, centerY, 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL, 235));
        drawRing(graphics, centerX, centerY, 4, 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 150));
        graphics.fill(centerX - radius + 3, centerY - radius + 3, centerX - radius + 6, centerY - radius + 4, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 110));
        graphics.fill(centerX + radius - 5, centerY + radius - 4, centerX + radius - 2, centerY + radius - 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 155));
    }

    private void drawGrindButton(GuiGraphics graphics) {
        int x = leftPos + LensGrindingBenchLayout.GRIND_BUTTON_X;
        int y = topPos + LensGrindingBenchLayout.GRIND_BUTTON_Y;
        int width = LensGrindingBenchLayout.GRIND_BUTTON_WIDTH;
        int height = LensGrindingBenchLayout.GRIND_BUTTON_HEIGHT;
        InteractionWeight weight = menu.ready() ? InteractionWeight.PRIMARY
                : outputBlocked() ? InteractionWeight.SECONDARY
                : InteractionWeight.TERTIARY;
        int stateColor = menu.ready() ? ThermalSmelterUiSkin.STATUS_READY
                : outputBlocked() ? ThermalSmelterUiSkin.STATUS_INVALID
                : ThermalSmelterUiSkin.STATUS_EMPTY;

        softButtonShell(graphics, x, y, width, height, weight);
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, ThermalSmelterUiSkin.withAlpha(stateColor, menu.ready() ? 180 : 92));
        drawCenteredText(
                graphics,
                font,
                Component.translatable("screen.spectralization.lens_grinding_bench.grind").getString(),
                x + width / 2,
                y + 2,
                0.46F,
                weight.arrowColor()
        );
    }

    private void drawChamber(GuiGraphics graphics) {
        int x = leftPos + LensGrindingBenchLayout.CHAMBER_X;
        int y = topPos + LensGrindingBenchLayout.CHAMBER_Y;

        drawCeramicPanel(graphics, x, y, LensGrindingBenchLayout.CHAMBER_WIDTH, LensGrindingBenchLayout.CHAMBER_HEIGHT);
        insetPanel(
                graphics,
                leftPos + LensGrindingBenchLayout.CHAMBER_INNER_X,
                topPos + LensGrindingBenchLayout.CHAMBER_INNER_Y,
                LensGrindingBenchLayout.CHAMBER_INNER_WIDTH,
                LensGrindingBenchLayout.CHAMBER_INNER_HEIGHT,
                ThermalSmelterUiSkin.CHAMBER_BG
        );
        drawChamberRegions(graphics);

        drawKindControls(graphics);
        drawTargetControls(graphics);
        drawQualityBar(graphics);
        drawRangePreview(graphics);
    }

    private void drawChamberRegions(GuiGraphics graphics) {
        subtleRegion(
                graphics,
                leftPos + LensGrindingBenchLayout.KIND_ZONE_X,
                topPos + LensGrindingBenchLayout.KIND_ZONE_Y,
                LensGrindingBenchLayout.KIND_ZONE_WIDTH,
                LensGrindingBenchLayout.KIND_ZONE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 42)
        );
        subtleRegion(
                graphics,
                leftPos + LensGrindingBenchLayout.TARGET_ZONE_X,
                topPos + LensGrindingBenchLayout.TARGET_ZONE_Y,
                LensGrindingBenchLayout.TARGET_ZONE_WIDTH,
                LensGrindingBenchLayout.TARGET_ZONE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 36)
        );
        subtleRegion(
                graphics,
                leftPos + LensGrindingBenchLayout.RIGHT_ZONE_X,
                topPos + LensGrindingBenchLayout.RIGHT_ZONE_Y,
                LensGrindingBenchLayout.RIGHT_ZONE_WIDTH,
                LensGrindingBenchLayout.RIGHT_ZONE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 38)
        );
        drawMeterPlate(graphics);
    }

    private void drawKindControls(GuiGraphics graphics) {
        int centerX = leftPos + LensGrindingBenchLayout.KIND_CENTER_X;
        int centerY = topPos + LensGrindingBenchLayout.KIND_CENTER_Y;
        drawIconButton(graphics, LensGrindingBenchLayout.KIND_PREV_X, LensGrindingBenchLayout.KIND_BUTTON_Y, false, InteractionWeight.TERTIARY);
        drawIconButton(graphics, LensGrindingBenchLayout.KIND_NEXT_X, LensGrindingBenchLayout.KIND_BUTTON_Y, true, InteractionWeight.TERTIARY);
        drawLensGlyph(graphics, centerX, centerY, lensKind());
        drawCenteredText(graphics, font, shortKindCode(), centerX, topPos + LensGrindingBenchLayout.KIND_CODE_Y, 0.54F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawTargetControls(GuiGraphics graphics) {
        int boxX = leftPos + LensGrindingBenchLayout.TARGET_BOX_X;
        int boxY = topPos + LensGrindingBenchLayout.TARGET_BOX_Y;
        int centerX = boxX + LensGrindingBenchLayout.TARGET_BOX_WIDTH / 2;
        InteractionWeight targetWeight = targetLocked() ? InteractionWeight.DISABLED : InteractionWeight.TERTIARY;
        drawIconButton(graphics, LensGrindingBenchLayout.TARGET_PREV_X, LensGrindingBenchLayout.TARGET_BUTTON_Y, false, targetWeight);
        drawIconButton(graphics, LensGrindingBenchLayout.TARGET_NEXT_X, LensGrindingBenchLayout.TARGET_BUTTON_Y, true, targetWeight);
        drawCenteredText(graphics, font, parameterSymbol() + "=" + formatUnits(data(LensGrindingBenchBlockEntity.DATA_TARGET)), centerX, boxY, 0.72F, ThermalSmelterUiSkin.TEXT_SUB);
    }

    private void drawQualityBar(GuiGraphics graphics) {
        int x = leftPos + LensGrindingBenchLayout.QUALITY_BAR_X;
        int y = topPos + LensGrindingBenchLayout.QUALITY_BAR_Y;
        int width = LensGrindingBenchLayout.QUALITY_BAR_WIDTH;
        int height = LensGrindingBenchLayout.QUALITY_BAR_HEIGHT;
        int coarse = data(LensGrindingBenchBlockEntity.DATA_COARSE_CHANCE);
        int clear = data(LensGrindingBenchBlockEntity.DATA_CLEAR_CHANCE);
        int coarseHeight = Math.round(height * coarse / 100.0F);
        int clearHeight = Math.round(height * clear / 100.0F);
        int preciseHeight = Math.max(0, height - coarseHeight - clearHeight);
        int bottom = y + height;
        int coarseTop = bottom - coarseHeight;
        int clearTop = coarseTop - clearHeight;

        meterFrame(graphics, x - 1, y - 1, width + 2, height + 2, COLOR_CLEAR);
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 68));
        graphics.fill(x, coarseTop, x + width, bottom, ThermalSmelterUiSkin.withAlpha(COLOR_COARSE, 205));
        graphics.fill(x, clearTop, x + width, coarseTop, ThermalSmelterUiSkin.withAlpha(COLOR_CLEAR, 205));
        graphics.fill(x, clearTop - preciseHeight, x + width, clearTop, ThermalSmelterUiSkin.withAlpha(COLOR_PRECISE, 205));
        drawQualitySeparators(graphics, x, width, coarseTop, clearTop, coarseHeight, clearHeight, preciseHeight);
        drawSideScaleTicks(graphics, x, y, width, height, true);
        drawSideScaleLabels(
                graphics,
                font,
                "1",
                "0",
                leftPos + LensGrindingBenchLayout.QUALITY_SCALE_LABEL_X,
                topPos + LensGrindingBenchLayout.SCALE_TOP_LABEL_Y,
                topPos + LensGrindingBenchLayout.SCALE_BOTTOM_LABEL_Y,
                LensGrindingBenchLayout.METER_SCALE_LABEL_WIDTH,
                true,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.TEXT_SUB, 155)
        );
    }

    private void drawRangePreview(GuiGraphics graphics) {
        int x = leftPos + LensGrindingBenchLayout.RANGE_X;
        int y = topPos + LensGrindingBenchLayout.RANGE_Y;
        int min = data(LensGrindingBenchBlockEntity.DATA_TARGET_MIN);
        int max = Math.max(min + 1, data(LensGrindingBenchBlockEntity.DATA_TARGET_MAX));
        int target = data(LensGrindingBenchBlockEntity.DATA_TARGET);
        int previewMin = data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MIN);
        int previewMax = data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MAX);
        int width = LensGrindingBenchLayout.RANGE_WIDTH;
        int height = LensGrindingBenchLayout.RANGE_HEIGHT;

        meterFrame(graphics, x - 1, y - 1, width + 2, height + 2, COLOR_TARGET);
        graphics.fill(x + 3, y, x + width - 3, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 112));
        int rollTop = interpolateVertical(y, y + height - 1, previewMax, min, max);
        int rollBottom = interpolateVertical(y, y + height - 1, previewMin, min, max);
        graphics.fill(x + 1, rollTop, x + width - 1, rollBottom + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 122));
        int targetY = interpolateVertical(y, y + height - 1, target, min, max);
        graphics.fill(x - 3, targetY, x + width + 3, targetY + 1, COLOR_TARGET);
        drawSideScaleTicks(graphics, x, y, width, height, false);
        drawSideScaleLabels(
                graphics,
                font,
                formatUnits(max),
                formatUnits(min),
                leftPos + LensGrindingBenchLayout.RANGE_SCALE_LABEL_X,
                topPos + LensGrindingBenchLayout.SCALE_TOP_LABEL_Y,
                topPos + LensGrindingBenchLayout.SCALE_BOTTOM_LABEL_Y,
                LensGrindingBenchLayout.METER_SCALE_LABEL_WIDTH,
                false,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.TEXT_SUB, 155)
        );
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
        if (insideSlot(mouseX, mouseY, LensGrindingBenchLayout.SLOT_BLANK_X, LensGrindingBenchLayout.SLOT_BLANK_Y)) {
            return List.of(tt("tooltip.blank"));
        }

        if (insideSlot(mouseX, mouseY, LensGrindingBenchLayout.SLOT_TOOL_X, LensGrindingBenchLayout.SLOT_TOOL_Y)) {
            return toolTooltip();
        }

        if (insideSlot(mouseX, mouseY, LensGrindingBenchLayout.SLOT_REFERENCE_X, LensGrindingBenchLayout.SLOT_REFERENCE_Y)) {
            return List.of(tt("tooltip.reference"));
        }

        if (insideSlot(mouseX, mouseY, LensGrindingBenchLayout.SLOT_OUTPUT_X, LensGrindingBenchLayout.SLOT_OUTPUT_Y)) {
            return List.of(outputBlocked() ? tt("tooltip.output_blocked") : tt("tooltip.output"));
        }

        if (insideGrindWheel(mouseX, mouseY)) {
            return grindWheelTooltip();
        }

        if (insideGrindButton(mouseX, mouseY)) {
            return grindWheelTooltip();
        }

        if (insideAnyButton(mouseX, mouseY, LensGrindingBenchLayout.KIND_PREV_X, LensGrindingBenchLayout.KIND_BUTTON_Y)
                || insideAnyButton(mouseX, mouseY, LensGrindingBenchLayout.KIND_NEXT_X, LensGrindingBenchLayout.KIND_BUTTON_Y)) {
            return List.of(tt("tooltip.kind", Component.translatable(lensKind().translationKey())));
        }

        if (insideLocal(mouseX, mouseY,
                LensGrindingBenchLayout.KIND_ZONE_X,
                LensGrindingBenchLayout.KIND_ZONE_Y,
                LensGrindingBenchLayout.KIND_ZONE_WIDTH,
                LensGrindingBenchLayout.KIND_ZONE_HEIGHT)) {
            return List.of(tt("tooltip.kind", Component.translatable(lensKind().translationKey())));
        }

        if (insideTargetControls(mouseX, mouseY)) {
            return targetTooltip();
        }

        if (insideLocal(mouseX, mouseY, LensGrindingBenchLayout.QUALITY_BAR_X - 2, LensGrindingBenchLayout.QUALITY_BAR_Y - 2, LensGrindingBenchLayout.QUALITY_BAR_WIDTH + 4, LensGrindingBenchLayout.QUALITY_BAR_HEIGHT + 4)) {
            return List.of(tt(
                    "tooltip.quality",
                    data(LensGrindingBenchBlockEntity.DATA_COARSE_CHANCE),
                    data(LensGrindingBenchBlockEntity.DATA_CLEAR_CHANCE),
                    data(LensGrindingBenchBlockEntity.DATA_PRECISE_CHANCE)
            ));
        }

        if (insideLocal(mouseX, mouseY, LensGrindingBenchLayout.RANGE_X - 2, LensGrindingBenchLayout.RANGE_Y - 3, LensGrindingBenchLayout.RANGE_WIDTH + 4, LensGrindingBenchLayout.RANGE_HEIGHT + 6)) {
            return previewTooltip();
        }

        if (insideLocal(mouseX, mouseY,
                LensGrindingBenchLayout.RIGHT_ZONE_X,
                LensGrindingBenchLayout.RIGHT_ZONE_Y,
                LensGrindingBenchLayout.RIGHT_ZONE_WIDTH,
                LensGrindingBenchLayout.RIGHT_ZONE_HEIGHT)) {
            return rightZoneTooltip();
        }

        return List.of();
    }

    private List<Component> targetTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.target", Component.translatable(lensKind().parameterKey()), formatUnits(data(LensGrindingBenchBlockEntity.DATA_TARGET))));
        if (targetLocked()) {
            tooltip.add(tt("tooltip.target_locked"));
        }
        appendMaterialTooltip(tooltip);
        tooltip.add(tt("tooltip.aperture_fixed"));
        return tooltip;
    }

    private List<Component> previewTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt(
                "tooltip.preview",
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MIN)),
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MAX)),
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_ERROR))
        ));
        appendMaterialTooltip(tooltip);
        return tooltip;
    }

    private List<Component> rightZoneTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.quality",
                data(LensGrindingBenchBlockEntity.DATA_COARSE_CHANCE),
                data(LensGrindingBenchBlockEntity.DATA_CLEAR_CHANCE),
                data(LensGrindingBenchBlockEntity.DATA_PRECISE_CHANCE)));
        tooltip.add(tt(
                "tooltip.preview",
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MIN)),
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_PREVIEW_MAX)),
                formatUnits(data(LensGrindingBenchBlockEntity.DATA_ERROR))
        ));
        appendMaterialTooltip(tooltip);
        return tooltip;
    }

    private List<Component> toolTooltip() {
        ItemStack tool = menu.getSlot(LensGrindingBenchBlockEntity.SLOT_TOOL).getItem();
        int tier = LensGrindingBenchBlockEntity.toolTier(tool);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.tool"));
        if (tier >= 0) {
            tooltip.add(tt("tooltip.tool_tier", tier + 1, data(LensGrindingBenchBlockEntity.DATA_ALLOWED_KINDS)));
            tooltip.add(tt("tooltip.tool_step", formatUnits(data(LensGrindingBenchBlockEntity.DATA_TARGET_STEP))));
            if (targetLocked()) {
                tooltip.add(tt("tooltip.target_locked"));
            }
        }
        return tooltip;
    }

    private List<Component> grindWheelTooltip() {
        List<Component> tooltip = new ArrayList<>();

        if (outputBlocked()) {
            tooltip.add(tt("tooltip.output_blocked"));
        } else if (!hasBlank()) {
            tooltip.add(tt("tooltip.missing_blank"));
        } else if (!hasTool()) {
            tooltip.add(tt("tooltip.missing_tool"));
        } else if (menu.ready()) {
            tooltip.add(tt("tooltip.grind_ready"));
        } else {
            tooltip.add(tt("tooltip.grind_blocked"));
        }

        tooltip.add(tt("tooltip.grind_progress", progressPercent()));
        return tooltip;
    }

    private void appendMaterialTooltip(List<Component> tooltip) {
        if (!hasBlank()) {
            return;
        }

        LensMaterial material = blankMaterial();
        tooltip.add(tt(
                "tooltip.material",
                Component.translatable(material.translationKey()),
                material.minFocalLengthText(),
                material.maxFocalLengthText(),
                material.transmittancePercent(2)
        ));
    }

    private boolean insideSlot(double mouseX, double mouseY, int x, int y) {
        return insideLocal(
                mouseX,
                mouseY,
                x - 2,
                y - 2,
                LensGrindingBenchLayout.SLOT_SIZE + 4,
                LensGrindingBenchLayout.SLOT_SIZE + 4
        );
    }

    private boolean insideAnyButton(double mouseX, double mouseY, int x, int y) {
        return insideLocal(
                mouseX,
                mouseY,
                x,
                y,
                LensGrindingBenchLayout.KIND_BUTTON_SIZE,
                LensGrindingBenchLayout.KIND_BUTTON_SIZE
        );
    }

    private boolean insideGrindWheel(double mouseX, double mouseY) {
        double dx = mouseX - leftPos - LensGrindingBenchLayout.GRIND_WHEEL_CENTER_X;
        double dy = mouseY - topPos - LensGrindingBenchLayout.GRIND_WHEEL_CENTER_Y;
        int radius = LensGrindingBenchLayout.GRIND_WHEEL_RADIUS + 2;
        return dx * dx + dy * dy <= radius * radius;
    }

    private boolean insideGrindButton(double mouseX, double mouseY) {
        return insideLocal(
                mouseX,
                mouseY,
                LensGrindingBenchLayout.GRIND_BUTTON_X,
                LensGrindingBenchLayout.GRIND_BUTTON_Y,
                LensGrindingBenchLayout.GRIND_BUTTON_WIDTH,
                LensGrindingBenchLayout.GRIND_BUTTON_HEIGHT
        );
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private void click(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private boolean hasBlank() {
        return menu.getSlot(LensGrindingBenchBlockEntity.SLOT_BLANK).hasItem();
    }

    private boolean hasTool() {
        return menu.getSlot(LensGrindingBenchBlockEntity.SLOT_TOOL).hasItem();
    }

    private boolean hasReference() {
        return menu.getSlot(LensGrindingBenchBlockEntity.SLOT_REFERENCE).hasItem();
    }

    private boolean outputBlocked() {
        return menu.getSlot(LensGrindingBenchBlockEntity.SLOT_OUTPUT).hasItem();
    }

    private boolean targetLocked() {
        return data(LensGrindingBenchBlockEntity.DATA_TARGET_LOCKED) != 0;
    }

    private boolean insideTargetControls(double mouseX, double mouseY) {
        return insideLocal(
                mouseX,
                mouseY,
                LensGrindingBenchLayout.TARGET_ZONE_X,
                LensGrindingBenchLayout.TARGET_ZONE_Y,
                LensGrindingBenchLayout.TARGET_ZONE_WIDTH,
                LensGrindingBenchLayout.TARGET_ZONE_HEIGHT
        );
    }

    private LensMaterial blankMaterial() {
        return LensMaterial.fromBlank(menu.getSlot(LensGrindingBenchBlockEntity.SLOT_BLANK).getItem())
                .orElse(LensMaterial.ORDINARY);
    }

    private LensKind lensKind() {
        return LensKind.byIndex(data(LensGrindingBenchBlockEntity.DATA_LENS_KIND));
    }

    private String parameterSymbol() {
        return lensKind().parameter().symbol();
    }

    private String shortKindCode() {
        return switch (lensKind()) {
            case CONVEX -> "CVX";
            case CONCAVE -> "CCV";
            case ENDER -> "END";
            case MAGMA -> "MAG";
            case ECHO -> "ECH";
        };
    }

    private String progressPercent() {
        int max = Math.max(1, data(LensGrindingBenchBlockEntity.DATA_GRIND_PROGRESS_MAX));
        int progress = Math.max(0, Math.min(max, data(LensGrindingBenchBlockEntity.DATA_GRIND_PROGRESS)));
        return Integer.toString(Math.round(progress * 100.0F / max));
    }

    private static String formatUnits(int value) {
        return LensProfile.formatUnits(value);
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private Component tt(String path, Object... args) {
        return Component.translatable("screen.spectralization.lens_grinding_bench." + path, args);
    }

    private void drawLensGlyph(GuiGraphics graphics, int centerX, int centerY, LensKind kind) {
        ResourceLocation icon = lensIcon(kind);
        if (minecraft != null && minecraft.getResourceManager().getResource(icon).isPresent()) {
            graphics.blit(icon, centerX - 8, centerY - 8, 0, 0, 16, 16, 16, 16);
            return;
        }

        int color = switch (kind) {
            case ENDER -> 0xFF8CE7FF;
            case MAGMA -> ThermalSmelterUiSkin.HEAT;
            case ECHO -> 0xFFAED9FF;
            default -> ThermalSmelterUiSkin.OPTICAL;
        };

        graphics.fill(centerX - 1, centerY - 8, centerX + 1, centerY + 9, 0xAAE8FFF8);

        switch (kind) {
            case CONCAVE -> {
                graphics.fill(centerX - 5, centerY - 7, centerX - 3, centerY + 8, color);
                graphics.fill(centerX + 3, centerY - 7, centerX + 5, centerY + 8, color);
                graphics.fill(centerX - 3, centerY - 8, centerX + 3, centerY - 6, color);
                graphics.fill(centerX - 3, centerY + 6, centerX + 3, centerY + 8, color);
            }
            case ENDER -> {
                graphics.fill(centerX - 6, centerY - 6, centerX + 7, centerY - 4, color);
                graphics.fill(centerX - 6, centerY + 4, centerX + 7, centerY + 6, color);
                graphics.fill(centerX - 6, centerY - 4, centerX - 4, centerY + 4, color);
                graphics.fill(centerX + 5, centerY - 4, centerX + 7, centerY + 4, color);
                graphics.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xCC2B162F);
            }
            case MAGMA -> {
                graphics.fill(centerX - 1, centerY - 8, centerX + 2, centerY - 5, color);
                graphics.fill(centerX - 3, centerY - 5, centerX + 4, centerY - 2, color);
                graphics.fill(centerX - 5, centerY - 2, centerX + 6, centerY + 3, color);
                graphics.fill(centerX - 3, centerY + 3, centerX + 4, centerY + 6, color);
                graphics.fill(centerX - 1, centerY + 6, centerX + 2, centerY + 9, color);
                graphics.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFE28A);
            }
            case ECHO -> {
                graphics.fill(centerX - 6, centerY - 7, centerX + 7, centerY - 5, color);
                graphics.fill(centerX - 6, centerY + 5, centerX + 7, centerY + 7, color);
                graphics.fill(centerX - 8, centerY - 3, centerX - 6, centerY + 3, color);
                graphics.fill(centerX + 6, centerY - 3, centerX + 8, centerY + 3, color);
                graphics.fill(centerX - 3, centerY - 4, centerX + 4, centerY + 5, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 85));
            }
            default -> {
                graphics.fill(centerX - 1, centerY - 8, centerX + 2, centerY + 9, color);
                graphics.fill(centerX - 3, centerY - 6, centerX + 4, centerY + 7, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 136));
                graphics.fill(centerX - 5, centerY - 3, centerX + 6, centerY + 4, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 68));
            }
        }
    }

    private static ResourceLocation lensIcon(LensKind kind) {
        String texture = switch (kind) {
            case CONVEX -> "convex";
            case CONCAVE -> "concave";
            case ENDER -> "ender_lens";
            case MAGMA -> "magmatic_lens";
            case ECHO -> "echo_lens";
        };
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/gui/lens/" + texture + ".png");
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

    private void drawMeterPlate(GuiGraphics graphics) {
        int x = leftPos + LensGrindingBenchLayout.RIGHT_ZONE_X;
        int y = topPos + LensGrindingBenchLayout.RIGHT_ZONE_Y;
        int width = LensGrindingBenchLayout.RIGHT_ZONE_WIDTH;
        int height = LensGrindingBenchLayout.RIGHT_ZONE_HEIGHT;
        int seamX = x + width / 2;

        graphics.fill(x + 4, y + 5, x + width - 4, y + height - 5, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 22));
        graphics.fill(seamX, y + 7, seamX + 1, y + height - 7, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 36));
        graphics.fill(x + 7, y + 4, x + width - 7, y + 5, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 58));
        graphics.fill(x + 5, y + height - 9, x + 7, y + height - 7, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
        graphics.fill(x + width - 7, y + height - 9, x + width - 5, y + height - 7, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 72));
    }

    private static void meterFrame(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.CHAMBER_SHADOW, 46));
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 105));
        outline(graphics, x + 1, y + 1, width - 2, height - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 92));
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 98));
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, 78));
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 82));
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 62));
        graphics.fill(x + 3, y + 4, x + width - 3, y + 5, ThermalSmelterUiSkin.withAlpha(accent, 42));
    }

    private static void drawQualitySeparators(GuiGraphics graphics, int x, int width, int coarseTop, int clearTop, int coarseHeight, int clearHeight, int preciseHeight) {
        if (coarseHeight > 0 && clearHeight + preciseHeight > 0) {
            graphics.fill(x + 1, coarseTop, x + width - 1, coarseTop + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 46));
            graphics.fill(x + 1, coarseTop + 1, x + width - 1, coarseTop + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 34));
        }

        if (clearHeight > 0 && preciseHeight > 0) {
            graphics.fill(x + 1, clearTop, x + width - 1, clearTop + 1, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 42));
            graphics.fill(x + 1, clearTop + 1, x + width - 1, clearTop + 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 34));
        }
    }

    private static void drawSideScaleTicks(GuiGraphics graphics, int x, int y, int width, int height, boolean leftSide) {
        for (int i = 0; i <= 4; i++) {
            int tickY = y + Math.round((height - 1) * i / 4.0F);
            int tickLength = i == 0 || i == 4
                    ? LensGrindingBenchLayout.METER_SCALE_TICK_LONG
                    : LensGrindingBenchLayout.METER_SCALE_TICK_SHORT;
            if (leftSide) {
                graphics.fill(x - 1 - tickLength, tickY, x - 1, tickY + 1, DETAIL_LINE);
            } else {
                graphics.fill(x + width + 1, tickY, x + width + 1 + tickLength, tickY + 1, DETAIL_LINE);
            }
        }
    }

    private static void drawSideScaleLabels(GuiGraphics graphics, Font font, String topText, String bottomText, int x, int topY, int bottomY, int width, boolean alignRight, int color) {
        drawAlignedText(graphics, font, topText, x, topY, width, 0.50F, color, alignRight);
        drawAlignedText(graphics, font, bottomText, x, bottomY, width, 0.50F, color, alignRight);
    }

    private static void drawAlignedText(GuiGraphics graphics, Font font, String text, int x, int y, int width, float scale, int color, boolean alignRight) {
        int textWidth = font.width(text);
        int drawX = alignRight ? Math.round(x + width - textWidth * scale) : x;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void drawDisk(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int radiusSquared = radius * radius;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radiusSquared) {
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    private static void drawRing(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness, int color) {
        int outerSquared = radius * radius;
        int inner = Math.max(0, radius - thickness);
        int innerSquared = inner * inner;

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSquared = dx * dx + dy * dy;
                if (distanceSquared <= outerSquared && distanceSquared >= innerSquared) {
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    private static void drawArcRing(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness, double sweepDegrees, int color) {
        if (sweepDegrees <= 0.0D) {
            return;
        }

        int outerSquared = radius * radius;
        int inner = Math.max(0, radius - thickness);
        int innerSquared = inner * inner;
        double sweep = Math.min(360.0D, sweepDegrees);

        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > outerSquared || distanceSquared < innerSquared) {
                    continue;
                }

                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0D;
                if (angle < 0.0D) {
                    angle += 360.0D;
                }

                if (angle <= sweep) {
                    graphics.fill(centerX + dx, centerY + dy, centerX + dx + 1, centerY + dy + 1, color);
                }
            }
        }
    }

    private static void slotFrame(GuiGraphics graphics, int x, int y, int color, boolean active) {
        graphics.fill(x, y, x + LensGrindingBenchLayout.SLOT_SIZE, y + LensGrindingBenchLayout.SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, LensGrindingBenchLayout.SLOT_SIZE, LensGrindingBenchLayout.SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + LensGrindingBenchLayout.SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + LensGrindingBenchLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + LensGrindingBenchLayout.SLOT_SIZE - 2, x + LensGrindingBenchLayout.SLOT_SIZE - 1, y + LensGrindingBenchLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + LensGrindingBenchLayout.SLOT_SIZE - 2, y + 1, x + LensGrindingBenchLayout.SLOT_SIZE - 1, y + LensGrindingBenchLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + LensGrindingBenchLayout.SLOT_SIZE - 2, y + LensGrindingBenchLayout.SLOT_SIZE - 2, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
        if (active) {
            graphics.fill(x + LensGrindingBenchLayout.SLOT_SIZE - 2, y + 2, x + LensGrindingBenchLayout.SLOT_SIZE - 1, y + LensGrindingBenchLayout.SLOT_SIZE - 2, ThermalSmelterUiSkin.withAlpha(color, 155));
            graphics.fill(x + 2, y + LensGrindingBenchLayout.SLOT_SIZE - 2, x + LensGrindingBenchLayout.SLOT_SIZE - 2, y + LensGrindingBenchLayout.SLOT_SIZE - 1, ThermalSmelterUiSkin.withAlpha(color, 90));
        }
    }

    private void drawIconButton(GuiGraphics graphics, int localX, int localY, boolean right, InteractionWeight weight) {
        int x = leftPos + localX;
        int y = topPos + localY;
        int color = weight.arrowColor();
        softButtonShell(graphics, x, y, LensGrindingBenchLayout.KIND_BUTTON_SIZE, LensGrindingBenchLayout.KIND_BUTTON_SIZE, weight);
        if (right) {
            pixelArrowRight(graphics, x + LensGrindingBenchLayout.BUTTON_ARROW_INSET, y + LensGrindingBenchLayout.BUTTON_ARROW_INSET, LensGrindingBenchLayout.BUTTON_ARROW_SIZE, LensGrindingBenchLayout.BUTTON_ARROW_SIZE, color);
        } else {
            pixelArrowLeft(graphics, x + LensGrindingBenchLayout.BUTTON_ARROW_INSET, y + LensGrindingBenchLayout.BUTTON_ARROW_INSET, LensGrindingBenchLayout.BUTTON_ARROW_SIZE, LensGrindingBenchLayout.BUTTON_ARROW_SIZE, color);
        }
    }

    private static void softButtonShell(GuiGraphics graphics, int x, int y, int width, int height, InteractionWeight weight) {
        graphics.fill(x, y, x + width, y + height, weight.fillColor());
        outline(graphics, x, y, width, height, weight.borderColor());
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, weight.highlightColor());
        if (weight != InteractionWeight.TERTIARY) {
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, weight.shadowColor());
        }
    }

    private static void blockedOverlay(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + LensGrindingBenchLayout.SLOT_SIZE, y + LensGrindingBenchLayout.SLOT_SIZE, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STATUS_INVALID, 72));
        for (int i = 3; i < LensGrindingBenchLayout.SLOT_SIZE - 3; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
            graphics.fill(x + LensGrindingBenchLayout.SLOT_SIZE - 1 - i, y + i, x + LensGrindingBenchLayout.SLOT_SIZE - i, y + i + 1, ThermalSmelterUiSkin.STATUS_INVALID);
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

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static int interpolateVertical(int top, int bottom, int value, int min, int max) {
        double ratio = (value - min) / (double) (max - min);
        ratio = Math.max(0.0, Math.min(1.0, ratio));
        return bottom - (int) Math.round((bottom - top) * ratio);
    }

    private enum InteractionWeight {
        PRIMARY(68, 118, 95, 82, 150),
        SECONDARY(38, 84, 66, 52, 128),
        TERTIARY(18, 46, 38, 0, 94),
        DISABLED(8, 20, 18, 0, 42);

        private final int fillAlpha;
        private final int borderAlpha;
        private final int highlightAlpha;
        private final int shadowAlpha;
        private final int arrowAlpha;

        InteractionWeight(int fillAlpha, int borderAlpha, int highlightAlpha, int shadowAlpha, int arrowAlpha) {
            this.fillAlpha = fillAlpha;
            this.borderAlpha = borderAlpha;
            this.highlightAlpha = highlightAlpha;
            this.shadowAlpha = shadowAlpha;
            this.arrowAlpha = arrowAlpha;
        }

        int fillColor() {
            return ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, fillAlpha);
        }

        int borderColor() {
            return ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, borderAlpha);
        }

        int highlightColor() {
            return ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, highlightAlpha);
        }

        int shadowColor() {
            return ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_SHADOW, shadowAlpha);
        }

        int arrowColor() {
            return ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, arrowAlpha);
        }
    }
}
