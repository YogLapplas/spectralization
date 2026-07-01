package io.github.yoglappland.spectralization.compat.jei;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.blockentity.BasicLithographyMachineBlockEntity;
import io.github.yoglappland.spectralization.blockentity.SolarDopingChamberBlockEntity;
import io.github.yoglappland.spectralization.blockentity.ThermalSmelterBlockEntity;
import io.github.yoglappland.spectralization.client.screen.BasicLithographyMachineScreen;
import io.github.yoglappland.spectralization.client.screen.FiberDrawingMachineScreen;
import io.github.yoglappland.spectralization.client.screen.LightSourceGeneratorScreen;
import io.github.yoglappland.spectralization.client.screen.PhotothermalGeneratorScreen;
import io.github.yoglappland.spectralization.client.screen.SolarDopingChamberScreen;
import io.github.yoglappland.spectralization.client.screen.ThermalSmelterScreen;
import io.github.yoglappland.spectralization.machine.BasicLithographyRecipe;
import io.github.yoglappland.spectralization.machine.FiberDrawingRecipe;
import io.github.yoglappland.spectralization.machine.LightSourceGeneratorFuelRecipe;
import io.github.yoglappland.spectralization.machine.PhotothermalGeneratorFuelRecipe;
import io.github.yoglappland.spectralization.machine.SolarDopingRecipe;
import io.github.yoglappland.spectralization.machine.ThermalSmelterRecipe;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineLayout;
import io.github.yoglappland.spectralization.menu.FiberDrawingMachineLayout;
import io.github.yoglappland.spectralization.menu.BasicLithographyMachineMenu;
import io.github.yoglappland.spectralization.menu.LightSourceGeneratorLayout;
import io.github.yoglappland.spectralization.menu.PhotothermalGeneratorLayout;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberLayout;
import io.github.yoglappland.spectralization.menu.SolarDopingChamberMenu;
import io.github.yoglappland.spectralization.menu.ThermalSmelterLayout;
import io.github.yoglappland.spectralization.menu.ThermalSmelterMenu;
import io.github.yoglappland.spectralization.optics.lens.LensMaterial;
import io.github.yoglappland.spectralization.optics.lens.LensProfile;
import io.github.yoglappland.spectralization.registry.SpectralMenus;
import io.github.yoglappland.spectralization.storage.PhotoinducedReactionRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

@JeiPlugin
public final class SpectralJeiPlugin implements IModPlugin {
    public static final RecipeType<ThermalSmelterRecipe> THERMAL_SMELTER =
            RecipeType.create(Spectralization.MODID, "thermal_smelter", ThermalSmelterRecipe.class);
    public static final RecipeType<BasicLithographyRecipe> BASIC_LITHOGRAPHY =
            RecipeType.create(Spectralization.MODID, "basic_lithography", BasicLithographyRecipe.class);
    public static final RecipeType<SolarDopingRecipe> SOLAR_DOPING =
            RecipeType.create(Spectralization.MODID, "solar_doping", SolarDopingRecipe.class);
    public static final RecipeType<FiberDrawingRecipe> FIBER_DRAWING =
            RecipeType.create(Spectralization.MODID, "fiber_drawing", FiberDrawingRecipe.class);
    public static final RecipeType<PhotoinducedReactionRecipe> PHOTOINDUCED_REACTION =
            RecipeType.create(Spectralization.MODID, "photoinduced_reaction", PhotoinducedReactionRecipe.class);
    public static final RecipeType<LightSourceGeneratorFuelRecipe> LIGHT_SOURCE_GENERATOR =
            RecipeType.create(Spectralization.MODID, "light_source_generator", LightSourceGeneratorFuelRecipe.class);
    public static final RecipeType<PhotothermalGeneratorFuelRecipe> PHOTOTHERMAL_GENERATOR =
            RecipeType.create(Spectralization.MODID, "photothermal_generator", PhotothermalGeneratorFuelRecipe.class);
    public static final RecipeType<LaserDeviceRecipe> LASER =
            RecipeType.create(Spectralization.MODID, "laser", LaserDeviceRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new ThermalSmelterRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new BasicLithographyRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new SolarDopingRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new FiberDrawingRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new PhotoinducedReactionRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new LightSourceGeneratorFuelRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new PhotothermalGeneratorFuelRecipeCategory(registration.getJeiHelpers().getGuiHelper()),
                new LaserDeviceRecipeCategory(registration.getJeiHelpers().getGuiHelper())
        );
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(THERMAL_SMELTER, ThermalSmelterRecipe.recipes());
        registration.addRecipes(BASIC_LITHOGRAPHY, BasicLithographyRecipe.recipes());
        registration.addRecipes(SOLAR_DOPING, SolarDopingRecipe.recipes());
        registration.addRecipes(FIBER_DRAWING, FiberDrawingRecipe.recipes());
        registration.addRecipes(PHOTOINDUCED_REACTION, PhotoinducedReactionRecipe.recipes());
        registration.addRecipes(LIGHT_SOURCE_GENERATOR, LightSourceGeneratorFuelRecipe.recipes());
        registration.addRecipes(PHOTOTHERMAL_GENERATOR, PhotothermalGeneratorFuelRecipe.recipes());
        registration.addRecipes(LASER, LaserDeviceRecipe.recipes());
        registration.addRecipes(RecipeTypes.CRAFTING, List.of(basicLithographyMachineCraftingDisplay(registration)));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Spectralization.THERMAL_SMELTER_ITEM.get()), THERMAL_SMELTER);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.BASIC_LITHOGRAPHY_MACHINE_ITEM.get()), BASIC_LITHOGRAPHY);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.SOLAR_DOPING_CHAMBER_ITEM.get()), SOLAR_DOPING);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.DIMENSION_DOPING_CHAMBER_ITEM.get()), SOLAR_DOPING);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.FIBER_DRAWING_MACHINE_ITEM.get()), FIBER_DRAWING);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.HOLOGRAPHIC_STORAGE_SHELL_ITEM.get()), PHOTOINDUCED_REACTION);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.LIGHT_SOURCE_GENERATOR_ITEM.get()), LIGHT_SOURCE_GENERATOR);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.PHOTOTHERMAL_GENERATOR_ITEM.get()), PHOTOTHERMAL_GENERATOR);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.RUBY_BLOCK_ITEM.get()), LASER);
        registration.addRecipeCatalyst(new ItemStack(Spectralization.FIBER_LASER_ITEM.get()), LASER);
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
        registration.addRecipeClickArea(
                FiberDrawingMachineScreen.class,
                FiberDrawingMachineLayout.RECIPE_CLICK_X,
                FiberDrawingMachineLayout.RECIPE_CLICK_Y,
                FiberDrawingMachineLayout.RECIPE_CLICK_WIDTH,
                FiberDrawingMachineLayout.RECIPE_CLICK_HEIGHT,
                FIBER_DRAWING
        );
        registration.addRecipeClickArea(
                LightSourceGeneratorScreen.class,
                LightSourceGeneratorLayout.RECIPE_CLICK_X,
                LightSourceGeneratorLayout.RECIPE_CLICK_Y,
                LightSourceGeneratorLayout.RECIPE_CLICK_WIDTH,
                LightSourceGeneratorLayout.RECIPE_CLICK_HEIGHT,
                LIGHT_SOURCE_GENERATOR
        );
        registration.addRecipeClickArea(
                PhotothermalGeneratorScreen.class,
                PhotothermalGeneratorLayout.RECIPE_CLICK_X,
                PhotothermalGeneratorLayout.RECIPE_CLICK_Y,
                PhotothermalGeneratorLayout.RECIPE_CLICK_WIDTH,
                PhotothermalGeneratorLayout.RECIPE_CLICK_HEIGHT,
                PHOTOTHERMAL_GENERATOR
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

    private static RecipeHolder<CraftingRecipe> basicLithographyMachineCraftingDisplay(IRecipeRegistration registration) {
        CraftingRecipe recipe = registration.getVanillaRecipeFactory()
                .createShapedRecipeBuilder(
                        CraftingBookCategory.MISC,
                        List.of(new ItemStack(Spectralization.BASIC_LITHOGRAPHY_MACHINE_ITEM.get()))
                )
                .define('I', Ingredient.of(Items.IRON_INGOT))
                .define('L', Ingredient.of(shortFocalLens()))
                .define('R', Ingredient.of(Items.REDSTONE))
                .define('B', Ingredient.of(Items.BLAST_FURNACE))
                .define('S', Ingredient.of(Items.SMOOTH_STONE))
                .pattern("ILI")
                .pattern("RBR")
                .pattern("ISI")
                .group(Spectralization.MODID)
                .build();

        return new RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(Spectralization.MODID, "basic_lithography_machine_crafting_display"),
                recipe
        );
    }

    private static ItemStack shortFocalLens() {
        return LensProfile.withUnits(
                "short",
                LensProfile.MIN_FOCAL_LENGTH_UNITS,
                100,
                2,
                LensMaterial.ORDINARY.id(),
                LensProfile.MAX_FINISH_PERMILLE
        ).createStack();
    }
}
