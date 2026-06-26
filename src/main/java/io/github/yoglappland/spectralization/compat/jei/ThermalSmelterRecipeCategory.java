package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
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

public final class ThermalSmelterRecipeCategory implements IRecipeCategory<ThermalSmelterRecipe> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 58;
    private static final int INPUT_X = 12;
    private static final int INPUT_SINGLE_Y = 20;
    private static final int INPUT_PAIR_Y = 8;
    private static final int ADDITIVE_Y = 32;
    private static final int OUTPUT_X = 120;
    private static final int OUTPUT_Y = 20;
    private static final int ARROW_X = 49;
    private static final int ARROW_Y = 24;
    private static final int ARROW_WIDTH = 50;
    private static final int ARROW_HEIGHT = 9;
    private static final int HEAT_STRIP_X = 56;
    private static final int HEAT_STRIP_Y = 42;
    private static final int HEAT_STRIP_SEGMENTS = 5;
    private static final int HEAT_STRIP_SEGMENT_WIDTH = 6;
    private static final int HEAT_STRIP_SEGMENT_GAP = 2;
    private static final int ITEM_SLOT_INSET = 1;

    private final IDrawable icon;

    public ThermalSmelterRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.THERMAL_SMELTER_ITEM.get()));
    }

    @Override
    public RecipeType<ThermalSmelterRecipe> getRecipeType() {
        return SpectralJeiPlugin.THERMAL_SMELTER;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.spectralization.thermal_smelter");
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
    public void setRecipe(IRecipeLayoutBuilder builder, ThermalSmelterRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT,
                        INPUT_X + ITEM_SLOT_INSET,
                        (recipe.consumesAdditive() ? INPUT_PAIR_Y : INPUT_SINGLE_Y) + ITEM_SLOT_INSET)
                .setSlotName("input")
                .addIngredients(recipe.ingredient());

        if (recipe.consumesAdditive()) {
            builder.addSlot(RecipeIngredientRole.INPUT,
                            INPUT_X + ITEM_SLOT_INSET,
                            ADDITIVE_Y + ITEM_SLOT_INSET)
                    .setSlotName("additive")
                    .addIngredients(recipe.additive());
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT,
                        OUTPUT_X + ITEM_SLOT_INSET,
                        OUTPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("output")
                .addItemStack(recipe.resultStack());

        builder.moveRecipeTransferButton(WIDTH - 18, 4);
    }

    @Override
    public void draw(
            ThermalSmelterRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        drawMiniPanel(graphics, recipe);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            ThermalSmelterRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, ARROW_X, ARROW_Y - 2, ARROW_WIDTH, ARROW_HEIGHT + 4)
                || inside(mouseX, mouseY, HEAT_STRIP_X, HEAT_STRIP_Y - 2, heatStripWidth(), 8)) {
            tooltip.add(tt("tooltip.heat_needed", recipe.minimumTemperature()));
            tooltip.add(tt("jei.heat_cost", recipe.heatCost()));
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, x, y, width, height);
    }

    private void drawMiniPanel(GuiGraphics graphics, ThermalSmelterRecipe recipe) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, INPUT_X, recipe.consumesAdditive() ? INPUT_PAIR_Y : INPUT_SINGLE_Y, ThermalSmelterUiSkin.HEAT);

        if (recipe.consumesAdditive()) {
            slotWell(graphics, INPUT_X, ADDITIVE_Y, ThermalSmelterUiSkin.OPTICAL);
        }

        slotWell(graphics, OUTPUT_X, OUTPUT_Y, ThermalSmelterUiSkin.PROGRESS);
        pixelArrowRight(graphics, ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 145));
        heatStrip(graphics, recipe);
    }

    private void fillPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, ThermalSmelterUiSkin.MACHINE_BG);
        outline(graphics, x, y, width, height, ThermalSmelterUiSkin.STRONG_BORDER);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 3, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, ThermalSmelterUiSkin.PANEL_HIGHLIGHT);
        graphics.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
        graphics.fill(x + width - 3, y + 1, x + width - 1, y + height - 1, ThermalSmelterUiSkin.PANEL_SHADOW);
    }

    private void slotWell(GuiGraphics graphics, int x, int y, int accent) {
        graphics.fill(x, y, x + 18, y + 18, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, 18, 18, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + 17, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 16, y + 1, x + 17, y + 17, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
        graphics.fill(x + 16, y + 3, x + 17, y + 15, ThermalSmelterUiSkin.withAlpha(accent, 155));
        graphics.fill(x + 3, y + 16, x + 16, y + 17, ThermalSmelterUiSkin.withAlpha(accent, 90));
    }

    private void heatStrip(GuiGraphics graphics, ThermalSmelterRecipe recipe) {
        int activeSegments = Math.max(1, Math.min(HEAT_STRIP_SEGMENTS, (int) Math.ceil(recipe.minimumTemperature() / 600.0)));

        for (int segment = 0; segment < HEAT_STRIP_SEGMENTS; segment++) {
            int x = HEAT_STRIP_X + segment * (HEAT_STRIP_SEGMENT_WIDTH + HEAT_STRIP_SEGMENT_GAP);
            int color = segment < activeSegments ? ThermalSmelterUiSkin.HEAT : ThermalSmelterUiSkin.EMPTY;
            graphics.fill(x, HEAT_STRIP_Y, x + HEAT_STRIP_SEGMENT_WIDTH, HEAT_STRIP_Y + 4, ThermalSmelterUiSkin.withAlpha(color, segment < activeSegments ? 175 : 70));
            outline(graphics, x, HEAT_STRIP_Y, HEAT_STRIP_SEGMENT_WIDTH, 4, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 110));
        }
    }

    private void pixelArrowRight(GuiGraphics graphics, int x, int y, int width, int height, int color) {
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

    private void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int heatStripWidth() {
        return HEAT_STRIP_SEGMENTS * HEAT_STRIP_SEGMENT_WIDTH
                + (HEAT_STRIP_SEGMENTS - 1) * HEAT_STRIP_SEGMENT_GAP;
    }

    private Component tt(String path, Object... args) {
        return Component.translatable("screen.spectralization.thermal_smelter." + path, args);
    }
}
