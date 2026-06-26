package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.FiberDrawingRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
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

public final class FiberDrawingRecipeCategory implements IRecipeCategory<FiberDrawingRecipe> {
    private static final int WIDTH = 156;
    private static final int HEIGHT = 70;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int MATERIAL_X = 12;
    private static final int MATERIAL_Y = 25;
    private static final int MOLD_INPUT_X = 69;
    private static final int MOLD_INPUT_Y = 5;
    private static final int OUTPUT_X = 126;
    private static final int OUTPUT_Y = 25;
    private static final int DRAW_X = 42;
    private static final int DRAW_Y = 23;
    private static final int DRAW_WIDTH = 72;
    private static final int DRAW_HEIGHT = 24;

    private final IDrawable icon;

    public FiberDrawingRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.FIBER_DRAWING_MACHINE_ITEM.get()));
    }

    @Override
    public RecipeType<FiberDrawingRecipe> getRecipeType() {
        return SpectralJeiPlugin.FIBER_DRAWING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("container.spectralization.fiber_drawing_machine");
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
    public void setRecipe(IRecipeLayoutBuilder builder, FiberDrawingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, MATERIAL_X + ITEM_SLOT_INSET, MATERIAL_Y + ITEM_SLOT_INSET)
                .setSlotName("material")
                .addItemStack(recipe.materialItem());
        builder.addSlot(RecipeIngredientRole.INPUT, MOLD_INPUT_X + ITEM_SLOT_INSET, MOLD_INPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("mold_input")
                .addItemStack(recipe.moldItem());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + ITEM_SLOT_INSET, OUTPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("output")
                .addItemStack(recipe.resultItem());
    }

    @Override
    public void draw(
            FiberDrawingRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, MATERIAL_X, MATERIAL_Y, 0xFF9FE7DF);
        slotWell(graphics, MOLD_INPUT_X, MOLD_INPUT_Y, 0xFF9EA8FF);
        slotWell(graphics, OUTPUT_X, OUTPUT_Y, 0xFF9FE7DF);
        drawLine(graphics, recipe);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void drawLine(GuiGraphics graphics, FiberDrawingRecipe recipe) {
        int centerY = DRAW_Y + DRAW_HEIGHT / 2;
        int color = recipe.singleMode() ? 0xFF9EA8FF : 0xFF9FE7DF;
        int leftStart = DRAW_X + 3;
        int rightEnd = DRAW_X + DRAW_WIDTH - 3;
        int dieCenter = DRAW_X + DRAW_WIDTH / 2;
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
                ThermalSmelterUiSkin.withAlpha(0xFFFFB35C, 162),
                ThermalSmelterUiSkin.withAlpha(color, 188),
                rightEnd + 1
        );
        drawFiberPress(graphics, taperEnd, dieEnd, centerY, color);
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
            int sliceColor = t < 0.55D ? leftColor : rightColor;
            fillClipped(graphics, leftEnd + offset, centerY - half, leftEnd + offset + 1, centerY + half + 1,
                    clipRight, sliceColor);
        }

        fillClipped(graphics, taperEnd, centerY - narrowHalf, dieEnd, centerY + narrowHalf + 1, clipRight, rightColor);
        fillClipped(graphics, dieEnd, centerY - narrowHalf, rightEnd, centerY + narrowHalf + 1, clipRight, rightColor);
        fillClipped(graphics, leftStart + 1, centerY - wideHalf + 1, leftEnd - 1, centerY - wideHalf + 2,
                clipRight, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 38));
        fillClipped(graphics, dieEnd, centerY - narrowHalf + 1, rightEnd - 1, centerY - narrowHalf + 2,
                clipRight, ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 50));
    }

    private static void drawFiberPress(GuiGraphics graphics, int x, int right, int centerY, int accent) {
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

        graphics.fill(x + 2, centerY - 1, right - 2, centerY + 2, ThermalSmelterUiSkin.withAlpha(accent, 152));
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
