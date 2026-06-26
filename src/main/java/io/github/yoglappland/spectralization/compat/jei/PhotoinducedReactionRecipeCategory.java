package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.storage.PhotoinducedReactionRecipe;
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

public final class PhotoinducedReactionRecipeCategory implements IRecipeCategory<PhotoinducedReactionRecipe> {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 58;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int INPUT_X = 24;
    private static final int INPUT_Y = 20;
    private static final int SHELL_X = 66;
    private static final int SHELL_Y = 20;
    private static final int OUTPUT_X = 108;
    private static final int OUTPUT_Y = 20;
    private static final int REACTION_LINE_X = 42;
    private static final int REACTION_LINE_Y = 28;
    private static final int REACTION_LINE_WIDTH = 66;
    private static final int REACTION_LINE_HEIGHT = 3;
    private static final int REACTION_LINE_COLOR = 0xFFC8C3B7;

    private final IDrawable icon;

    public PhotoinducedReactionRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.HOLOGRAPHIC_STORAGE_SHELL_ITEM.get()));
    }

    @Override
    public RecipeType<PhotoinducedReactionRecipe> getRecipeType() {
        return SpectralJeiPlugin.PHOTOINDUCED_REACTION;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.spectralization.photoinduced_reaction");
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
    public void setRecipe(IRecipeLayoutBuilder builder, PhotoinducedReactionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + ITEM_SLOT_INSET, INPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("input")
                .addItemStacks(recipe.displayInputs());
        builder.addSlot(RecipeIngredientRole.CATALYST, SHELL_X + ITEM_SLOT_INSET, SHELL_Y + ITEM_SLOT_INSET)
                .setSlotName("shell")
                .addItemStack(new ItemStack(Spectralization.HOLOGRAPHIC_STORAGE_SHELL_ITEM.get()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + ITEM_SLOT_INSET, OUTPUT_Y + ITEM_SLOT_INSET)
                .setSlotName("output")
                .addItemStacks(recipe.displayResults());
    }

    @Override
    public void draw(
            PhotoinducedReactionRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        reactionLine(graphics);
        slotWell(graphics, INPUT_X, INPUT_Y, ThermalSmelterUiSkin.HEAT);
        slotWell(graphics, OUTPUT_X, OUTPUT_Y, ThermalSmelterUiSkin.PROGRESS);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            PhotoinducedReactionRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, REACTION_LINE_X, REACTION_LINE_Y - 5, REACTION_LINE_WIDTH, REACTION_LINE_HEIGHT + 10)
                || inside(mouseX, mouseY, SHELL_X, SHELL_Y, SLOT_SIZE, SLOT_SIZE)) {
            tooltip.add(Component.translatable(
                    "jei.spectralization.photoinduced_reaction.power",
                    trim(recipe.requiredCoherentPower())
            ));
            tooltip.add(Component.translatable(
                    "jei.spectralization.photoinduced_reaction.time",
                    secondsText(recipe.processTicks())
            ));

            if ("wool_color_shift".equals(recipe.id())) {
                tooltip.add(Component.translatable("jei.spectralization.photoinduced_reaction.random_output"));
            }
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void reactionLine(GuiGraphics graphics) {
        graphics.fill(
                REACTION_LINE_X,
                REACTION_LINE_Y,
                REACTION_LINE_X + REACTION_LINE_WIDTH,
                REACTION_LINE_Y + REACTION_LINE_HEIGHT,
                ThermalSmelterUiSkin.withAlpha(REACTION_LINE_COLOR, 230)
        );
        graphics.fill(
                REACTION_LINE_X + 1,
                REACTION_LINE_Y - 1,
                REACTION_LINE_X + REACTION_LINE_WIDTH - 1,
                REACTION_LINE_Y,
                ThermalSmelterUiSkin.withAlpha(ThermalSmelterUiSkin.PANEL_HIGHLIGHT, 76)
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return ThermalSmelterUiSkin.inside(mouseX, mouseY, x, y, width, height);
    }

    private static String secondsText(int ticks) {
        double seconds = Math.max(0, ticks) / 20.0;
        return seconds == Math.rint(seconds)
                ? String.valueOf((int) seconds)
                : String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static String trim(double value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value)
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
