package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.client.gui.SolarDopingPieChart;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class SolarDopingRecipeCategory implements IRecipeCategory<SolarDopingRecipe> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 66;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int INPUT_X = 14;
    private static final int INPUT_Y = 24;
    private static final int OUTPUT_X = 118;
    private static final int OUTPUT_Y = 24;
    private static final int PIE_CENTER_X = 75;
    private static final int PIE_CENTER_Y = 28;
    private static final int PIE_RADIUS = 13;
    private static final int PIE_INNER_RADIUS = 5;
    private static final int STRIP_X = 55;
    private static final int STRIP_Y = 50;
    private static final int STRIP_WIDTH = 40;
    private static final int STRIP_HEIGHT = 4;

    private final IDrawable icon;

    public SolarDopingRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.SOLAR_DOPING_CHAMBER_ITEM.get()));
    }

    @Override
    public RecipeType<SolarDopingRecipe> getRecipeType() {
        return SpectralJeiPlugin.SOLAR_DOPING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.spectralization.solar_doping_chamber");
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
    public void setRecipe(IRecipeLayoutBuilder builder, SolarDopingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + ITEM_SLOT_INSET, INPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("input")
                .addItemStack(new ItemStack(recipe.input()))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(tt("tooltip.process")));
        var outputSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + ITEM_SLOT_INSET, OUTPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("output")
                .addItemStacks(recipe.resultStacks());
        if (recipe.hasRandomResults()) {
            outputSlot.addRichTooltipCallback((view, tooltip) -> {
                tooltip.add(tt("jei.random_outputs"));
                int totalWeight = recipe.totalResultWeight();
                for (SolarDopingRecipe.WeightedResult result : recipe.results()) {
                    tooltip.add(Component.literal(percentText(result.weight() * 100.0 / totalWeight) + " ")
                            .append(result.resultStack().getHoverName()));
                }
            });
        }
        builder.moveRecipeTransferButton(WIDTH - 18, 3);
    }

    @Override
    public void draw(
            SolarDopingRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, INPUT_X, INPUT_Y, recipe.accentColor());
        slotWell(graphics, OUTPUT_X, OUTPUT_Y, ThermalSmelterUiSkin.OPTICAL);
        drawPie(graphics, recipe);
        energyStrip(graphics);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            SolarDopingRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, PIE_CENTER_X - PIE_RADIUS - 2, PIE_CENTER_Y - PIE_RADIUS - 2,
                PIE_RADIUS * 2 + 4, PIE_RADIUS * 2 + 4)) {
            if (recipe.hasRandomResults()) {
                tooltip.add(tt("jei.random_outputs"));
                SolarDopingPieChart.ratioTooltipLines(recipe).forEach(tooltip::add);
            }
            tooltip.add(tt("jei.average_seconds", secondsText(recipe.expectedTicks(1.0, 1.0))));
        } else if (inside(mouseX, mouseY, STRIP_X, STRIP_Y - 2, STRIP_WIDTH, 8)) {
            tooltip.add(tt("jei.energy_per_tick", SolarDopingChamberBlockEntity.ENERGY_PER_TICK * 20));
            tooltip.add(tt("jei.average_seconds", secondsText(recipe.expectedTicks(1.0, 1.0))));
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void drawPie(GuiGraphics graphics, SolarDopingRecipe recipe) {
        SolarDopingPieChart.draw(
                graphics,
                PIE_CENTER_X,
                PIE_CENTER_Y,
                PIE_RADIUS,
                PIE_INNER_RADIUS,
                recipe,
                recipe.accentColor(),
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 82)
        );
    }

    private static void energyStrip(GuiGraphics graphics) {
        graphics.fill(STRIP_X, STRIP_Y, STRIP_X + STRIP_WIDTH, STRIP_Y + STRIP_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.EMPTY, 74));
        int lit = STRIP_WIDTH / 2;
        graphics.fill(STRIP_X, STRIP_Y, STRIP_X + lit, STRIP_Y + STRIP_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.OPTICAL, 150));
        outline(graphics, STRIP_X, STRIP_Y, STRIP_WIDTH, STRIP_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 110));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, x, y, width, height);
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

    private static Component tt(String path, Object... args) {
        return Component.translatable("screen.spectralization.solar_doping_chamber." + path, args);
    }

    private static String secondsText(int ticks) {
        double seconds = Math.max(0, ticks) / 20.0;
        if (seconds >= 10.0) {
            return String.valueOf(Math.round(seconds));
        }

        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static String percentText(double percent) {
        if (Math.abs(percent - Math.rint(percent)) < 0.01) {
            return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
        }

        return String.format(java.util.Locale.ROOT, "%.1f%%", percent);
    }
}
