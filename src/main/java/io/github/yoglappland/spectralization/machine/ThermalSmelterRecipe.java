package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.Spectralization;
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
import net.minecraft.world.level.ItemLike;

public record ThermalSmelterRecipe(
        Predicate<ItemStack> ingredient,
        Predicate<ItemStack> additive,
        Supplier<ItemStack> result,
        int additiveCost,
        int minimumTemperature,
        double heatCost,
        int processTicks
) {
    private static final List<ThermalSmelterRecipe> RECIPES = List.of(
            tagged(c("raw_materials/silver"), Spectralization.SILVER_INGOT, 1235, 220.0, 150),
            item(Items.SAND, Items.GLASS, 1600, 180.0, 120),
            item(Items.RED_SAND, Items.GLASS, 1600, 180.0, 120),
            itemWithAdditive(
                    Items.GLASS,
                    stack -> stack.is(c("ingots/silver")) || stack.is(Spectralization.SILVER_INGOT.get()),
                    Spectralization.SILVER_GLASS_ITEM,
                    1,
                    1350,
                    240.0,
                    160
            ),
            tagged(c("gems/rutile"), Spectralization.TITANIUM_DIOXIDE_DUST, 2110, 360.0, 190),
            tagged(c("gems/corundum"), Spectralization.ALUMINA_DUST, 2320, 450.0, 220),
            tagged(c("gems/ruby"), Spectralization.ALUMINA_DUST, 2320, 520.0, 260),
            taggedWithAdditive(
                    c("dusts/yttrium_oxide"),
                    stack -> stack.is(Spectralization.ALUMINA_DUST.get()) || stack.is(c("dusts/alumina")),
                    Spectralization.YAG_CRYSTAL,
                    1,
                    2240,
                    470.0,
                    240
            )
    );

    public static Optional<ThermalSmelterRecipe> find(ItemStack input, ItemStack additive) {
        if (input.isEmpty()) {
            return Optional.empty();
        }

        return RECIPES.stream().filter(recipe -> recipe.matches(input, additive)).findFirst();
    }

    public static boolean isProcessable(ItemStack stack) {
        return !stack.isEmpty() && RECIPES.stream().anyMatch(recipe -> recipe.matchesIngredient(stack));
    }

    public static boolean isPotentialAdditive(ItemStack stack) {
        return !stack.isEmpty() && RECIPES.stream().anyMatch(recipe -> recipe.additiveCost > 0 && recipe.additive.test(stack));
    }

    public static boolean isMeltable(ItemStack stack) {
        return isProcessable(stack);
    }

    public boolean matches(ItemStack input, ItemStack additiveStack) {
        return matchesIngredient(input) && hasAdditive(additiveStack);
    }

    public boolean matchesIngredient(ItemStack stack) {
        return ingredient.test(stack);
    }

    public ItemStack resultStack() {
        return result.get().copy();
    }

    public boolean consumesAdditive() {
        return additiveCost > 0;
    }

    private boolean hasAdditive(ItemStack stack) {
        return additiveCost <= 0 || (!stack.isEmpty() && stack.getCount() >= additiveCost && additive.test(stack));
    }

    private static ThermalSmelterRecipe item(
            Item item,
            ItemLike result,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(
                stack -> stack.is(item),
                ItemStack::isEmpty,
                () -> new ItemStack(result),
                0,
                minimumTemperature,
                heatCost,
                processTicks
        );
    }

    private static ThermalSmelterRecipe tagged(
            TagKey<Item> tag,
            Supplier<? extends ItemLike> result,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(
                stack -> stack.is(tag),
                ItemStack::isEmpty,
                () -> new ItemStack(result.get()),
                0,
                minimumTemperature,
                heatCost,
                processTicks
        );
    }

    private static ThermalSmelterRecipe itemWithAdditive(
            Item item,
            Predicate<ItemStack> additive,
            Supplier<? extends ItemLike> result,
            int additiveCost,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(
                stack -> stack.is(item),
                additive,
                () -> new ItemStack(result.get()),
                additiveCost,
                minimumTemperature,
                heatCost,
                processTicks
        );
    }

    private static ThermalSmelterRecipe taggedWithAdditive(
            TagKey<Item> tag,
            Predicate<ItemStack> additive,
            Supplier<? extends ItemLike> result,
            int additiveCost,
            int minimumTemperature,
            double heatCost,
            int processTicks
    ) {
        return new ThermalSmelterRecipe(
                stack -> stack.is(tag),
                additive,
                () -> new ItemStack(result.get()),
                additiveCost,
                minimumTemperature,
                heatCost,
                processTicks
        );
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    public ThermalSmelterRecipe {
        if (additiveCost < 0 || processTicks <= 0) {
            throw new IllegalArgumentException("Thermal smelter recipes require positive output and time");
        }
    }
}
