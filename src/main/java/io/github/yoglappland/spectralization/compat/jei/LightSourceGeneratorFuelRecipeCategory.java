package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import io.github.yoglappland.spectralization.machine.LightSourceGeneratorFuelRecipe;
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

public final class LightSourceGeneratorFuelRecipeCategory implements IRecipeCategory<LightSourceGeneratorFuelRecipe> {
    private static final int WIDTH = 136;
    private static final int HEIGHT = 48;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int SOURCE_X = 14;
    private static final int SOURCE_Y = 15;
    private static final int BOLT_X = 54;
    private static final int BOLT_Y = 12;
    private static final int BOLT_SIZE = 24;
    private static final int OUTPUT_X = 88;
    private static final int OUTPUT_Y = 18;
    private static final ResourceLocation LIGHTNING_ICON =
            ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "textures/gui/lightening.png");

    private final IDrawable icon;

    public LightSourceGeneratorFuelRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.LIGHT_SOURCE_GENERATOR_ITEM.get()));
    }

    @Override
    public RecipeType<LightSourceGeneratorFuelRecipe> getRecipeType() {
        return SpectralJeiPlugin.LIGHT_SOURCE_GENERATOR;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.spectralization.light_source_generator");
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
    public void setRecipe(IRecipeLayoutBuilder builder, LightSourceGeneratorFuelRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, SOURCE_X + ITEM_SLOT_INSET, SOURCE_Y + ITEM_SLOT_INSET)
                .setSlotName("source")
                .addItemStack(recipe.source());
    }

    @Override
    public void draw(
            LightSourceGeneratorFuelRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        fillPanel(graphics, 0, 0, WIDTH, HEIGHT);
        slotWell(graphics, SOURCE_X, SOURCE_Y, 0xFFF1B94A);
        drawLightning(graphics);
        drawOutput(graphics, recipe);
    }

    @Override
    public void getTooltip(
            ITooltipBuilder tooltip,
            LightSourceGeneratorFuelRecipe recipe,
            IRecipeSlotsView recipeSlotsView,
            double mouseX,
            double mouseY
    ) {
        if (inside(mouseX, mouseY, BOLT_X - 3, BOLT_Y - 3, BOLT_SIZE + 6, BOLT_SIZE + 6)
                || inside(mouseX, mouseY, OUTPUT_X - 2, OUTPUT_Y - 2, 46, 14)) {
            tooltip.add(Component.translatable("jei.spectralization.light_source_generator.output", recipe.fePerTick()));
            tooltip.add(Component.translatable(
                    "jei.spectralization.light_source_generator.duration",
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

    private static void drawOutput(GuiGraphics graphics, LightSourceGeneratorFuelRecipe recipe) {
        Component text = Component.literal(recipe.fePerTick() + " FE/t");
        graphics.drawString(
                Minecraft.getInstance().font,
                text,
                OUTPUT_X,
                OUTPUT_Y,
                SpectralJeiUi.TEXT,
                false
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
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }
}
