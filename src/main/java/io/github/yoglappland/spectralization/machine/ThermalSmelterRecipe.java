package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
import io.github.yoglappland.spectralization.registry.SpectralFluids;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public record ThermalSmelterRecipe(
        Predicate<ItemStack> ingredient,
        Supplier<? extends Fluid> fluid,
        int amount,
        int minimumTemperature,
        double heatCost,
        int processTicks
) {
    private static final List<ThermalSmelterRecipe> RECIPES = List.of(
            tagged(c("ingots/silver"), SpectralFluids.MOLTEN_SILVER.still()::get, 250, 1235, 180.0, 120),
            tagged(c("raw_materials/silver"), SpectralFluids.MOLTEN_SILVER.still()::get, 250, 1235, 220.0, 150),
            tagged(c("ingots/gold"), SpectralFluids.MOLTEN_GOLD.still()::get, 250, 1337, 190.0, 130),
            tagged(c("ingots/copper"), SpectralFluids.MOLTEN_COPPER.still()::get, 250, 1358, 170.0, 120),
            item(Items.GLASS, SpectralFluids.MOLTEN_SILICA.still()::get, 250, 1980, 310.0, 180),
            item(Items.SAND, SpectralFluids.MOLTEN_SILICA.still()::get, 125, 1980, 210.0, 160),
            item(Items.RED_SAND, SpectralFluids.MOLTEN_SILICA.still()::get, 125, 1980, 210.0, 160),
            tagged(c("dusts/titanium_dioxide"), SpectralFluids.MOLTEN_TITANIUM_DIOXIDE.still()::get, 250, 2110, 360.0, 190),
            tagged(c("gems/rutile"), SpectralFluids.MOLTEN_TITANIUM_DIOXIDE.still()::get, 250, 2110, 400.0, 220),
            tagged(c("dusts/alumina"), SpectralFluids.MOLTEN_ALUMINA.still()::get, 250, 2320, 450.0, 220),
            tagged(c("gems/corundum"), SpectralFluids.MOLTEN_ALUMINA.still()::get, 250, 2320, 520.0, 260),
            tagged(c("gems/ruby"), SpectralFluids.MOLTEN_ALUMINA.still()::get, 250, 2320, 520.0, 260),
            tagged(c("gems/fluorite"), SpectralFluids.MOLTEN_FLUORITE.still()::get, 250, 1680, 260.0, 150),
            tagged(c("dusts/yttrium_oxide"), SpectralFluids.MOLTEN_YTTRIUM_OXIDE.still()::get, 250, 2420, 520.0, 260),
            tagged(c("gems/yag"), SpectralFluids.MOLTEN_YAG.still()::get, 250, 2240, 470.0, 240)
    );

    public static Optional<ThermalSmelterRecipe> find(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        return RECIPES.stream().filter(recipe -> recipe.matches(stack)).findFirst();
    }

    public static boolean isMeltable(ItemStack stack) {
        return find(stack).isPresent();
    }

    public boolean matches(ItemStack stack) {
        return ingredient.test(stack);
    }

    public FluidStack resultStack() {
        return new FluidStack(fluid.get(), amount);
    }

    private static ThermalSmelterRecipe item(
            Item item,
            Supplier<? extends Fluid> fluid,
            int amount,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(stack -> stack.is(item), fluid, amount, minimumTemperature, heatCost, processTicks);
    }

    private static ThermalSmelterRecipe tagged(
            TagKey<Item> tag,
            Supplier<? extends Fluid> fluid,
            int amount,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(stack -> stack.is(tag), fluid, amount, minimumTemperature, heatCost, processTicks);
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    public ThermalSmelterRecipe {
        if (amount <= 0 || processTicks <= 0) {
            throw new IllegalArgumentException("Thermal smelter recipes require positive output and time");
        }
    }
}
