package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.PhotothermalGeneratorFuelRecipe;
import java.util.Locale;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PhotothermalGeneratorFuelRecipeCategory implements IRecipeCategory<PhotothermalGeneratorFuelRecipe> {
    private static final int WIDTH = 126;
    private static final int HEIGHT = 50;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int FUEL_X = 14;
    private static final int FUEL_Y = 16;
    private static final int BOLT_X = 48;
    private static final int BOLT_Y = 13;
    private static final int BOLT_SIZE = 24;
    private static final int TEXT_X = 78;
    private static final int OUTPUT_Y = 13;
    private static final int TIME_Y = 25;
    private static final ResourceLocation LIGHTNING_ICON =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/gui/lightening.png");

    private final IDrawable icon;

    public PhotothermalGeneratorFuelRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.PHOTOTHERMAL_GENERATOR_ITEM.get()));
    }

    @Override
    public RecipeType<PhotothermalGeneratorFuelRecipe> getRecipeType() {
        return SpectralJeiPlugin.PHOTOTHERMAL_GENERATOR;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.spectralization.photothermal_generator");
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
    public void setRecipe(IRecipeLayoutBuilder builder, PhotothermalGeneratorFuelRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, FUEL_X + ITEM_SLOT_INSET, FUEL_Y + ITEM_SLOT_INSET)
                .setSlotName("fuel")
                .addItemStack(recipe.fuel());
    }

    @Override
    public void draw(
            PhotothermalGeneratorFuelRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, FUEL_X, FUEL_Y, 0xFFF1B94A);
        drawLightning(graphics);
        drawStats(graphics, recipe);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            PhotothermalGeneratorFuelRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, BOLT_X - 3, BOLT_Y - 3, BOLT_SIZE + 6, BOLT_SIZE + 6)
                || inside(mouseX, mouseY, TEXT_X - 2, OUTPUT_Y - 2, 44, 24)) {
            tooltip.add(Component.translatable("jei.spectralization.photothermal_generator.output", recipe.fePerTick()));
            tooltip.add(Component.translatable(
                    "jei.spectralization.photothermal_generator.duration",
                    secondsText(recipe.burnTicks())
            ));
        }
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void drawLightning(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(BOLT_X, BOLT_Y, 0.0F);
        graphics.pose().scale(BOLT_SIZE / 16.0F, BOLT_SIZE / 16.0F, 1.0F);
        graphics.blit(LIGHTNING_ICON, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private static void drawStats(GuiGraphics graphics, PhotothermalGeneratorFuelRecipe recipe) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal(recipe.fePerTick() + " FE/t"), TEXT_X, OUTPUT_Y,
                SpectralJeiUi.TEXT, false);
        graphics.drawString(font, Component.literal(secondsText(recipe.burnTicks()) + " s"), TEXT_X, TIME_Y,
                SpectralJeiUi.MUTED_TEXT, false);
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
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
