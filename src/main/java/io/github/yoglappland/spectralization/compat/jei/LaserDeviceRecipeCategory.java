package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.client.gui.ThermalSmelterUiSkin;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
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
import net.minecraft.world.item.ItemStack;

public final class LaserDeviceRecipeCategory implements IRecipeCategory<LaserDeviceRecipe> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 70;
    private static final int SLOT_SIZE = 18;
    private static final int ITEM_SLOT_INSET = 1;
    private static final int DEVICE_X = 14;
    private static final int DEVICE_Y = 26;
    private static final int TEXT_X = 42;
    private static final int TEXT_Y = 8;
    private static final int LINE_HEIGHT = 13;

    private final IDrawable icon;

    public LaserDeviceRecipeCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(Spectralization.BASIC_DIODE_LASER_ITEM.get()));
    }

    @Override
    public RecipeType<LaserDeviceRecipe> getRecipeType() {
        return SpectralJeiPlugin.LASER;
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
                .setSlotName("device")
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
        drawStats(graphics, recipe);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }

    private static void drawStats(GuiGraphics graphics, LaserDeviceRecipe recipe) {
        drawLine(
                graphics,
                0,
                Component.translatable(
                        "jei.spectralization.optical_device.gain_per_pu",
                        gainSlopeText(recipe.gainPerPumpUnit())
                )
        );
        drawLine(
                graphics,
                1,
                Component.translatable(
                        "jei.spectralization.optical_device.reference_gain",
                        referenceGainText(recipe.nominalSinglePassGain(), recipe.referencePump())
                )
        );
        drawLine(
                graphics,
                2,
                Component.translatable(
                        "jei.spectralization.optical_device.saturation_power",
                        powerText(recipe.saturationPower())
                )
        );
        drawLine(
                graphics,
                3,
                Component.translatable(
                        "jei.spectralization.optical_device.handling_limit",
                        powerText(recipe.handlingLimit())
                )
        );
    }

    private static void drawLine(GuiGraphics graphics, int line, Component text) {
        graphics.drawString(
                Minecraft.getInstance().font,
                text,
                TEXT_X,
                TEXT_Y + line * LINE_HEIGHT,
                SpectralJeiUi.TEXT,
                false
        );
    }

    private static Component gainSlopeText(double gainPerPumpUnit) {
        if (!Double.isFinite(gainPerPumpUnit) || gainPerPumpUnit <= 0.0D) {
            return Component.translatable("jei.spectralization.optical_device.not_configured");
        }

        return Component.literal(String.format(Locale.ROOT, "+%.4fx/PU", gainPerPumpUnit));
    }

    private static Component referenceGainText(double gain, double referencePump) {
        if (!Double.isFinite(gain) || gain <= 0.0D) {
            return Component.translatable("jei.spectralization.optical_device.not_configured");
        }

        if (!Double.isFinite(referencePump) || referencePump <= 0.0D) {
            return gainText(gain);
        }

        return Component.translatable(
                "jei.spectralization.optical_device.reference_gain_at_pump",
                gainText(gain),
                String.format(Locale.ROOT, "%.1f", referencePump)
        );
    }

    private static Component gainText(double gain) {
        if (!Double.isFinite(gain) || gain <= 0.0D) {
            return Component.translatable("jei.spectralization.optical_device.not_configured");
        }

        return Component.literal(String.format(Locale.ROOT, "%.2fx", gain));
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
