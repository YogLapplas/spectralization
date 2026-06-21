package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.menu.ThermalSmelterLayout;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class SpectralJeiPlugin implements IModPlugin {
    public static final RecipeType<ThermalSmelterRecipe> THERMAL_SMELTER =
            RecipeType.create(Spectralization.MODID, "thermal_smelter", ThermalSmelterRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ThermalSmelterRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(THERMAL_SMELTER, ThermalSmelterRecipe.recipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Spectralization.THERMAL_SMELTER_ITEM.get()), THERMAL_SMELTER);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(
                ThermalSmelterScreen.class,
                ThermalSmelterLayout.PROCESS_X + ThermalSmelterLayout.PROCESS_RECIPE_CLICK_X,
                ThermalSmelterLayout.PROCESS_Y + ThermalSmelterLayout.PROCESS_RECIPE_CLICK_Y,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_WIDTH,
                ThermalSmelterLayout.PROCESS_RECIPE_CLICK_HEIGHT,
                THERMAL_SMELTER
        );
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                ThermalSmelterMenu.class,
                SpectralMenus.THERMAL_SMELTER.get(),
                THERMAL_SMELTER,
                0,
                2,
                ThermalSmelterBlockEntity.SLOT_COUNT,
                36
        );
    }
}
