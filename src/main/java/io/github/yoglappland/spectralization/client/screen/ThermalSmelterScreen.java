package io.github.yoglappland.spectralization.client.screen;

import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SpectralMachineScreen;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin.ProcessState;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin.ProcessView;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.menu.ThermalSmelterLayout;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ThermalSmelterScreen extends SpectralMachineScreen<ThermalSmelterMenu> {
    public ThermalSmelterScreen(ThermalSmelterMenu menu, Inventory playerInventory, Component title) {
        super(
                menu,
                playerInventory,
                title,
                "thermal_smelter",
                ThermalSmelterLayout.IMAGE_WIDTH,
                ThermalSmelterLayout.IMAGE_HEIGHT,
                ThermalSmelterLayout.INVENTORY_LABEL_X,
                ThermalSmelterLayout.INVENTORY_LABEL_Y
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderMachineTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        ThermalSmelterUiSkin.drawScreenShell(graphics, leftPos, topPos, imageWidth, imageHeight);
        ThermalSmelterUiSkin.drawProcess(
                graphics,
                font,
                leftPos + ThermalSmelterLayout.PROCESS_X,
                topPos + ThermalSmelterLayout.PROCESS_Y,
                processView()
        );
        ThermalSmelterUiSkin.drawPlayerInventory(graphics, font, leftPos, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    private ProcessView processView() {
        Optional<ThermalSmelterRecipe> recipe = menu.activeRecipe();
        boolean additiveActive = recipe
                .map(ThermalSmelterRecipe::consumesAdditive)
                .orElse(menu.inputStack().isEmpty() || !menu.additiveStack().isEmpty());
        boolean highTemperature = recipe
                .map(activeRecipe -> activeRecipe.minimumTemperature() >= 2000)
                .orElse(heatRatio() > 0.65);

        return new ProcessView(
                heatRatio(),
                progressRatio(),
                opticalRatio(),
                state(),
                additiveActive,
                highTemperature,
                data(ThermalSmelterBlockEntity.DATA_TEMPERATURE),
                progressPercent(),
                spPercent(),
                parallelCount()
        );
    }

    private void renderMachineTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hoveredSlot != null
                && hoveredSlot.hasItem()
                && (!isOutputSlotArea(mouseX, mouseY) || state() != ProcessState.OUTPUT_BLOCKED)) {
            return;
        }

        List<Component> tooltip = tooltipAt(mouseX, mouseY);

        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        if (insideRecipeClickArea(mouseX, mouseY)) {
            return List.of();
        }

        if (insideSlot(mouseX, mouseY, ThermalSmelterLayout.SLOT_INPUT_X, ThermalSmelterLayout.SLOT_INPUT_Y)) {
            return List.of(tt("tooltip.input"));
        }

        if (insideSlot(mouseX, mouseY, ThermalSmelterLayout.SLOT_ADDITIVE_X, ThermalSmelterLayout.SLOT_ADDITIVE_Y)) {
            return List.of(tt("tooltip.additive"));
        }

        if (isOutputSlotArea(mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(tt("tooltip.output"));
            if (state() == ProcessState.OUTPUT_BLOCKED) {
                tooltip.add(tt("tooltip.clear_output"));
            }
            return tooltip;
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_SP_BAR_X - 2,
                ThermalSmelterLayout.PROCESS_SP_BAR_Y - 2,
                ThermalSmelterLayout.PROCESS_SP_BAR_WIDTH + 4,
                ThermalSmelterLayout.PROCESS_SP_BAR_HEIGHT + 4)) {
            return opticalTooltip();
        }

        if (insideStage(mouseX, mouseY, ThermalSmelterLayout.PROCESS_STAGE_X1)) {
            return inputStageTooltip();
        }

        if (insideStage(mouseX, mouseY, ThermalSmelterLayout.PROCESS_STAGE_X2)) {
            return heatStageTooltip();
        }

        if (insideStage(mouseX, mouseY, ThermalSmelterLayout.PROCESS_STAGE_X3)) {
            return outputStageTooltip();
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_TEMP_BOX_X,
                ThermalSmelterLayout.PROCESS_TEMP_BOX_Y,
                ThermalSmelterLayout.PROCESS_TEMP_BOX_WIDTH,
                ThermalSmelterLayout.PROCESS_TEMP_BOX_HEIGHT)) {
            return temperatureTooltip();
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_PARALLEL_GRID_X - 2,
                ThermalSmelterLayout.PROCESS_PARALLEL_GRID_Y - 2,
                2 * ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_GAP + 4,
                2 * ThermalSmelterLayout.PROCESS_PARALLEL_GRID_SIZE + ThermalSmelterLayout.PROCESS_PARALLEL_GRID_GAP + 4)) {
            return List.of(tt("tooltip.parallel_value", parallelCount()));
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_X - 3,
                ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_Y - 2,
                ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_WIDTH + 6,
                ThermalSmelterLayout.PROCESS_STATUS_LABEL_Y - ThermalSmelterLayout.PROCESS_STATUS_INDICATOR_Y + 18)) {
            return stateTooltip();
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_PROGRESS_X - 2,
                ThermalSmelterLayout.PROCESS_PROGRESS_Y - 2,
                progressSegmentsWidth() + 4,
                ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_HEIGHT + 4)) {
            return List.of(tt("tooltip.progress_value", progressPercent()));
        }

        if (insideProcess(mouseX, mouseY,
                ThermalSmelterLayout.PROCESS_CHAMBER_X,
                ThermalSmelterLayout.PROCESS_CHAMBER_Y,
                ThermalSmelterLayout.PROCESS_CHAMBER_WIDTH,
                ThermalSmelterLayout.PROCESS_CHAMBER_HEIGHT)) {
            return chamberTooltip();
        }

        return List.of();
    }

    private List<Component> temperatureTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.heat_value", data(ThermalSmelterBlockEntity.DATA_TEMPERATURE)));
        menu.activeRecipe().ifPresent(recipe -> tooltip.add(tt("tooltip.heat_needed", recipe.minimumTemperature())));
        return tooltip;
    }

    private List<Component> opticalTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.sp_value", spPercent()));
        tooltip.add(tt("tooltip.optical_value", formatPower()));

        if (spPercent() <= 0) {
            tooltip.add(tt("tooltip.no_optical"));
        }

        return trimTooltip(tooltip);
    }

    private List<Component> inputStageTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.stage_input"));

        if (menu.inputStack().isEmpty()) {
            tooltip.add(tt("tooltip.empty"));
        } else if (menu.activeRecipe().isEmpty()) {
            tooltip.add(tt("tooltip.invalid_recipe"));
        } else {
            tooltip.add(tt("tooltip.stage_input_ready"));
        }

        return trimTooltip(tooltip);
    }

    private List<Component> heatStageTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.stage_heat"));
        tooltip.add(tt("tooltip.heat_value", data(ThermalSmelterBlockEntity.DATA_TEMPERATURE)));
        tooltip.add(tt("tooltip.sp_value", spPercent()));

        Optional<ThermalSmelterRecipe> recipe = menu.activeRecipe();
        if (recipe.isPresent() && data(ThermalSmelterBlockEntity.DATA_TEMPERATURE) < recipe.get().minimumTemperature()) {
            tooltip.add(tt("tooltip.heat_needed", recipe.get().minimumTemperature()));
        }

        return trimTooltip(tooltip);
    }

    private List<Component> outputStageTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(tt("tooltip.stage_output"));
        tooltip.add(tt("tooltip.progress_value", progressPercent()));

        if (state() == ProcessState.OUTPUT_BLOCKED) {
            tooltip.add(tt("tooltip.clear_output"));
        } else {
            tooltip.add(tt("status." + stateKey(state())));
        }

        return trimTooltip(tooltip);
    }

    private List<Component> stateTooltip() {
        List<Component> tooltip = new ArrayList<>();
        ProcessState state = state();
        tooltip.add(tt("status." + stateKey(state)));

        switch (state) {
            case EMPTY -> tooltip.add(tt("tooltip.empty"));
            case READY -> tooltip.add(tt("tooltip.ready"));
            case PENDING -> addPendingReason(tooltip);
            case INVALID -> addInvalidReason(tooltip);
            case OUTPUT_BLOCKED -> tooltip.add(tt("tooltip.clear_output"));
        }

        return tooltip;
    }

    private List<Component> chamberTooltip() {
        return trimTooltip(stateTooltip());
    }

    private void addPendingReason(List<Component> tooltip) {
        Optional<ThermalSmelterRecipe> recipe = menu.activeRecipe();

        if (recipe.isPresent()
                && data(ThermalSmelterBlockEntity.DATA_TEMPERATURE) < recipe.get().minimumTemperature()) {
            tooltip.add(tt("tooltip.wait_heat"));
            tooltip.add(tt("tooltip.heat_needed", recipe.get().minimumTemperature()));
            return;
        }

        tooltip.add(tt("tooltip.progress_value", progressPercent()));
    }

    private void addInvalidReason(List<Component> tooltip) {
        if (!ThermalSmelterRecipe.isProcessable(menu.inputStack())) {
            tooltip.add(tt("tooltip.invalid_recipe"));
            return;
        }

        tooltip.add(tt("tooltip.additive_missing"));
    }

    private List<Component> trimTooltip(List<Component> tooltip) {
        return tooltip.size() <= 4 ? tooltip : tooltip.subList(0, 4);
    }

    private boolean isOutputSlotArea(double mouseX, double mouseY) {
        return insideSlot(mouseX, mouseY, ThermalSmelterLayout.SLOT_OUTPUT_X, ThermalSmelterLayout.SLOT_OUTPUT_Y)
                || insideSlot(mouseX, mouseY, ThermalSmelterLayout.SLOT_OUTPUT_SECOND_X, ThermalSmelterLayout.SLOT_OUTPUT_SECOND_Y);
    }

    private boolean insideRecipeClickArea(double mouseX, double mouseY) {
        return insideProcess(
                mouseX,
                mouseY,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_X,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_Y,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_WIDTH,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_HEIGHT
        );
    }

    private boolean insideStage(double mouseX, double mouseY, int x) {
        return insideProcess(
                mouseX,
                mouseY,
                x - 2,
                ThermalSmelterLayout.PROCESS_STAGE_Y - 2,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE + 4,
                ThermalSmelterLayout.PROCESS_STAGE_SIZE + 4
        );
    }

    private int progressSegmentsWidth() {
        return ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENTS * ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_WIDTH
                + (ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENTS - 1) * ThermalSmelterLayout.PROCESS_PROGRESS_SEGMENT_GAP;
    }

    private boolean insideSlot(double mouseX, double mouseY, int x, int y) {
        return insideLocal(
                mouseX,
                mouseY,
                x - 2,
                y - 2,
                ThermalSmelterLayout.SLOT_SIZE + 4,
                ThermalSmelterLayout.SLOT_SIZE + 4
        );
    }

    private boolean insideProcess(double mouseX, double mouseY, int x, int y, int width, int height) {
        return insideLocal(
                mouseX,
                mouseY,
                ThermalSmelterLayout.PROCESS_X + x,
                ThermalSmelterLayout.PROCESS_Y + y,
                width,
                height
        );
    }

    private boolean insideLocal(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX - leftPos, mouseY - topPos, x, y, width, height);
    }

    private ProcessState state() {
        if (menu.inputStack().isEmpty()) {
            return ProcessState.EMPTY;
        }

        Optional<ThermalSmelterRecipe> recipe = menu.activeRecipe();

        if (recipe.isEmpty()) {
            return ProcessState.INVALID;
        }

        if (menu.outputBlocked()) {
            return ProcessState.OUTPUT_BLOCKED;
        }

        if (data(ThermalSmelterBlockEntity.DATA_PROGRESS) > 0
                || data(ThermalSmelterBlockEntity.DATA_TEMPERATURE) < recipe.get().minimumTemperature()) {
            return ProcessState.PENDING;
        }

        return ProcessState.READY;
    }

    private String stateKey(ProcessState state) {
        return switch (state) {
            case EMPTY -> "empty";
            case READY -> "ready";
            case PENDING -> "pending";
            case INVALID -> "invalid";
            case OUTPUT_BLOCKED -> "output_blocked";
        };
    }

    private int progressPercent() {
        return (int) Math.round(progressRatio() * 100.0);
    }

    private String formatPower() {
        return String.format(Locale.ROOT, "%.2f", data(ThermalSmelterBlockEntity.DATA_HEAT_POWER_X100) / 100.0);
    }

    private int spPercent() {
        return (int) Math.round(opticalRatio() * 100.0);
    }

    private int parallelCount() {
        return Math.max(1, data(ThermalSmelterBlockEntity.DATA_PARALLEL_COUNT));
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
        return ratio(heatPowerX100, ThermalSmelterBlockEntity.FULL_SP_HEAT_POWER_X100);
    }

    private double ratio(double value, double max) {
        if (!Double.isFinite(value) || !Double.isFinite(max) || max <= 0.0) {
            return 0.0;
        }

        return Math.max(0.0, Math.min(1.0, value / max));
    }

    private int data(int index) {
        return menu.getData(index);
    }

    private Component tt(String path, Object... args) {
        return Component.translatable("screen.spectralization.thermal_smelter." + path, args);
    }
}
