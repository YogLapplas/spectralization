package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.BasicLithographyRecipe;
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

public final class BasicLithographyRecipeCategory implements IRecipeCategory<BasicLithographyRecipe> {
    private static final int WIDTH = 164;
    private static final int HEIGHT = 72;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int ITEM_INPUT_X = 12;
    private static final int ITEM_INPUT_Y = 25;
    private static final int ITEM_SLOT_GAP = 4;
    private static final int TEMPLATE_X = 72;
    private static final int TEMPLATE_Y = 9;
    private static final int ARROW_X = 61;
    private static final int ARROW_Y = 36;
    private static final int ARROW_WIDTH = 44;
    private static final int ARROW_HEIGHT = 9;
    private static final int OUTPUT_X = 132;
    private static final int OUTPUT_Y = 27;
    private static final int ENERGY_STRIP_X = 60;
    private static final int ENERGY_STRIP_Y = 55;
    private static final int ENERGY_STRIP_SEGMENTS = 5;
    private static final int ENERGY_STRIP_SEGMENT_WIDTH = 7;
    private static final int ENERGY_STRIP_SEGMENT_GAP = 2;

    private final IDrawable icon;

    public BasicLithographyRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.BASIC_LITHOGRAPHY_MACHINE_ITEM.get()));
    }

    @Override
    public RecipeType<BasicLithographyRecipe> getRecipeType() {
        return SpectralJeiPlugin.BASIC_LITHOGRAPHY;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.spectralization.basic_lithography_machine");
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
    public void setRecipe(IRecipeLayoutBuilder builder, BasicLithographyRecipe recipe, IFocusGroup focuses) {
        int index = 0;

        for (BasicLithographyRecipe.ItemCost cost : recipe.itemCosts()) {
            int x = ITEM_INPUT_X + (index % 2) * (SLOT_SIZE + ITEM_SLOT_GAP);
            int y = ITEM_INPUT_Y + (index / 2) * (SLOT_SIZE + ITEM_SLOT_GAP);
            builder.addSlot(RecipeIngredientRole.INPUT, x + ITEM_SLOT_INSET, y + ITEM_SLOT_INSET)
                    .setSlotName("item_" + index)
                    .addItemStack(new ItemStack(cost.item(), cost.count()));
            index++;
        }

        if (recipe.usesTemplate() && recipe.templateItem() != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, TEMPLATE_X + ITEM_SLOT_INSET, TEMPLATE_Y + ITEM_SLOT_INSET)
                    .setSlotName("template")
                    .addItemStack(new ItemStack(recipe.templateItem()));
        }

        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + ITEM_SLOT_INSET, OUTPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("output")
                .addItemStack(recipe.resultStack());

        builder.moveRecipeTransferButton(WIDTH - 18, 4);
    }

    @Override
    public void draw(
            BasicLithographyRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);

        for (int index = 0; index < recipe.itemCosts().size(); index++) {
            int x = ITEM_INPUT_X + (index % 2) * (SLOT_SIZE + ITEM_SLOT_GAP);
            int y = ITEM_INPUT_Y + (index / 2) * (SLOT_SIZE + ITEM_SLOT_GAP);
            slotWell(graphics, x, y, ThermalSmelterUiSkin.PROGRESS);
        }

        if (recipe.usesTemplate()) {
            slotWell(graphics, TEMPLATE_X, TEMPLATE_Y, ThermalSmelterUiSkin.withAlpha(0xFF9FE7DF, 255));
        }

        slotWell(graphics, OUTPUT_X, OUTPUT_Y, ThermalSmelterUiSkin.OPTICAL);
        pixelArrowRight(graphics, ARROW_X, ARROW_Y, ARROW_WIDTH, ARROW_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.STRONG_BORDER, 155));
        energyStrip(graphics, recipe);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            BasicLithographyRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, ARROW_X, ARROW_Y - 2, ARROW_WIDTH, ARROW_HEIGHT + 4)
                || inside(mouseX, mouseY, ENERGY_STRIP_X, ENERGY_STRIP_Y - 2, energyStripWidth(), 8)) {
            tooltip.add(tt("jei.energy_per_tick", BasicLithographyMachineBlockEntity.ENERGY_PER_TICK));
            tooltip.add(tt("jei.process_ticks", recipe.processTicks()));
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, x, y, width, height);
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
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, ThermalSmelterUiSkin.SLOT_BG);
        outline(graphics, x, y, SLOT_SIZE, SLOT_SIZE, ThermalSmelterUiSkin.BORDER);
        graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + 2, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + 1, x + 2, y + SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_SHADOW);
        graphics.fill(x + 1, y + SLOT_SIZE - 2, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + SLOT_SIZE - 2, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, ThermalSmelterUiSkin.SLOT_HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 2,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.SLOT_HIGHLIGHT, 36));
        graphics.fill(x + SLOT_SIZE - 2, y + 3, x + SLOT_SIZE - 1, y + SLOT_SIZE - 3,
                ThermalSmelterUiSkin.withAlpha(accent, 140));
        graphics.fill(x + 3, y + SLOT_SIZE - 2, x + SLOT_SIZE - 2, y + SLOT_SIZE - 1,
                ThermalSmelterUiSkin.withAlpha(accent, 86));
    }

    private void energyStrip(GuiGraphics graphics, BasicLithographyRecipe recipe) {
        int activeSegments = Math.max(1, Math.min(ENERGY_STRIP_SEGMENTS, (int) Math.ceil(recipe.processTicks() / 30.0)));

        for (int segment = 0; segment < ENERGY_STRIP_SEGMENTS; segment++) {
            int x = ENERGY_STRIP_X + segment * (ENERGY_STRIP_SEGMENT_WIDTH + ENERGY_STRIP_SEGMENT_GAP);
            int color = segment < activeSegments ? ThermalSmelterUiSkin.OPTICAL : ThermalSmelterUiSkin.EMPTY;
            graphics.fill(x, ENERGY_STRIP_Y, x + ENERGY_STRIP_SEGMENT_WIDTH, ENERGY_STRIP_Y + 4,
                    ThermalSmelterUiSkin.withAlpha(color, segment < activeSegments ? 170 : 70));
            outline(graphics, x, ENERGY_STRIP_Y, ENERGY_STRIP_SEGMENT_WIDTH, 4,
                    ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.BORDER, 110));
        }
    }

    private void pixelArrowRight(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int centerY = y + height / 2;
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
            int halfHeight = Math.round(distanceToTip * (height / 2.0F) / (float) Math.max(1, headWidth - 1));
            graphics.fill(arrowX, centerY - halfHeight, arrowX + 1, centerY + halfHeight + 1, color);
        }
    }

    private void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private int energyStripWidth() {
        return ENERGY_STRIP_SEGMENTS * ENERGY_STRIP_SEGMENT_WIDTH
                + (ENERGY_STRIP_SEGMENTS - 1) * ENERGY_STRIP_SEGMENT_GAP;
    }

    private Component tt(String path, Object... args) {
        return Component.translatable("screen.spectralization.basic_lithography_machine." + path, args);
    }
}
