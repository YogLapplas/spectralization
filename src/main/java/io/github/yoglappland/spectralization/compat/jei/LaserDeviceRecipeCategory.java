package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.optics.OpticalMaterialProfiles;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class LaserDeviceRecipeCategory implements IRecipeCategory<LaserDeviceRecipe> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 124;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int DEVICE_X = 12;
    private static final int DEVICE_Y = 21;
    private static final int LABEL_X = 42;
    private static final int BAR_X = 100;
    private static final int ROW_Y = 8;
    private static final int ROW_HEIGHT = 13;
    private static final int ROW_HOVER_HEIGHT = 12;
    private static final int BAR_SEGMENTS = 10;
    private static final int BAR_SEGMENT_WIDTH = 5;
    private static final int BAR_SEGMENT_HEIGHT = 6;
    private static final int BAR_SEGMENT_GAP = 2;
    private static final int CHART_X = 10;
    private static final int CHART_Y = 60;
    private static final int CHART_WIDTH = 156;
    private static final int CHART_HEIGHT = 52;
    private static final int CHART_LABEL_Y = 49;
    private static final int PUMP_ROW = 0;
    private static final int SATURATION_ROW = 1;
    private static final int HANDLING_ROW = 2;
    private static final int PUMP_COLOR = 0xFFF0B14A;
    private static final int SATURATION_COLOR = 0xFF57B8E6;
    private static final int HANDLING_COLOR = 0xFFE65E7C;
    private static final int GAIN_CURVE_COLOR = 0xFF111111;

    private final IDrawable icon;
    private final double maxGainPerPumpUnit;
    private final double maxSaturationPower;
    private final double maxHandlingLimit;

    public LaserDeviceRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.RUBY_BLOCK_ITEM.get()));
        List<LaserDeviceRecipe> recipes = LaserDeviceRecipe.recipes();
        this.maxGainPerPumpUnit = max(recipes, LaserDeviceRecipe::gainPerPumpUnit);
        this.maxSaturationPower = max(recipes, LaserDeviceRecipe::saturationPower);
        this.maxHandlingLimit = max(recipes, LaserDeviceRecipe::handlingLimit);
    }

    @Override
    public RecipeType<LaserDeviceRecipe> getRecipeType() {
        return SpectralJeiPlugin.GAIN_MATERIAL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.spectralization.laser");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, LaserDeviceRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, DEVICE_X + ITEM_SLOT_INSET, DEVICE_Y + ITEM_SLOT_INSET)
                .setSlotName("material")
                .addItemStack(recipe.stack());
    }

    @Override
    public void draw(
            LaserDeviceRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, DEVICE_X, DEVICE_Y, 0xFFE65E7C);
        drawStatBar(
                graphics,
                Component.translatable("jei.spectralization.gain_material.pump_response"),
                recipe.gainPerPumpUnit(),
                maxGainPerPumpUnit,
                PUMP_ROW,
                PUMP_COLOR
        );
        drawStatBar(
                graphics,
                Component.translatable("jei.spectralization.gain_material.saturation"),
                recipe.saturationPower(),
                maxSaturationPower,
                SATURATION_ROW,
                SATURATION_COLOR
        );
        drawStatBar(
                graphics,
                Component.translatable("jei.spectralization.gain_material.handling"),
                recipe.handlingLimit(),
                maxHandlingLimit,
                HANDLING_ROW,
                HANDLING_COLOR
        );
        drawGainCurve(graphics, recipe, mouseX, mouseY);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            LaserDeviceRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (insideRow(mouseX, mouseY, PUMP_ROW)) {
            tooltip.add(Component.translatable("jei.spectralization.gain_material.pump_response"));
            tooltip.add(Component.translatable(
                    "jei.spectralization.gain_material.tooltip.gain_per_pu",
                    gainSlopeText(recipe.gainPerPumpUnit())
            ));
            return;
        }

        if (insideRow(mouseX, mouseY, SATURATION_ROW)) {
            tooltip.add(Component.translatable("jei.spectralization.gain_material.saturation"));
            tooltip.add(Component.translatable(
                    "jei.spectralization.gain_material.tooltip.saturation_power",
                    powerText(recipe.saturationPower())
            ));
            return;
        }

        if (insideRow(mouseX, mouseY, HANDLING_ROW)) {
            tooltip.add(Component.translatable("jei.spectralization.gain_material.handling"));
            tooltip.add(Component.translatable(
                    "jei.spectralization.gain_material.tooltip.handling_limit",
                    powerText(recipe.handlingLimit())
            ));
            return;
        }

        if (insideChart(mouseX, mouseY)) {
            SpectralParameterChart.Hover hover = SpectralParameterChart.hoverAt(
                    mouseX,
                    CHART_X,
                    CHART_Y,
                    CHART_WIDTH,
                    CHART_HEIGHT,
                    frequency -> OpticalMaterialProfiles.spectralGainPerPumpUnitFor(recipe.state(), frequency)
            );
            tooltip.add(Component.translatable("jei.spectralization.gain_material.gain_curve"));
            tooltip.add(Component.translatable(
                    "jei.spectralization.chart.tooltip.frequency",
                    SpectralParameterChart.frequencyText(hover.frequency())
            ));
            tooltip.add(Component.translatable(
                    "jei.spectralization.chart.tooltip.gain_per_pu",
                    SpectralParameterChart.compactValue(hover.value())
            ));
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void drawStatBar(
            GuiGraphics graphics,
            Component label,
            double value,
            double maxValue,
            int row,
            int accent
    ) {
        Font font = Minecraft.getInstance().font;
        int y = ROW_Y + row * ROW_HEIGHT;
        graphics.drawString(font, label, LABEL_X, y, SpectralJeiUi.TEXT, false);

        int activeSegments = filledSegments(value, maxValue);

        for (int segment = 0; segment < BAR_SEGMENTS; segment++) {
            int x = BAR_X + segment * (BAR_SEGMENT_WIDTH + BAR_SEGMENT_GAP);
            boolean active = segment < activeSegments;
            int fill = active ? accent : ThermalSmelterUiSkin.EMPTY;
            int alpha = active ? 205 : 58;
            graphics.fill(x, y + 2, x + BAR_SEGMENT_WIDTH, y + 2 + BAR_SEGMENT_HEIGHT,
                    ThermalSmelterUiSkin.withAlpha(fill, alpha));
            outline(
                    graphics,
                    x,
                    y + 2,
                    BAR_SEGMENT_WIDTH,
                    BAR_SEGMENT_HEIGHT,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, active ? 145 : 80)
            );
        }
    }

    private void drawGainCurve(GuiGraphics graphics, LaserDeviceRecipe recipe, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                Component.translatable("jei.spectralization.gain_material.gain_curve"),
                CHART_X,
                CHART_LABEL_Y,
                SpectralJeiUi.TEXT,
                false
        );
        SpectralParameterChart.draw(
                graphics,
                CHART_X,
                CHART_Y,
                CHART_WIDTH,
                CHART_HEIGHT,
                frequency -> OpticalMaterialProfiles.spectralGainPerPumpUnitFor(recipe.state(), frequency),
                maxGainPerPumpUnit,
                GAIN_CURVE_COLOR,
                mouseX,
                mouseY
        );
    }

    private boolean insideRow(double mouseX, double mouseY, int row) {
        int y = ROW_Y + row * ROW_HEIGHT - 2;
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, LABEL_X, y, WIDTH - LABEL_X - 8, ROW_HOVER_HEIGHT);
    }

    private static boolean insideChart(double mouseX, double mouseY) {
        return SpectralParameterChart.inside(mouseX, mouseY, CHART_X, CHART_Y, CHART_WIDTH, CHART_HEIGHT);
    }

    private static int filledSegments(double value, double maxValue) {
        if (!Double.isFinite(value) || value <= 0.0D || !Double.isFinite(maxValue) || maxValue <= 0.0D) {
            return 0;
        }

        return Math.max(1, Math.min(BAR_SEGMENTS, (int) Math.ceil(value / maxValue * BAR_SEGMENTS)));
    }

    private static double max(List<LaserDeviceRecipe> recipes, ToDoubleFunction<LaserDeviceRecipe> value) {
        double max = 0.0D;

        for (LaserDeviceRecipe recipe : recipes) {
            double current = value.applyAsDouble(recipe);

            if (Double.isFinite(current) && current > max) {
                max = current;
            }
        }

        return max;
    }

    private static Component gainSlopeText(double gainPerPumpUnit) {
        if (!Double.isFinite(gainPerPumpUnit) || gainPerPumpUnit <= 0.0D) {
            return Component.translatable("jei.spectralization.optical_device.not_configured");
        }

        return Component.literal(String.format(Locale.ROOT, "+%.4fx/PU", gainPerPumpUnit));
    }

    private static Component powerText(double power) {
        if (!Double.isFinite(power) || power <= 0.0D) {
            return Component.translatable("jei.spectralization.optical_device.not_configured");
        }

        return Component.translatable(
                "jei.spectralization.optical_device.power_sp",
                String.format(Locale.ROOT, "%.0f", power)
        );
    }

    private static void fillPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private static void slotWell(GuiGraphics graphics, int x, int y, int accent) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + SLOT_SIZE - 2, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + SLOT_SIZE - 2, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
        graphics.fill(x + SLOT_SIZE - 2, y + 3, x + SLOT_SIZE - 1, y + SLOT_SIZE - 3,
                ThermalSmelterUiSkin.withAlpha(accent, 140));
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
