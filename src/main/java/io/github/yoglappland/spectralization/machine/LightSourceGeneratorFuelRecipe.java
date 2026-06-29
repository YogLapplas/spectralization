package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.blockentity.PhotonicGradientGeneratorBlockEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record LightSourceGeneratorFuelRecipe(ItemStack source, int fePerTick, int burnTicks) {
    public static List<LightSourceGeneratorFuelRecipe> recipes() {
        return BuiltInRegistries.ITEM.stream()
                .map(LightSourceGeneratorFuelRecipe::fromItem)
                .filter(recipe -> recipe.fePerTick() > 0)
                .sorted(Comparator.comparing(recipe -> BuiltInRegistries.ITEM.getKey(recipe.source().getItem()).toString()))
                .toList();
    }

    private static LightSourceGeneratorFuelRecipe fromItem(Item item) {
        ItemStack stack = new ItemStack(item);
        return new LightSourceGeneratorFuelRecipe(
                stack,
                PhotonicGradientGeneratorBlockEntity.outputFor(stack),
                PhotonicGradientGeneratorBlockEntity.BURN_TICKS
        );
    }

    public ResourceLocation sourceId() {
        return BuiltInRegistries.ITEM.getKey(source.getItem());
    }
}
