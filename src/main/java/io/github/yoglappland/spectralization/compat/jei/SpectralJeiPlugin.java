package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.screen.BasicLithographyMachineScreen;
import io.github.yoglappland.spectralization.client.screen.SolarDopingChamberScreen;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.machine.BasicLithographyRecipe;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineLayout;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineMenu;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberLayout;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberMenu;
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
    public static final RecipeType<BasicLithographyRecipe> BASIC_LITHOGRAPHY =
            RecipeType.create(Spectralization.MODID, "basic_lithography", BasicLithographyRecipe.class);
    public static final RecipeType<SolarDopingRecipe> SOLAR_DOPING =
            RecipeType.create(Spectralization.MODID, "solar_doping", SolarDopingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ThermalSmelterRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BasicLithographyRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new SolarDopingRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(THERMAL_SMELTER, ThermalSmelterRecipe.recipes());
        registration.addRecipes(BASIC_LITHOGRAPHY, BasicLithographyRecipe.recipes());
        registration.addRecipes(SOLAR_DOPING, SolarDopingRecipe.recipes());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Spectralization.THERMAL_SMELTER_ITEM.get()), THERMAL_SMELTER);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.BASIC_LITHOGRAPHY_MACHINE_ITEM.get()), BASIC_LITHOGRAPHY);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.SOLAR_DOPING_CHAMBER_ITEM.get()), SOLAR_DOPING);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.DIMENSION_DOPING_CHAMBER_ITEM.get()), SOLAR_DOPING);
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
        registration.addRecipeClickArea(
                BasicLithographyMachineScreen.class,
                BasicLithographyMachineLayout.RECIPE_ARROW_X,
                BasicLithographyMachineLayout.RECIPE_ARROW_Y,
                BasicLithographyMachineLayout.RECIPE_ARROW_WIDTH,
                BasicLithographyMachineLayout.RECIPE_ARROW_HEIGHT,
                BASIC_LITHOGRAPHY
        );
        registration.addRecipeClickArea(
                SolarDopingChamberScreen.class,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_X,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_TOP_HEIGHT,
                SOLAR_DOPING
        );
        registration.addRecipeClickArea(
                SolarDopingChamberScreen.class,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_X,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_LEFT_HEIGHT,
                SOLAR_DOPING
        );
        registration.addRecipeClickArea(
                SolarDopingChamberScreen.class,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_X,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_RIGHT_HEIGHT,
                SOLAR_DOPING
        );
        registration.addRecipeClickArea(
                SolarDopingChamberScreen.class,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_X,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_Y,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_WIDTH,
                SolarDopingChamberLayout.RECIPE_CLICK_BOTTOM_HEIGHT,
                SOLAR_DOPING
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
        registration.addRecipeTransferHandler(
                BasicLithographyMachineMenu.class,
                SpectralMenus.BASIC_LITHOGRAPHY_MACHINE.get(),
                BASIC_LITHOGRAPHY,
                0,
                6,
                BasicLithographyMachineBlockEntity.SLOT_COUNT,
                36
        );
        registration.addRecipeTransferHandler(
                SolarDopingChamberMenu.class,
                SpectralMenus.SOLAR_DOPING_CHAMBER.get(),
                SOLAR_DOPING,
                SolarDopingChamberBlockEntity.SLOT_PROCESS,
                1,
                SolarDopingChamberBlockEntity.SLOT_COUNT,
                36
        );
    }
}
