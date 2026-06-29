package io.github.yoglappland.spectralization.machine;

import io.github.yoglappland.spectralization.blockentity.PhotothermalGeneratorBlockEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record PhotothermalGeneratorFuelRecipe(ItemStack fuel, int burnTicks, int fePerTick) {
    public static List<PhotothermalGeneratorFuelRecipe> recipes() {
        return BuiltInRegistries.ITEM.stream()
                .map(PhotothermalGeneratorFuelRecipe::fromItem)
                .filter(recipe -> recipe.burnTicks() > 0)
                .sorted(Comparator.comparing(recipe -> BuiltInRegistries.ITEM.getKey(recipe.fuel().getItem()).toString()))
                .toList();
    }

    private static PhotothermalGeneratorFuelRecipe fromItem(Item item) {
        ItemStack stack = new ItemStack(item);
        return new PhotothermalGeneratorFuelRecipe(
                stack,
                stack.getBurnTime(null),
                PhotothermalGeneratorBlockEntity.BASE_FE_PER_TICK
        );
    }

    public ResourceLocation fuelId() {
        return BuiltInRegistries.ITEM.getKey(fuel.getItem());
    }
}
